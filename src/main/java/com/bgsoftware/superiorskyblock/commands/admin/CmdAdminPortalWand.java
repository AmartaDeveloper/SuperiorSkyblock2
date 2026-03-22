package com.bgsoftware.superiorskyblock.commands.admin;

import com.bgsoftware.superiorskyblock.SuperiorSkyblockPlugin;
import com.bgsoftware.superiorskyblock.commands.ISuperiorCommand;
import com.bgsoftware.superiorskyblock.listener.LobbyPortalWandListener;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class CmdAdminPortalWand implements ISuperiorCommand {

    @Override
    public List<String> getAliases() {
        return Collections.singletonList("portalwand");
    }

    @Override
    public String getPermission() {
        return "superior.admin.portalwand";
    }

    @Override
    public String getUsage(Locale locale) {
        return "admin portalwand";
    }

    @Override
    public String getDescription(Locale locale) {
        return "Get the portal region selection wand.";
    }

    @Override
    public int getMinArgs() {
        return 2;
    }

    @Override
    public int getMaxArgs() {
        return 2;
    }

    @Override
    public boolean canBeExecutedByConsole() {
        return false;
    }

    @Override
    public void execute(SuperiorSkyblockPlugin plugin, CommandSender sender, String[] args) {
        Player player = (Player) sender;
        player.getInventory().addItem(LobbyPortalWandListener.createWand());
        sender.sendMessage(ChatColor.GREEN + "You received the Portal Region Wand.");
        sender.sendMessage(ChatColor.GRAY + "Left-click a block to set pos1, right-click for pos2.");
        sender.sendMessage(ChatColor.GRAY + "Then run /is admin setportalregion to save.");
    }

    @Override
    public List<String> tabComplete(SuperiorSkyblockPlugin plugin, CommandSender sender, String[] args) {
        return Collections.emptyList();
    }

}
