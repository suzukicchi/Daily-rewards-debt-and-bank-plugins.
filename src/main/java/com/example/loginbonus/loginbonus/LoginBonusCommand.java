package com.example.loginbonus.loginbonus;

import com.example.loginbonus.MainPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class LoginBonusCommand implements CommandExecutor {

    private final MainPlugin plugin;

    public LoginBonusCommand(MainPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("このコマンドはプレイヤーのみ実行できます。");
            return true;
        }

        if (!player.hasPermission("serverconbini.daily")) {
            player.sendMessage("§cログインボーナスを受け取る権限がありません。");
            return true;
        }

        plugin.getLoginBonusManager().openBonusGUI(player);
        return true;
    }
}
