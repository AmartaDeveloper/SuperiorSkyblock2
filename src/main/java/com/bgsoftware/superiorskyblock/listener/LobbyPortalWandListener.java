package com.bgsoftware.superiorskyblock.listener;

import com.bgsoftware.common.annotations.Nullable;
import com.bgsoftware.superiorskyblock.SuperiorSkyblockPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LobbyPortalWandListener implements Listener {

    private static final String WAND_NAME = ChatColor.GOLD + "Portal Region Wand";
    private static final String WAND_LORE = ChatColor.GRAY + "Left-click: pos1  |  Right-click: pos2";

    /** Per-player selections: index 0 = pos1 (left-click), index 1 = pos2 (right-click). */
    private static final Map<UUID, Location[]> SELECTIONS = new HashMap<>();

    public LobbyPortalWandListener(SuperiorSkyblockPlugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    // -------------------------------------------------------------------------
    // Event handler
    // -------------------------------------------------------------------------

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent e) {
        if (!isWand(e.getItem()))
            return;

        if (e.getAction() != Action.LEFT_CLICK_BLOCK && e.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;

        if (e.getClickedBlock() == null)
            return;

        e.setCancelled(true);

        Player player = e.getPlayer();
        UUID id = player.getUniqueId();
        Location[] sel = SELECTIONS.computeIfAbsent(id, k -> new Location[2]);

        if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
            sel[0] = e.getClickedBlock().getLocation();
            player.sendMessage(ChatColor.GREEN + "Position 1 set to " + format(sel[0]));
        } else {
            sel[1] = e.getClickedBlock().getLocation();
            player.sendMessage(ChatColor.GREEN + "Position 2 set to " + format(sel[1]));
        }

        if (sel[0] != null && sel[1] != null) {
            player.sendMessage(ChatColor.YELLOW + "Both positions selected. Run /is admin setportalregion to save.");
        }
    }

    // -------------------------------------------------------------------------
    // Static helpers used by commands
    // -------------------------------------------------------------------------

    /**
     * Returns the current selection for a player, or null if pos1 or pos2 has not been set yet.
     * Index 0 = pos1 (left-click), index 1 = pos2 (right-click).
     */
    @Nullable
    public static Location[] getSelection(UUID playerId) {
        Location[] sel = SELECTIONS.get(playerId);
        if (sel == null || sel[0] == null || sel[1] == null)
            return null;
        return sel;
    }

    /** Creates a fresh wand item to give to an admin. */
    public static ItemStack createWand() {
        ItemStack wand = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = wand.getItemMeta();
        meta.setDisplayName(WAND_NAME);
        meta.setLore(Arrays.asList(WAND_LORE));
        wand.setItemMeta(meta);
        return wand;
    }

    /** Returns true if the given item is the portal region wand. */
    public static boolean isWand(ItemStack item) {
        return item != null
                && item.getType() == Material.BLAZE_ROD
                && item.hasItemMeta()
                && item.getItemMeta().hasDisplayName()
                && item.getItemMeta().getDisplayName().equals(WAND_NAME);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static String format(Location loc) {
        return "(" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ")";
    }

}
