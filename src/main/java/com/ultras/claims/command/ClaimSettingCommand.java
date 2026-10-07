package com.ultras.claims.command;

import com.ultras.claims.UltrasClaims;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

/** /claim_setting - personal settings menu. */
public final class ClaimSettingCommand implements CommandExecutor, TabCompleter {
    private final UltrasClaims plugin;

    public ClaimSettingCommand(UltrasClaims plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }
        plugin.gui().openPlayerSettings(p);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }
}
