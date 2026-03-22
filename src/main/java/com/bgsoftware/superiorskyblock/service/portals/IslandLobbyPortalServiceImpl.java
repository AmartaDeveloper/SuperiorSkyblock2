package com.bgsoftware.superiorskyblock.service.portals;

import com.bgsoftware.superiorskyblock.SuperiorSkyblockPlugin;
import com.bgsoftware.superiorskyblock.api.player.PlayerStatus;
import com.bgsoftware.superiorskyblock.api.service.portals.IslandLobbyPortalService;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import com.bgsoftware.superiorskyblock.core.threads.BukkitExecutor;
import com.bgsoftware.superiorskyblock.service.IService;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of IslandLobbyPortalService.
 * Handles sending players from island nether portals to lobby server via Velocity/BungeeCord,
 * and manages per-schematic protected regions that prevent portal blocks from being broken or placed.
 */
public class IslandLobbyPortalServiceImpl implements IslandLobbyPortalService, IService {

    private final SuperiorSkyblockPlugin plugin;
    private final boolean enabled;
    private final String destinationServer;
    private final String channelName;

    /**
     * Per-schematic protected regions, stored as int[6]: {minX, minY, minZ, maxX, maxY, maxZ}
     * relative to the island center. Keyed by schematic name (e.g. "normal", "mycel", "desert").
     */
    private final Map<String, int[]> schematicRegions = new HashMap<>();

    public IslandLobbyPortalServiceImpl(SuperiorSkyblockPlugin plugin) {
        this.plugin = plugin;

        File configFile = new File(plugin.getDataFolder(), "config.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);

        this.enabled = config.getBoolean("island-lobby-portal.enabled", false);
        this.destinationServer = config.getString("island-lobby-portal.destination-server", "lobby");
        this.channelName = config.getString("island-lobby-portal.channel-name", "BungeeCord");

        try {
            plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, channelName);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to register plugin messaging channel: " + channelName);
        }

        loadRegionsFromDisk();
    }

    // -------------------------------------------------------------------------
    // IService
    // -------------------------------------------------------------------------

    @Override
    public Class<?> getAPIClass() {
        return IslandLobbyPortalService.class;
    }

    // -------------------------------------------------------------------------
    // IslandLobbyPortalService
    // -------------------------------------------------------------------------

    @Override
    public void sendPlayerToLobby(SuperiorPlayer superiorPlayer) {
        if (!enabled || superiorPlayer == null)
            return;

        Player player = superiorPlayer.asPlayer();
        if (player == null || !player.isOnline())
            return;

        superiorPlayer.setPlayerStatus(PlayerStatus.LEAVING_ISLAND);

        try {
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("Connect");
            out.writeUTF(destinationServer);
            player.sendPluginMessage(plugin, channelName, out.toByteArray());
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send " + player.getName() + " to " + destinationServer);
            e.printStackTrace();
            superiorPlayer.removePlayerStatus(PlayerStatus.LEAVING_ISLAND);
        }

        BukkitExecutor.sync(() -> superiorPlayer.removePlayerStatus(PlayerStatus.LEAVING_ISLAND), 40L);
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public String getDestinationServer() {
        return destinationServer;
    }

    @Override
    public boolean hasProtectedRegion() {
        return !schematicRegions.isEmpty();
    }

    @Override
    public boolean isInProtectedRegion(String schematicName, Location islandCenter, Location blockLocation) {
        int[] region = schematicRegions.get(schematicName);
        if (region == null)
            return false;

        int relX = blockLocation.getBlockX() - islandCenter.getBlockX();
        int relY = blockLocation.getBlockY() - islandCenter.getBlockY();
        int relZ = blockLocation.getBlockZ() - islandCenter.getBlockZ();

        return relX >= region[0] && relX <= region[3]
                && relY >= region[1] && relY <= region[4]
                && relZ >= region[2] && relZ <= region[5];
    }

    @Override
    public void setProtectedRegion(String schematicName, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        schematicRegions.put(schematicName, new int[]{minX, minY, minZ, maxX, maxY, maxZ});
        saveRegionsToDisk();
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private void loadRegionsFromDisk() {
        File regionFile = new File(plugin.getDataFolder(), "portal-region.yml");
        if (!regionFile.exists())
            return;

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(regionFile);
        ConfigurationSection regions = cfg.getConfigurationSection("regions");
        if (regions == null)
            return;

        for (String schematicName : regions.getKeys(false)) {
            ConfigurationSection section = regions.getConfigurationSection(schematicName);
            if (section == null)
                continue;
            int minX = section.getInt("min.x", 0);
            int minY = section.getInt("min.y", 0);
            int minZ = section.getInt("min.z", 0);
            int maxX = section.getInt("max.x", 0);
            int maxY = section.getInt("max.y", 0);
            int maxZ = section.getInt("max.z", 0);
            schematicRegions.put(schematicName, new int[]{minX, minY, minZ, maxX, maxY, maxZ});
        }

        if (!schematicRegions.isEmpty())
            plugin.getLogger().info("Loaded portal regions for schematics: " + schematicRegions.keySet());
    }

    private void saveRegionsToDisk() {
        File regionFile = new File(plugin.getDataFolder(), "portal-region.yml");
        YamlConfiguration cfg = new YamlConfiguration();

        for (Map.Entry<String, int[]> entry : schematicRegions.entrySet()) {
            String path = "regions." + entry.getKey();
            int[] r = entry.getValue();
            cfg.set(path + ".min.x", r[0]);
            cfg.set(path + ".min.y", r[1]);
            cfg.set(path + ".min.z", r[2]);
            cfg.set(path + ".max.x", r[3]);
            cfg.set(path + ".max.y", r[4]);
            cfg.set(path + ".max.z", r[5]);
        }

        try {
            cfg.save(regionFile);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save portal regions: " + e.getMessage());
        }
    }

}
