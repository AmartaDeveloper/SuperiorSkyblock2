package com.bgsoftware.superiorskyblock.commands.admin;

import com.bgsoftware.superiorskyblock.SuperiorSkyblockPlugin;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.schematic.Schematic;
import com.bgsoftware.superiorskyblock.api.service.portals.IslandLobbyPortalService;
import com.bgsoftware.superiorskyblock.api.world.Dimension;
import com.bgsoftware.superiorskyblock.commands.CommandTabCompletes;
import com.bgsoftware.superiorskyblock.commands.ISuperiorCommand;
import com.bgsoftware.superiorskyblock.listener.LobbyPortalWandListener;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class CmdAdminSetPortalRegion implements ISuperiorCommand {

    @Override
    public List<String> getAliases() {
        return Collections.singletonList("setportalregion");
    }

    @Override
    public String getPermission() {
        return "superior.admin.setportalregion";
    }

    @Override
    public String getUsage(Locale locale) {
        return "admin setportalregion <schematic>";
    }

    @Override
    public String getDescription(Locale locale) {
        return "Save the selected portal region for a schematic type (lobby portal block protection).";
    }

    @Override
    public int getMinArgs() {
        return 3;
    }

    @Override
    public int getMaxArgs() {
        return 3;
    }

    @Override
    public boolean canBeExecutedByConsole() {
        return false;
    }

    @Override
    public void execute(SuperiorSkyblockPlugin plugin, CommandSender sender, String[] args) {
        Player player = (Player) sender;

        String schematicName = args[2];

        // Validate the schematic exists
        Schematic schematic = plugin.getSchematics().getSchematic(schematicName);
        if (schematic == null) {
            sender.sendMessage(ChatColor.RED + "Unknown schematic: " + schematicName);
            sender.sendMessage(ChatColor.RED + "Use Tab to see available schematics.");
            return;
        }

        Location[] selection = LobbyPortalWandListener.getSelection(player.getUniqueId());
        if (selection == null) {
            sender.sendMessage(ChatColor.RED + "You need to select both positions first.");
            sender.sendMessage(ChatColor.RED + "Use /is admin portalwand to get the wand.");
            return;
        }

        Location pos1 = selection[0];
        Location pos2 = selection[1];

        if (!pos1.getWorld().equals(pos2.getWorld())) {
            sender.sendMessage(ChatColor.RED + "Both positions must be in the same world.");
            return;
        }

        // The selection must be on an island so we can calculate the relative offset
        Island island = plugin.getGrid().getIslandAt(pos1);
        if (island == null) {
            sender.sendMessage(ChatColor.RED + "Your selection must be on an island. Stand on any island with that schematic pasted and reselect.");
            return;
        }

        // Get island center — relative offsets are the same for every island of the same schematic type
        Dimension dimension = plugin.getSettings().getWorlds().getDefaultWorldDimension();
        Location center = island.getCenter(dimension);

        int minX = Math.min(pos1.getBlockX(), pos2.getBlockX()) - center.getBlockX();
        int minY = Math.min(pos1.getBlockY(), pos2.getBlockY()) - center.getBlockY();
        int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ()) - center.getBlockZ();
        int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX()) - center.getBlockX();
        int maxY = Math.max(pos1.getBlockY(), pos2.getBlockY()) - center.getBlockY();
        int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ()) - center.getBlockZ();

        IslandLobbyPortalService service = plugin.getServices().getService(IslandLobbyPortalService.class);
        service.setProtectedRegion(schematicName, minX, minY, minZ, maxX, maxY, maxZ);

        sender.sendMessage(ChatColor.GREEN + "Portal region saved for schematic '" + schematicName + "'!");
        sender.sendMessage(ChatColor.GRAY + "Relative offsets from island center:");
        sender.sendMessage(ChatColor.GRAY + "  Min: (" + minX + ", " + minY + ", " + minZ + ")");
        sender.sendMessage(ChatColor.GRAY + "  Max: (" + maxX + ", " + maxY + ", " + maxZ + ")");
        sender.sendMessage(ChatColor.YELLOW + "This region is now protected on every '" + schematicName + "' island.");
    }

    @Override
    public List<String> tabComplete(SuperiorSkyblockPlugin plugin, CommandSender sender, String[] args) {
        if (args.length == 3)
            return CommandTabCompletes.getSchematics(plugin, args[2]);
        return Collections.emptyList();
    }

}
