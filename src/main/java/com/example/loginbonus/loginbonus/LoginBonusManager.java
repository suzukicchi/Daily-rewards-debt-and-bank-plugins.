package com.example.loginbonus.loginbonus;

import com.example.loginbonus.MainPlugin;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class LoginBonusManager implements Listener {

    private final MainPlugin plugin;
    private final NamespacedKey lastClaimKey;
    private final NamespacedKey streakKey;

    public LoginBonusManager(MainPlugin plugin) {
        this.plugin = plugin;
        this.lastClaimKey = new NamespacedKey(plugin, "loginbonus_last_claim");
        this.streakKey = new NamespacedKey(plugin, "loginbonus_streak");
    }

    private String parseColor(String text) {
        if (text == null) return "";
        return text.replace("&", "§");
    }

    private String getTodayDateString() {
        return new SimpleDateFormat("yyyy-MM-dd").format(new Date());
    }

    private String getYesterdayDateString() {
        long yesterdayMs = System.currentTimeMillis() - (24 * 60 * 60 * 1000);
        return new SimpleDateFormat("yyyy-MM-dd").format(new Date(yesterdayMs));
    }

    public boolean hasClaimedToday(Player player) {
        String lastClaim = player.getPersistentDataContainer().get(lastClaimKey, PersistentDataType.STRING);
        return getTodayDateString().equals(lastClaim);
    }

    private void checkAndResetStreak(Player player) {
        if (hasClaimedToday(player)) return;

        String lastClaim = player.getPersistentDataContainer().get(lastClaimKey, PersistentDataType.STRING);
        
        if (lastClaim == null) return;

        if (plugin.getConfig().getBoolean("login-bonus.reset-on-new-month", true)) {
            try {
                int lastClaimMonth = Integer.parseInt(lastClaim.split("-")[1]);
                int currentMonth = LocalDate.now().getMonthValue();

                if (lastClaimMonth != currentMonth) {
                    player.getPersistentDataContainer().set(streakKey, PersistentDataType.INTEGER, 0);
                    return;
                }
            } catch (Exception e) {
                
            }
        }

        String yesterday = getYesterdayDateString();
        if (!lastClaim.equals(yesterday)) {
            boolean shouldReset = plugin.getConfig().getBoolean("login-bonus.reset-on-miss", true);
            if (shouldReset) {
                player.getPersistentDataContainer().set(streakKey, PersistentDataType.INTEGER, 0);
            }
        }
    }

    public void openBonusGUI(Player player) {
        if (plugin.getLoanManager().checkAndEnforceSeizure(player)) {
            String deniedMsg = plugin.getMessage("login-bonus.seizure-denied", "prefix.login");
            player.sendMessage(parseColor(deniedMsg));
            
            plugin.getLoanManager().openLoanGUI(player);
            return;
        }

        checkAndResetStreak(player);

        String titleStr = plugin.getConfig().getString("login-bonus.gui-title", "&d&l本日のログインボーナス");
        Inventory gui = Bukkit.createInventory(player, 54, parseColor(titleStr));

        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        if (fillerMeta != null) { fillerMeta.setDisplayName(" "); filler.setItemMeta(fillerMeta); }
        for (int i = 0; i < 54; i++) {
            gui.setItem(i, filler);
        }

        Integer currentStreak = player.getPersistentDataContainer().get(streakKey, PersistentDataType.INTEGER);
        if (currentStreak == null) currentStreak = 0;
        int nextClaimDay = currentStreak + 1;
        boolean claimedToday = hasClaimedToday(player);

        int[] calendarSlots = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43,
            46, 47, 48
        };

        int baseReward = plugin.getConfig().getInt("login-bonus.base-reward", 1000);
        int currentMonth = LocalDate.now().getMonthValue();

        for (int i = 0; i < 31; i++) {
            int day = i + 1;
            int slot = calendarSlots[i];

            ItemStack item;
            ItemMeta meta;

            if (!claimedToday && day == nextClaimDay) {
                item = new ItemStack(Material.CHEST_MINECART);
                meta = item.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(parseColor("&a&l👉 [" + day + "日目] を受け取る"));
                    List<String> lore = new ArrayList<>();
                    int streakReward = plugin.getConfig().getInt("login-bonus.monthly-streaks.month-" + currentMonth + "." + day, 0);
                    lore.add(parseColor("&7・基本報酬: &e" + baseReward + "円"));
                    if (streakReward > 0) {
                        lore.add(parseColor("&b・連続ボーナス: &e+" + streakReward + "円"));
                    }
                    lore.add(parseColor("&a▶ クリックして報酬を獲得！"));
                    meta.setLore(lore);
                }
            } else if (day < nextClaimDay || (claimedToday && day == currentStreak)) {
                item = new ItemStack(Material.MINECART);
                meta = item.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(parseColor("&7" + day + "日目 [受取済み]"));
                    List<String> lore = new ArrayList<>();
                    lore.add(parseColor("&8この報酬は受け取り済みです。"));
                    meta.setLore(lore);
                }
            } else {
                item = new ItemStack(Material.RAIL);
                meta = item.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(parseColor("&8" + day + "日目 (未到達)"));
                    List<String> lore = new ArrayList<>();
                    int streakReward = plugin.getConfig().getInt("login-bonus.monthly-streaks.month-" + currentMonth + "." + day, 0);
                    if (streakReward > 0) {
                        lore.add(parseColor("&d⭐ 連続ボーナス対象日: &e+" + streakReward + "円"));
                    }
                    lore.add(parseColor("&8毎日ログインしてここまで進めよう！"));
                    meta.setLore(lore);
                }
            }

            if (meta != null) item.setItemMeta(meta);
            gui.setItem(slot, item);
        }

        ItemStack infoItem = new ItemStack(Material.CLOCK);
        ItemMeta infoMeta = infoItem.getItemMeta();
        if (infoMeta != null) {
            String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy年MM月dd日"));
            infoMeta.setDisplayName(parseColor("&b&l【本日の日付】 &f" + dateStr));
            List<String> lore = new ArrayList<>();
            lore.add(parseColor("&7・現在の連続ログイン: &e" + currentStreak + "日"));
            boolean shouldReset = plugin.getConfig().getBoolean("login-bonus.reset-on-miss", true);
            boolean monthReset = plugin.getConfig().getBoolean("login-bonus.reset-on-new-month", true);
            lore.add(parseColor("&7・ログインミス時のリセット: " + (shouldReset ? "&cあり" : "&aなし")));
            lore.add(parseColor("&7・月替わり時のリセット: " + (monthReset ? "&cあり" : "&aなし")));
            infoMeta.setLore(lore);
            infoItem.setItemMeta(infoMeta);
        }
        gui.setItem(4, infoItem);

        ItemStack guideItem = new ItemStack(Material.BOOK);
        ItemMeta guideMeta = guideItem.getItemMeta();
        if (guideMeta != null) {
            guideMeta.setDisplayName(parseColor("&e&l💡 ガイド説明"));
            List<String> lore = new ArrayList<>();
            lore.add(parseColor("&7毎日1回、ログイン報酬を獲得できます。"));
            lore.add(parseColor("&7連続でログインすると、特定の日に"));
            lore.add(parseColor("&d特別なマンスリーボーナス &7が加算されます！"));
            guideMeta.setLore(lore);
            guideItem.setItemMeta(guideMeta);
        }
        gui.setItem(0, guideItem);

        ItemStack backItem = new ItemStack(Material.BOOKSHELF);
        ItemMeta backMeta = backItem.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName(parseColor("&c&l◀ 無人契約機（ローン窓口）へ戻る"));
            List<String> lore = new ArrayList<>();
            lore.add(parseColor("&7ローン窓口の借入・返済画面を開きます。"));
            backMeta.setLore(lore);
            backItem.setItemMeta(backMeta);
        }
        gui.setItem(45, backItem);

        ItemStack bankItem = new ItemStack(Material.EMERALD);
        ItemMeta bankMeta = bankItem.getItemMeta();
        if (bankMeta != null) {
            bankMeta.setDisplayName(parseColor("&1&l🏦 銀行画面へ"));
            List<String> lore = new ArrayList<>();
            lore.add(parseColor("&7銀行の預金・引出画面を開きます。"));
            bankMeta.setLore(lore);
            bankItem.setItemMeta(bankMeta);
        }
        gui.setItem(52, bankItem);

        ItemStack closeItem = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeItem.getItemMeta();
        if (closeMeta != null) {
            closeMeta.setDisplayName(parseColor("&c&l❌ 画面を閉じる"));
            closeItem.setItemMeta(closeMeta);
        }
        gui.setItem(53, closeItem);

        player.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String titleStr = plugin.getConfig().getString("login-bonus.gui-title", "&d&l本日のログインボーナス");
        if (!event.getView().getTitle().equals(parseColor(titleStr))) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();

        if (slot == 53) {
            player.closeInventory();
            return;
        }

        if (slot == 45) {
            plugin.getLoanManager().openLoanGUI(player);
            return;
        }

        if (slot == 52) {
            plugin.getBankManager().openBankGUI(player);
            return;
        }

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() != Material.CHEST_MINECART) return;

        if (hasClaimedToday(player)) {
            String alreadyMsg = plugin.getMessage("login-bonus.claimed-already", "prefix.login");
            player.sendMessage(parseColor(alreadyMsg));
            return;
        }

        Economy eco = plugin.getEconomy();
        if (eco == null) return;

        int baseReward = plugin.getConfig().getInt("login-bonus.base-reward", 1000);
        
        Integer streak = player.getPersistentDataContainer().get(streakKey, PersistentDataType.INTEGER);
        if (streak == null) streak = 0;
        streak++;
        
        int currentMonth = LocalDate.now().getMonthValue();
        String streakPath = "login-bonus.monthly-streaks.month-" + currentMonth + "." + streak;
        int streakReward = plugin.getConfig().getInt(streakPath, 0); 
        
        int totalReward = baseReward + streakReward;

        player.getPersistentDataContainer().set(lastClaimKey, PersistentDataType.STRING, getTodayDateString());
        player.getPersistentDataContainer().set(streakKey, PersistentDataType.INTEGER, streak);

        eco.depositPlayer(player, totalReward);
        
        String successMsg = plugin.getMessage("login-bonus.success", "prefix.login").replace("%amount%", String.valueOf(baseReward));
        player.sendMessage(parseColor(successMsg));

        if (streakReward > 0) {
            String streakMsg = plugin.getMessage("login-bonus.streak-success", "prefix.login")
                                     .replace("%month%", String.valueOf(currentMonth))
                                     .replace("%streak%", String.valueOf(streak))
                                     .replace("%amount%", String.valueOf(streakReward));
            player.sendMessage(parseColor(streakMsg));
        }
        
        openBonusGUI(player);
    }
}
