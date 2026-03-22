package com.bgsoftware.superiorskyblock.listener;

import com.bgsoftware.superiorskyblock.SuperiorSkyblockPlugin;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.player.PlayerStatus;
import com.bgsoftware.superiorskyblock.api.service.portals.EntityPortalResult;
import com.bgsoftware.superiorskyblock.api.service.portals.PortalsManagerService;
import com.bgsoftware.superiorskyblock.api.world.Dimension;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import com.bgsoftware.superiorskyblock.core.IslandWorlds;
import com.bgsoftware.superiorskyblock.core.LazyReference;
import com.bgsoftware.superiorskyblock.core.Materials;
import com.bgsoftware.superiorskyblock.core.ObjectsPools;
import com.bgsoftware.superiorskyblock.core.ServerVersion;
import com.bgsoftware.superiorskyblock.core.threads.BukkitExecutor;
import com.bgsoftware.superiorskyblock.platform.event.GameEvent;
import com.bgsoftware.superiorskyblock.platform.event.GameEventPriority;
import com.bgsoftware.superiorskyblock.platform.event.GameEventType;
import com.bgsoftware.superiorskyblock.platform.event.args.GameEventArgs;
import com.bgsoftware.superiorskyblock.player.SuperiorNPCPlayer;
import com.bgsoftware.superiorskyblock.world.EntityTeleports;
import com.bgsoftware.superiorskyblock.api.world.Dimension;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.PortalType;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class PortalsListener extends AbstractGameEventListener {

    /**
     * Players added here are immune to lobby portal teleportation for 5 ticks.
     * Used to prevent /is go (and other plugin teleports) from triggering the portal
     * when the destination happens to be inside or adjacent to a nether portal block.
     */
    private final Set<UUID> recentlyTeleportedPlayers = new HashSet<>();

    private final LazyReference<PortalsManagerService> portalsManager = new LazyReference<PortalsManagerService>() {
        @Override
        protected PortalsManagerService create() {
            return plugin.getServices().getService(PortalsManagerService.class);
        }
    };

    private final LazyReference<com.bgsoftware.superiorskyblock.api.service.portals.IslandLobbyPortalService> lobbyPortalService = new LazyReference<com.bgsoftware.superiorskyblock.api.service.portals.IslandLobbyPortalService>() {
        @Override
        protected com.bgsoftware.superiorskyblock.api.service.portals.IslandLobbyPortalService create() {
            return plugin.getServices().getService(com.bgsoftware.superiorskyblock.api.service.portals.IslandLobbyPortalService.class);
        }
    };

    public PortalsListener(SuperiorSkyblockPlugin plugin) {
        super(plugin);
        registerListeners();
    }

    private void registerListeners() {
        registerCallback(GameEventType.ENTITY_PORTAL_EVENT, GameEventPriority.MONITOR, this::onEntityPortal);
        registerCallback(GameEventType.ENTITY_ENTER_PORTAL_EVENT, GameEventPriority.HIGHEST, this::onEntityEnterPortal);
        registerCallback(GameEventType.ENTITY_TELEPORT_EVENT, GameEventPriority.MONITOR, this::onEntityTeleport);
        registerCallback(GameEventType.BLOCK_IGNITE_EVENT, GameEventPriority.NORMAL, this::onBlockIgnite);
        registerCallback(GameEventType.BLOCK_FROM_TO_EVENT, GameEventPriority.NORMAL, this::onNetherPortalSpread);
        registerCallback(GameEventType.BLOCK_BREAK_EVENT, GameEventPriority.NORMAL, this::onPortalBlockBreak);
        registerCallback(GameEventType.BLOCK_PLACE_EVENT, GameEventPriority.NORMAL, this::onPortalBlockPlace);
    }

    private void onEntityPortal(GameEvent<GameEventArgs.EntityPortalEvent> e) {
        if (e.getArgs().entity instanceof Player)
            handlePlayerPortal(e);
        else
            handleEntityPortalResult(e);
    }

    private void onEntityEnterPortal(GameEvent<GameEventArgs.EntityEnterPortalEvent> e) {
        // Simulate portals in the following cases:
        //  - Using an end portal in the end
        //  - The target world is disabled

        Entity entity = e.getArgs().entity;

        Island island;
        try (ObjectsPools.Wrapper<Location> wrapper = ObjectsPools.LOCATION.obtain()) {
            island = plugin.getGrid().getIslandAt(entity.getLocation(wrapper.getHandle()));
        }

        if (island == null)
            return;

        Location portalLocation = e.getArgs().portalLocation;

        World world = portalLocation.getWorld();

        // Simulate end portal
        if (world.getEnvironment() == World.Environment.THE_END) {
            /* We teleport the player to his island instead of cancelling the event.
            Therefore, we must prevent the player from acting like he entered another island or left his island.*/

            SuperiorPlayer teleportedPlayer = entity instanceof Player ?
                    plugin.getPlayers().getSuperiorPlayer((Player) entity) : null;

            if (teleportedPlayer != null)
                teleportedPlayer.setPlayerStatus(PlayerStatus.LEAVING_ISLAND);

            BukkitExecutor.sync(() -> {
                Dimension dimension = plugin.getSettings().getWorlds().getDefaultWorldDimension();
                IslandWorlds.accessIslandWorldAsync(island, dimension, true, islandWorldResult -> {
                    islandWorldResult.ifRight(error -> {
                        if (teleportedPlayer != null)
                            teleportedPlayer.removePlayerStatus(PlayerStatus.LEAVING_ISLAND);
                    }).ifLeft(unused -> {
                        EntityTeleports.teleportUntilSuccess(entity,
                                island.getIslandHome(dimension), 5, () -> {
                                    if (teleportedPlayer != null)
                                        teleportedPlayer.removePlayerStatus(PlayerStatus.LEAVING_ISLAND);
                                });
                    });
                });

            }, 5L);
        }

        if (ServerVersion.isLessThan(ServerVersion.v1_16))
            return;

        boolean isPlayer = entity instanceof Player;

        Material originalMaterial = portalLocation.getBlock().getType();

        PortalType portalType = originalMaterial == Materials.NETHER_PORTAL.toBukkitType() ? PortalType.NETHER : PortalType.ENDER;

        // Instant lobby portal: intercept on the very first portal tick (tick 0), before any nether checks.
        // This gives Hypixel-style instant teleport to the hub when entering a nether portal on an island.
        // recentlyTeleportedPlayers guards against /is go (or any plugin teleport) landing the player
        // inside a portal block and falsely triggering the send.
        if (isPlayer && portalType == PortalType.NETHER && lobbyPortalService.get().isEnabled() && !island.isSpawn()) {
            UUID entityId = entity.getUniqueId();
            if (plugin.getNMSEntities().getPortalTicks(entity) == 0
                    && !recentlyTeleportedPlayers.contains(entityId)) {
                // Add immediately so that other portal blocks touching the player on the same tick
                // don't fire duplicate sends (EntityEnterPortalEvent fires once per portal block).
                recentlyTeleportedPlayers.add(entityId);
                BukkitExecutor.sync(() -> recentlyTeleportedPlayers.remove(entityId), 20L);
                SuperiorPlayer superiorPlayer = plugin.getPlayers().getSuperiorPlayer(entity);
                lobbyPortalService.get().sendPlayerToLobby(superiorPlayer);
            }
            return;
        }

        if (isPlayer && (portalType == PortalType.NETHER ? Bukkit.getAllowNether() : Bukkit.getAllowEnd()))
            return;

        if (portalType == PortalType.NETHER) {
            int ticksDelay = !isPlayer ? 0 : ((Player) entity).getGameMode() == GameMode.CREATIVE ? 1 : 80;
            int portalTicks = plugin.getNMSEntities().getPortalTicks(entity);
            if (portalTicks != ticksDelay)
                return;
        }

        if (isPlayer) {
            SuperiorPlayer superiorPlayer = plugin.getPlayers().getSuperiorPlayer(entity);
            this.portalsManager.get().handlePlayerPortalFromIsland(superiorPlayer, island, portalLocation, portalType, true);
        } else {
            this.portalsManager.get().handleEntityPortalFromIsland(entity, island, portalLocation, portalType);
        }
    }

    // Track any teleport so the player gets a 5-tick immunity from the lobby portal trigger.
    // Prevents /is go (and similar teleports) from firing the send when the landing spot is
    // inside or adjacent to the nether portal block in the island schematic.
    private void onEntityTeleport(GameEvent<GameEventArgs.EntityTeleportEvent> e) {
        if (!(e.getArgs().entity instanceof Player))
            return;
        if (!lobbyPortalService.get().isEnabled())
            return;
        UUID id = e.getArgs().entity.getUniqueId();
        recentlyTeleportedPlayers.add(id);
        BukkitExecutor.sync(() -> recentlyTeleportedPlayers.remove(id), 5L);
    }

    // Prevent players from breaking any block inside the admin-defined protected portal region.
    // The region covers the full portal frame + portal blocks, protecting all block types within it.
    // Players with superior.island.portal.bypass can break blocks in the region (e.g. admins via LuckPerms).
    private void onPortalBlockBreak(GameEvent<GameEventArgs.BlockBreakEvent> e) {
        if (!lobbyPortalService.get().isEnabled() || !lobbyPortalService.get().hasProtectedRegion())
            return;

        Player player = e.getArgs().player;
        if (player != null && player.hasPermission("superior.island.portal.bypass"))
            return;

        Block block = e.getArgs().block;
        Island island;
        try (ObjectsPools.Wrapper<Location> wrapper = ObjectsPools.LOCATION.obtain()) {
            island = plugin.getGrid().getIslandAt(block.getLocation(wrapper.getHandle()));
        }
        if (island == null || island.isSpawn())
            return;

        Dimension dimension = plugin.getSettings().getWorlds().getDefaultWorldDimension();
        Location center = island.getCenter(dimension);
        if (lobbyPortalService.get().isInProtectedRegion(island.getSchematicName(), center, block.getLocation()))
            e.setCancelled();
    }

    // Prevent players from placing blocks inside the admin-defined protected portal region.
    // Same bypass permission applies: superior.island.portal.bypass
    private void onPortalBlockPlace(GameEvent<GameEventArgs.BlockPlaceEvent> e) {
        if (!lobbyPortalService.get().isEnabled() || !lobbyPortalService.get().hasProtectedRegion())
            return;

        Player player = e.getArgs().player;
        if (player != null && player.hasPermission("superior.island.portal.bypass"))
            return;

        Block block = e.getArgs().block;
        Island island;
        try (ObjectsPools.Wrapper<Location> wrapper = ObjectsPools.LOCATION.obtain()) {
            island = plugin.getGrid().getIslandAt(block.getLocation(wrapper.getHandle()));
        }
        if (island == null || island.isSpawn())
            return;

        Dimension dimension = plugin.getSettings().getWorlds().getDefaultWorldDimension();
        Location center = island.getCenter(dimension);
        if (lobbyPortalService.get().isInProtectedRegion(island.getSchematicName(), center, block.getLocation()))
            e.setCancelled();
    }

    // Prevent players from lighting new nether portals on their islands while lobby portals
    // are enabled. Only the pre-built schematic portal should ever exist.
    private void onBlockIgnite(GameEvent<GameEventArgs.BlockIgniteEvent> e) {
        if (!lobbyPortalService.get().isEnabled())
            return;
        if (e.getArgs().igniteCause != BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL
                && e.getArgs().igniteCause != BlockIgniteEvent.IgniteCause.SPREAD
                && e.getArgs().igniteCause != BlockIgniteEvent.IgniteCause.FIREBALL)
            return;
        try (ObjectsPools.Wrapper<Location> wrapper = ObjectsPools.LOCATION.obtain()) {
            Island island = plugin.getGrid().getIslandAt(e.getArgs().block.getLocation(wrapper.getHandle()));
            if (island != null && !island.isSpawn())
                e.setCancelled();
        }
    }

    // Prevent nether portal blocks from spreading (forming new columns of portal blocks)
    // on islands while lobby portals are enabled.
    private void onNetherPortalSpread(GameEvent<GameEventArgs.BlockFromToEvent> e) {
        if (!lobbyPortalService.get().isEnabled())
            return;
        if (e.getArgs().toBlock.getType() != Materials.NETHER_PORTAL.toBukkitType()
                && e.getArgs().block.getType() != Materials.NETHER_PORTAL.toBukkitType())
            return;
        // Only block new portal block creation, not existing portal blocks flowing (water/lava)
        if (e.getArgs().block.getType() != Materials.NETHER_PORTAL.toBukkitType())
            return;
        try (ObjectsPools.Wrapper<Location> wrapper = ObjectsPools.LOCATION.obtain()) {
            Island island = plugin.getGrid().getIslandAt(e.getArgs().block.getLocation(wrapper.getHandle()));
            if (island != null && !island.isSpawn())
                e.setCancelled();
        }
    }

    private void handlePlayerPortal(GameEvent<GameEventArgs.EntityPortalEvent> e) {
        SuperiorPlayer superiorPlayer = plugin.getPlayers().getSuperiorPlayer((Player) e.getArgs().entity);

        if (superiorPlayer instanceof SuperiorNPCPlayer) {
            ((SuperiorNPCPlayer) superiorPlayer).release();
            return;
        }

        PortalType portalType = (e.getArgs().cause == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) ?
                PortalType.NETHER : PortalType.ENDER;

        EntityPortalResult portalResult = this.portalsManager.get().handlePlayerPortal(
                superiorPlayer, e.getArgs().from, portalType, e.getArgs().to, true);

        handleEntityPortalResult(portalResult, e);
    }

    public void handleEntityPortalResult(GameEvent<GameEventArgs.EntityPortalEvent> e) {
        Location from = e.getArgs().from;
        Location to = e.getArgs().to;

        if (to == null || to.getWorld() == null || from == null || from.getWorld() == null)
            return;

        Entity entity = e.getArgs().entity;

        PortalType portalType = (e.getArgs().cause == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) ?
                PortalType.NETHER : PortalType.ENDER;

        EntityPortalResult portalResult = this.portalsManager.get().handleEntityPortal(entity, from, portalType, to);

        handleEntityPortalResult(portalResult, e);
    }

    private void handleEntityPortalResult(EntityPortalResult portalResult, GameEvent<GameEventArgs.EntityPortalEvent> event) {
        switch (portalResult) {
            case PORTAL_NOT_IN_ISLAND:
                return;
            case DESTINATION_WORLD_DISABLED:
            case PLAYER_IMMUNED_TO_PORTAL:
            case SCHEMATIC_GENERATING_COOLDOWN:
            case DESTINATION_NOT_ISLAND_WORLD:
            case PORTAL_EVENT_CANCELLED:
            case INVALID_SCHEMATIC:
            case WORLD_NOT_UNLOCKED:
            case DESTINATION_ISLAND_NOT_PERMITTED:
            case SUCCEED:
                event.setCancelled();
                return;
            default:
                throw new IllegalStateException("No handling for result: " + portalResult);
        }
    }

}
