package com.example.loginbonus.command;

import com.example.loginbonus.MainPlugin;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MainCommand implements CommandExecutor, TabCompleter {

    private final MainPlugin plugin;
    private final NamespacedKey lastClaimKey;
    private final NamespacedKey streakKey;

    public MainCommand(MainPlugin plugin) {
        this.plugin = plugin;
        this.lastClaimKey = new NamespacedKey(plugin, "loginbonus_last_claim");
        this.streakKey = new NamespacedKey(plugin, "loginbonus_streak");
    }

    private String parseColor(String text) {
        if (text == null) return "";
        return text.replace("&", "§");
    }

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

        if (args.length > 0) {
            String subCommand = args[0].toLowerCase();

            switch (subCommand) {
                case "loan":
                case "ローン":
                    if (args.length >= 4) {
                        if (!sender.hasPermission("loginbonus.admin")) {
                            sender.sendMessage(parseColor(plugin.getMessage("admin.no-permission", "prefix.admin")));
                            return true;
                        }

                        String action = args[1].toLowerCase();
                        int amount;
                        try {
                            amount = Integer.parseInt(args[2]);
                            if (amount < 0) {
                                sender.sendMessage(parseColor("&c[エラー] 金額には0以上の整数を指定してください。"));
                                return true;
                            }
                        } catch (NumberFormatException e) {
                            sender.sendMessage(parseColor("&c[エラー] 金額が不正な数値です。"));
                            return true;
                        }

                        List<Player> targets = selectPlayers(sender, args[3]);
                        if (targets.isEmpty()) {
                            sender.sendMessage(parseColor("&c[エラー] 対象のプレイヤーが見つかりません。"));
                            return true;
                        }

                        for (Player targetPlayer : targets) {
                            int currentLoan = 0;
                            try {
                                currentLoan = plugin.getLoanManager().calculateInterestAndGetAmount(targetPlayer);
                            } catch (Exception e) {
                                try {
                                    java.lang.reflect.Method m = plugin.getLoanManager().getClass().getMethod("getLoanAmount", Player.class);
                                    currentLoan = ((Double) m.invoke(plugin.getLoanManager(), targetPlayer)).intValue();
                                } catch (Exception ex) { currentLoan = 0; }
                            }

                            int newLoan = currentLoan;
                            String actionName = "";

                            if (action.equals("set")) {
                                newLoan = amount;
                                actionName = "設定";
                            } else if (action.equals("add")) {
                                newLoan = currentLoan + amount;
                                actionName = "追加";
                            } else if (action.equals("remove")) {
                                newLoan = Math.max(0, currentLoan - amount);
                                actionName = "削減";
                            } else {
                                sender.sendMessage(parseColor("&c[使い方] /" + label + " loan <set|add|remove> <金額> <プレイヤー/@a/@r/@p/@s>"));
                                return true;
                            }

                            try {
                                java.lang.reflect.Method setMethod = plugin.getLoanManager().getClass().getMethod("setLoanAmount", Player.class, double.class);
                                setMethod.invoke(plugin.getLoanManager(), targetPlayer, (double) newLoan);
                            } catch (Exception e) {
                                NamespacedKey loanKey = new NamespacedKey(plugin, "loan_amount");
                                targetPlayer.getPersistentDataContainer().set(loanKey, PersistentDataType.INTEGER, newLoan);
                            }

                            sender.sendMessage(parseColor("&a&l[ローン操作成功] &f" + targetPlayer.getName() + " &7のローン総額を " + actionName + " しました。"));
                            sender.sendMessage(parseColor("  &7元金利息込合計: &e" + currentLoan + "円 &7-> &c" + newLoan + "円"));
                            
                            targetPlayer.sendMessage(parseColor(plugin.getMessage("prefix.loan", "prefix.loan") + "&e管理者によってローンの調整が行われました。現在の借入総額: &c" + newLoan + "円"));
                        }
                        return true;
                    }

                    if (!(sender instanceof Player player)) {
                        sender.sendMessage("このコマンドはプレイヤーのみ実行できます。");
                        return true;
                    }
                    plugin.getLoanManager().openLoanGUI(player);
                    return true;

                case "config":
                case "設定":
                    if (!sender.hasPermission("loginbonus.admin")) {
                        sender.sendMessage(parseColor(plugin.getMessage("admin.no-permission", "prefix.admin")));
                        return true;
                    }

                    if (args.length < 2) {
                        sender.sendMessage(parseColor("&c&l[使い方] &7/" + label + " config <login-bonus|loan|bank|info> [設定項目] [値]"));
                        return true;
                    }

                    String category = args[1].toLowerCase();

                    if (category.equals("info") || category.equals("情報")) {
                        sender.sendMessage(parseColor("&d&l========================================"));
                        sender.sendMessage(parseColor("&b&l【プラグイン設定一覧 (config.yml)】"));
                        
                        sender.sendMessage(parseColor("&d[login-bonus]"));
                        sender.sendMessage(parseColor("  &7- base-reward : &e" + plugin.getConfig().get("login-bonus.base-reward") + " 円"));
                        sender.sendMessage(parseColor("  &7- reset-on-miss : &e" + plugin.getConfig().get("login-bonus.reset-on-miss")));
                        sender.sendMessage(parseColor("  &7- reset-on-new-month : &e" + plugin.getConfig().get("login-bonus.reset-on-new-month")));
                        
                        sender.sendMessage(parseColor("&4[loan]"));
                        sender.sendMessage(parseColor("  &7- max-limit : &e" + plugin.getConfig().get("loan.max-limit") + " 円"));
                        sender.sendMessage(parseColor("  &7- unit : &e" + plugin.getConfig().get("loan.unit") + " 円"));
                        sender.sendMessage(parseColor("  &7- block-bonus-threshold : &e" + plugin.getConfig().get("loan.block-bonus-threshold") + " 円"));
                        sender.sendMessage(parseColor("  &7- interest-rate : &e" + plugin.getConfig().get("loan.interest-rate")));
                        sender.sendMessage(parseColor("  &7- due-days : &e" + plugin.getConfig().get("loan.due-days") + " 日"));

                        // ★ 最新の config.yml の構造に合わせて表示を最適化
                        sender.sendMessage(parseColor("&1[bank]"));
                        sender.sendMessage(parseColor("  &7- max-limit : &e" + plugin.getConfig().get("bank.max-limit") + " 円"));
                        sender.sendMessage(parseColor("  &7- interest-rate : &e" + plugin.getConfig().get("bank.interest-rate") + " (金利)"));
                        sender.sendMessage(parseColor("  &7- interest-interval-hours : &e" + plugin.getConfig().get("bank.interest-interval-hours") + " 時間ごと"));
                        sender.sendMessage(parseColor("  &7- gui-title : &f" + plugin.getConfig().get("bank.gui-title")));
                        sender.sendMessage(parseColor("&d&l========================================"));
                        return true;
                    }

                    if (category.equals("login-bonus")) {
                        if (args.length < 3) {
                            sender.sendMessage(parseColor("&c&l[使い方] &7/" + label + " config login-bonus <base-reward|reset-on-miss|reset-on-new-month|help> [値]"));
                            return true;
                        }
                        String item = args[2].toLowerCase();
                        
                        if (item.equals("help")) {
                            sender.sendMessage(parseColor("&d&l=== [login-bonus] 設定項目の説明 ==="));
                            sender.sendMessage(parseColor("&e- base-reward &7: 通常ログイン時の基本報酬額（整数）"));
                            sender.sendMessage(parseColor("&e- reset-on-miss &7: ログインを逃した時に連続日数をリセットするか（true/false）"));
                            sender.sendMessage(parseColor("&e- reset-on-new-month &7: 新しい月になった時に連続日数をリセットするか（true/false）"));
                            return true;
                        }

                        String configPath = "login-bonus." + item;
                        if (!plugin.getConfig().contains(configPath)) {
                            sender.sendMessage(parseColor("&c&l[エラー] &7指定された項目 '" + item + "' は存在しません。help でご確認ください。"));
                            return true;
                        }

                        if (args.length < 4) {
                            sender.sendMessage(parseColor("&a&l[設定確認] &e" + configPath + " &7の現在の値: &f" + plugin.getConfig().get(configPath)));
                        } else {
                            handleConfigSet(sender, configPath, args[3]);
                        }
                        return true;
                    }

                    if (category.equals("loan") || category.equals("lone")) {
                        if (args.length < 3) {
                            sender.sendMessage(parseColor("&c&l[使い方] &7/" + label + " config loan <max-limit|unit|block-bonus-threshold|interest-rate|due-days|help> [値]"));
                            return true;
                        }
                        String item = args[2].toLowerCase();

                        if (item.equals("help")) {
                            sender.sendMessage(parseColor("&4&l=== [loan] 設定項目の説明 ==="));
                            sender.sendMessage(parseColor("&e- max-limit &7: プレイヤーが借入できる最大上限額（整数）"));
                            sender.sendMessage(parseColor("&e- unit &7: 1回あたりの借入単位（整数）"));
                            sender.sendMessage(parseColor("&e- block-bonus-threshold &7: ログボを差し押さえる借金閾値（整数）"));
                            sender.sendMessage(parseColor("&e- interest-rate &7: 24時間ごとの金利（小数 例: 0.05 = 5%）"));
                            sender.sendMessage(parseColor("&e- due-days &7: 返済期限の日数（整数）"));
                            return true;
                        }

                        String configPath;
                        switch (item) {
                            case "max-limit": configPath = "loan.max-limit"; break;
                            case "unit": configPath = "loan.unit"; break;
                            case "block-bonus-threshold": configPath = "loan.block-bonus-threshold"; break;
                            case "interest-rate": configPath = "loan.interest-rate"; break;
                            case "due-days": configPath = "loan.due-days"; break;
                            default: configPath = "loan." + item; break;
                        }

                        if (!plugin.getConfig().contains(configPath)) {
                            sender.sendMessage(parseColor("&c&l[エラー] &7指定された項目 '" + item + "' は存在しません。help でご確認ください。"));
                            return true;
                        }

                        if (args.length < 4) {
                            sender.sendMessage(parseColor("&a&l[設定確認] &e" + item + " &7の現在の値: &f" + plugin.getConfig().get(configPath)));
                        } else {
                            handleConfigSet(sender, configPath, args[3]);
                        }
                        return true;
                    }

                    // ★ 最新の config.yml 内の全 bank 設定項目に対応
                    if (category.equals("bank") || category.equals("銀行") || category.equals("b")) {
                        if (args.length < 3) {
                            sender.sendMessage(parseColor("&c&l[使い方] &7/" + label + " config bank <max-limit|interest-rate|interest-interval-hours|gui-title|help> [値]"));
                            return true;
                        }
                        String item = args[2].toLowerCase();

                        if (item.equals("help")) {
                            sender.sendMessage(parseColor("&1&l=== [bank] 設定項目の説明 ==="));
                            sender.sendMessage(parseColor("&e- max-limit &7: 銀行口座に預金できる最大上限額（整数）"));
                            sender.sendMessage(parseColor("&e- interest-rate &7: 定期預金の金利割合（小数 例: 0.02 = 2%）"));
                            sender.sendMessage(parseColor("&e- interest-interval-hours &7: 利息が付与される時間周期（整数時間）"));
                            sender.sendMessage(parseColor("&e- gui-title &7: 銀行GUIの画面タイトル名"));
                            return true;
                        }

                        String configPath;
                        switch (item) {
                            case "max-limit": case "limit": configPath = "bank.max-limit"; break;
                            case "interest-rate": case "rate": configPath = "bank.interest-rate"; break;
                            case "interest-interval-hours": case "interval": configPath = "bank.interest-interval-hours"; break;
                            case "gui-title": case "title": configPath = "bank.gui-title"; break;
                            default: configPath = "bank." + item; break;
                        }

                        if (!plugin.getConfig().contains(configPath)) {
                            sender.sendMessage(parseColor("&c&l[エラー] &7指定された項目 '" + item + "' は存在しません。help でご確認ください。"));
                            return true;
                        }

                        if (args.length < 4) {
                            sender.sendMessage(parseColor("&a&l[設定確認] &e" + item + " &7の現在の値: &f" + plugin.getConfig().get(configPath)));
                        } else {
                            // 第4引数以降にスペース区切りの文字列が来てもタイトルが崩れないよう結合処理
                            StringBuilder valBuilder = new StringBuilder(args[3]);
                            for (int i = 4; i < args.length; i++) {
                                valBuilder.append(" ").append(args[i]);
                            }
                            handleConfigSet(sender, configPath, valBuilder.toString());
                        }
                        return true;
                    }

                    sender.sendMessage(parseColor("&c&l[エラー] &7無効なカテゴリです。'login-bonus', 'loan', 'bank', または 'info' を指定してください。"));
                    return true;

                case "reload":
                case "リロード":
                    if (!sender.hasPermission("loginbonus.admin")) {
                        sender.sendMessage(parseColor(plugin.getMessage("admin.no-permission", "prefix.admin")));
                        return true;
                    }
                    plugin.reloadConfig();
                    sender.sendMessage(parseColor(plugin.getMessage("admin.reload-success", "prefix.admin")));
                    return true;

                case "info":
                case "情報":
                    if (!sender.hasPermission("loginbonus.use")) {
                        sender.sendMessage(parseColor(plugin.getMessage("admin.no-permission", "prefix.admin")));
                        return true;
                    }

                    Player target = null;
                    if (sender.hasPermission("loginbonus.info")) {
                        if (args.length < 2) {
                            if (sender instanceof Player player) target = player;
                            else {
                                sender.sendMessage(parseColor("&c&l[使い方] &7/" + label + " info <プレイヤー名>"));
                                return true;
                            }
                        } else {
                            target = Bukkit.getPlayer(args[1]);
                        }
                    } else {
                        if (!(sender instanceof Player player)) {
                            sender.sendMessage("このコマンドはプレイヤーのみ実行できます。");
                            return true;
                        }
                        target = player;
                        if (args.length >= 2 && !args[1].equalsIgnoreCase(player.getName())) {
                            player.sendMessage(parseColor("&c&l[セキュリティ] &7他人の情報は閲覧できません。あなたの情報を表示します。"));
                        }
                    }

                    if (target == null) {
                        sender.sendMessage(parseColor("&c指定されたプレイヤーはオフラインか、存在しません。"));
                        return true;
                    }

                    double currentBalance = 0;
                    if (plugin.getEconomy() != null) {
                        currentBalance = plugin.getEconomy().getBalance(target);
                    }

                    int currentBankBalance = plugin.getBankManager().getBankBalance(target.getUniqueId());

                    Integer streak = target.getPersistentDataContainer().get(streakKey, PersistentDataType.INTEGER);
                    String lastClaim = target.getPersistentDataContainer().get(lastClaimKey, PersistentDataType.STRING);
                    
                    int currentLoan = 0;
                    try {
                        currentLoan = plugin.getLoanManager().calculateInterestAndGetAmount(target);
                    } catch (NoSuchMethodError e) {
                        try {
                            java.lang.reflect.Method m = plugin.getLoanManager().getClass().getMethod("getLoanAmount", Player.class);
                            currentLoan = ((Double) m.invoke(plugin.getLoanManager(), target)).intValue();
                        } catch (Exception ex) {
                            currentLoan = 0;
                        }
                    }
                    
                    long loanStart = plugin.getLoanManager().getLoanStartDate(target);
                    int currentStreak = (streak != null) ? streak : 0;
                    String claimStatus = (lastClaim != null) ? lastClaim : "未受け取り";
                    
                    String loanStatus = "&aなし";
                    String dueDateStatus = "&7-";
                    
                    if (currentLoan > 0) {
                        loanStatus = "&c" + currentLoan + " 円";
                        int allowedDays = plugin.getConfig().getInt("loan.due-days", 7);
                        long elapsedMs = System.currentTimeMillis() - loanStart;
                        long elapsedDays = elapsedMs / (1000 * 60 * 60 * 24);
                        long remainingDays = allowedDays - elapsedDays;
                        
                        dueDateStatus = remainingDays >= 0 ? "&e残り " + remainingDays + " 日" : "&4&l期限切れ (差し押さえ中)";
                    }

                    String titleName = sender.equals(target) ? "あなた" : target.getName();
                    sender.sendMessage(parseColor("&d&l========================================"));
                    sender.sendMessage(parseColor("&b&l【ステータス情報】 &f&n" + titleName));
                    sender.sendMessage(parseColor("&7・UUID: &f" + target.getUniqueId()));
                    sender.sendMessage(parseColor("&a・現在の所持金: &e" + (int)currentBalance + " 円")); 
                    sender.sendMessage(parseColor("&1・銀行預金: &e" + currentBankBalance + " 円")); 
                    sender.sendMessage(parseColor("&d[ログインボーナス情報]"));
                    sender.sendMessage(parseColor("  &7- 現在の連続日数: &e" + currentStreak + " 日"));
                    sender.sendMessage(parseColor("  &7- 最終受取日: &f" + claimStatus));
                    sender.sendMessage(parseColor("&4[ローン情報]"));
                    sender.sendMessage(parseColor("  &7- 現在の借入総額(利息込): " + loanStatus));
                    sender.sendMessage(parseColor("  &7- 返済期限ステータス: " + dueDateStatus));
                    sender.sendMessage(parseColor("&d&l========================================"));
                    return true;

                case "daily":
                case "デイリー":
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage("このコマンドはプレイヤーのみ実行できます。");
                        return true;
                    }
                    plugin.getLoginBonusManager().openBonusGUI(player);
                    return true;
            }
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("このコマンドはプレイヤーのみ実行できます。");
            return true;
        }

        if (label.equalsIgnoreCase("loan") || label.equals("ローン")) {
            plugin.getLoanManager().openLoanGUI(player);
            return true;
        }
        
        if (label.equalsIgnoreCase("bank") || label.equals("銀行")) {
            plugin.getBankManager().openBankGUI(player);
            return true;
        }

        plugin.getLoginBonusManager().openBonusGUI(player);
        return true;
    }

    private void handleConfigSet(CommandSender sender, String configPath, String valueStr) {
        if (plugin.getConfig().isInt(configPath)) {
            try {
                plugin.getConfig().set(configPath, Integer.parseInt(valueStr));
            } catch (NumberFormatException e) {
                sender.sendMessage(parseColor("&c&l[エラー] &7この項目は数値(整数)で指定してください。"));
                return;
            }
        } else if (plugin.getConfig().isDouble(configPath)) {
            try {
                plugin.getConfig().set(configPath, Double.parseDouble(valueStr));
            } catch (NumberFormatException e) {
                sender.sendMessage(parseColor("&c&l[エラー] &7この項目は数値(小数)で指定してください。"));
                return;
            }
        } else if (plugin.getConfig().isBoolean(configPath)) {
            plugin.getConfig().set(configPath, Boolean.parseBoolean(valueStr));
        } else {
            plugin.getConfig().set(configPath, valueStr);
        }

        plugin.saveConfig();
        plugin.reloadConfig();
        
        sender.sendMessage(parseColor(plugin.getMessage("admin.reload-success", "prefix.admin")));
        sender.sendMessage(parseColor("&a&l[設定変更] &e" + configPath + " &aを &e" + valueStr + " &aに変更し、物理保存しました。"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (!sender.hasPermission("loginbonus.admin")) {
            if (args.length == 1) {
                for (String s : Arrays.asList("daily", "デイリー", "loan", "ローン", "info", "情報")) {
                    if (s.startsWith(args[0].toLowerCase())) completions.add(s);
                }
            }
            else if (args.length == 2 && (args[0].equalsIgnoreCase("info") || args[0].equals("情報"))) {
                if (sender.hasPermission("loginbonus.info")) {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) completions.add(p.getName());
                    }
                }
            }
            return completions;
        }

        if (args.length == 1) {
            List<String> sub = new ArrayList<>(Arrays.asList("daily", "デイリー", "loan", "ローン", "info", "情報", "reload", "リロード", "config"));
            for (String s : sub) if (s.startsWith(args[0].toLowerCase())) completions.add(s);
        } 
        else if (args.length == 2 && (args[0].equalsIgnoreCase("loan") || args[0].equals("ローン"))) {
            for (String s : Arrays.asList("set", "add", "remove")) {
                if (s.startsWith(args[1].toLowerCase())) completions.add(s);
            }
        }
        else if (args.length == 4 && (args[0].equalsIgnoreCase("loan") || args[0].equals("ローン"))) {
            for (String s : Arrays.asList("@a", "@r", "@p", "@s")) {
                if (s.startsWith(args[3].toLowerCase())) completions.add(s);
            }
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[3].toLowerCase())) completions.add(p.getName());
            }
        }
        else if (args.length == 2 && args[0].equalsIgnoreCase("config")) {
            for (String s : Arrays.asList("login-bonus", "loan", "bank", "info")) {
                if (s.startsWith(args[1].toLowerCase())) completions.add(s);
            }
        } 
        else if (args.length == 3 && args[0].equalsIgnoreCase("config")) {
            if (args[1].equalsIgnoreCase("login-bonus")) {
                for (String s : Arrays.asList("base-reward", "reset-on-miss", "reset-on-new-month", "help")) {
                    if (s.startsWith(args[2].toLowerCase())) completions.add(s);
                }
            } else if (args[1].equalsIgnoreCase("loan") || args[1].equalsIgnoreCase("lone")) {
                for (String s : Arrays.asList("max-limit", "unit", "block-bonus-threshold", "interest-rate", "due-days", "help")) {
                    if (s.startsWith(args[2].toLowerCase())) completions.add(s);
                }
            }
            // ★ Tab補完リストの最適化
            else if (args[1].equalsIgnoreCase("bank") || args[1].equalsIgnoreCase("銀行")) {
                for (String s : Arrays.asList("max-limit", "interest-rate", "interest-interval-hours", "gui-title", "help")) {
                    if (s.startsWith(args[2].toLowerCase())) completions.add(s);
                }
            }
        }
        else if (args.length == 4 && args[0].equalsIgnoreCase("config") && !args[2].equalsIgnoreCase("help")) {
            String item = args[2].toLowerCase();
            if (item.equals("reset-on-miss") || item.equals("reset-on-new-month")) {
                for (String s : Arrays.asList("true", "false")) {
                    if (s.startsWith(args[3].toLowerCase())) completions.add(s);
                }
            }
        }
        else if (args.length == 2 && (args[0].equalsIgnoreCase("info") || args[0].equals("情報"))) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) completions.add(p.getName());
            }
        }
        return completions;
    }
}
