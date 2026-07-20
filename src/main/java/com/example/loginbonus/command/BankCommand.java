package com.example.loginbonus.command;

import com.example.loginbonus.MainPlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class BankCommand implements CommandExecutor, TabCompleter {

    private final MainPlugin plugin;

    public BankCommand(MainPlugin plugin) {
        this.plugin = plugin;
    }

    private String parseColor(String text) {
        if (text == null) return "";
        return text.replace("&", "§");
    }

    /**
     * バニラ風のターゲットセレクタおよびプレイヤー名を解析する処理
     */
    private List<Player> selectPlayers(CommandSender sender, String selector) {
        List<Player> targets = new ArrayList<>();
        
        if (selector.equalsIgnoreCase("@a")) {
            targets.addAll(Bukkit.getOnlinePlayers());
        } else if (selector.equalsIgnoreCase("@r")) {
            List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
            if (!online.isEmpty()) {
                Collections.shuffle(online);
                targets.add(online.get(0));
            }
        } else if (selector.equalsIgnoreCase("@s")) {
            if (sender instanceof Player player) {
                targets.add(player);
            } else {
                sender.sendMessage(parseColor("&c[エラー] コンソールから @s は使用できません。"));
            }
        } else if (selector.equalsIgnoreCase("@p")) {
            if (sender instanceof Player player) {
                Player closest = null;
                double closestDistance = Double.MAX_VALUE;
                for (Player p : player.getWorld().getPlayers()) {
                    if (p.equals(player)) continue;
                    double dist = p.getLocation().distanceSquared(player.getLocation());
                    if (dist < closestDistance) {
                        closestDistance = dist;
                        closest = p;
                    }
                }
                targets.add(closest != null ? closest : player);
            } else {
                if (!Bukkit.getOnlinePlayers().isEmpty()) {
                    targets.add(Bukkit.getOnlinePlayers().iterator().next());
                }
            }
        } else {
            Player target = Bukkit.getPlayer(selector);
            if (target != null) {
                targets.add(target);
            }
        }
        
        return targets;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 3) {
            if (!sender.hasPermission("loginbonus.admin")) {
                sender.sendMessage(parseColor(plugin.getMessage("admin.no-permission", "prefix.admin")));
                return true;
            }

            String action = args[0].toLowerCase();
            int amount;
            try {
                amount = Integer.parseInt(args[1]);
                if (amount < 0) {
                    sender.sendMessage(parseColor("&c[エラー] 金額には0以上の整数を指定してください。"));
                    return true;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage(parseColor("&c[エラー] 金額が不正な数値です。"));
                return true;
            }

            List<Player> targets = selectPlayers(sender, args[2]);
            if (targets.isEmpty()) {
                sender.sendMessage(parseColor("&c[エラー] 対象のプレイヤーが見つかりません。"));
                return true;
            }

            for (Player targetPlayer : targets) {
                int currentBank = plugin.getBankManager().getBankBalance(targetPlayer.getUniqueId());
                int newBank = currentBank;
                String actionName = "";

                if (action.equals("set")) {
                    newBank = amount;
                    actionName = "設定";
                } else if (action.equals("add")) {
                    newBank = currentBank + amount;
                    actionName = "追加";
                } else if (action.equals("remove")) {
                    newBank = Math.max(0, currentBank - amount);
                    actionName = "削減";
                } else {
                    sender.sendMessage(parseColor("&c[使い方] /" + label + " <set|add|remove> <金額> <プレイヤー名/@a/@r/@p/@s>"));
                    return true;
                }

                plugin.getBankManager().setBankBalance(targetPlayer.getUniqueId(), newBank);

                sender.sendMessage(parseColor("&a&l[銀行口座操作成功] &f" + targetPlayer.getName() + " &7の銀行残高を " + actionName + " しました。"));
                sender.sendMessage(parseColor("  &7旧残高: &e" + currentBank + "円 &7-> &b新残高: " + newBank + "円"));
                
                targetPlayer.sendMessage(parseColor("&b&l[銀行] &e管理者によって口座残高の調整が行われました。現在の預金残高: &b" + newBank + "円"));
            }
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("このコマンドはゲーム内（プレイヤー）からのみ実行できます。");
            sender.sendMessage(parseColor("&c管理者コマンドの使い方: /" + label + " <set|add|remove> <金額> <MCID>"));
            return true;
        }

        plugin.getBankManager().openBankGUI(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (!sender.hasPermission("loginbonus.admin")) {
            return completions;
        }

        if (args.length == 1) {
            for (String s : Arrays.asList("set", "add", "remove")) {
                if (s.startsWith(args[0].toLowerCase())) completions.add(s);
            }
        } 
        else if (args.length == 3) {
            for (String s : Arrays.asList("@a", "@r", "@p", "@s")) {
                if (s.startsWith(args[2].toLowerCase())) completions.add(s);
            }
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[2].toLowerCase())) completions.add(p.getName());
            }
        }

        return completions;
    }
}
