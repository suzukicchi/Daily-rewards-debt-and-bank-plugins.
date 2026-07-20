package com.example.loginbonus.bank;

import com.example.loginbonus.MainPlugin;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BankManager implements Listener {

    private final MainPlugin plugin;
    private final File dataFile;
    private FileConfiguration bankConfig;
    private final NamespacedKey clickAmountKey; // ボタンの金額識別用NBT

    public BankManager(MainPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "bank_data.yml");
        this.clickAmountKey = new NamespacedKey(plugin, "bank_click_amount");
        loadBankData();
    }

    private String parseColor(String text) {
        if (text == null) return "";
        return text.replace("&", "§");
    }

    public void loadBankData() {
        if (!dataFile.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                dataFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        this.bankConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    public void saveBankData() {
        try {
            bankConfig.save(dataFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public int getBankBalance(UUID uuid) {
        return bankConfig.getInt(uuid.toString() + ".balance", 0);
    }

    public void setBankBalance(UUID uuid, int amount) {
        int maxLimit = plugin.getConfig().getInt("bank.max-limit", 10000000);
        bankConfig.set(uuid.toString() + ".balance", Math.max(0, Math.min(amount, maxLimit)));
        saveBankData();
    }

    /**
     * 銀行GUIを開く
     */
    public void openBankGUI(Player player) {
        String titleStr = plugin.getConfig().getString("bank.gui-title", "&1&l【ペリロッド銀行】預金・引出");
        Inventory gui = Bukkit.createInventory(player, 18, parseColor(titleStr));

        int currentBalance = getBankBalance(player.getUniqueId());
        int maxLimit = plugin.getConfig().getInt("bank.max-limit", 10000000);
        double interestPercent = plugin.getConfig().getDouble("bank.interest-rate", 0.02) * 100;
        int intervalHours = plugin.getConfig().getInt("bank.interest-interval-hours", 24);


        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = info.getItemMeta();
        if (infoMeta != null) {
            infoMeta.setDisplayName(parseColor("&b&l【口座ご利用状況】"));
            List<String> lore = new ArrayList<>();
            lore.add(parseColor("&7・現在の預金残高: &e" + currentBalance + "円"));
            lore.add(parseColor("&7・預金上限額: &7" + maxLimit + "円"));
            lore.add(parseColor("&7・現在の金利: &a" + intervalHours + "時間ごとに " + interestPercent + "%"));
            infoMeta.setLore(lore);
            info.setItemMeta(infoMeta);
        }
        gui.setItem(0, info);

        ItemStack loanJump = new ItemStack(Material.REDSTONE_TORCH);
        ItemMeta loanJumpMeta = loanJump.getItemMeta();
        if (loanJumpMeta != null) {
            loanJumpMeta.setDisplayName(parseColor("&4&l💰 ローン画面へ"));
            loanJump.setItemMeta(loanJumpMeta);
        }
        gui.setItem(7, loanJump);

        ItemStack dailyJump = new ItemStack(Material.CHEST_MINECART);
        ItemMeta jumpMeta = dailyJump.getItemMeta();
        if (jumpMeta != null) {
            jumpMeta.setDisplayName(parseColor("&d&l📆 デイリー報酬画面へ"));
            dailyJump.setItemMeta(jumpMeta);
        }
        gui.setItem(8, dailyJump);

        int[] amounts = {100, 1000, 10000, 50000};
        int[] depositSlots = {2, 3, 4, 5};
        for (int i = 0; i < amounts.length; i++) {
            int amt = amounts[i];
            ItemStack btn = new ItemStack(Material.EMERALD);
            ItemMeta meta = btn.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(parseColor("&a&l型番: 現金を預ける (&e+" + amt + "円&a&l)"));
                List<String> lore = new ArrayList<>();
                lore.add(parseColor("&7クリックすると、手持ちから銀行口座へ預金します。"));
                meta.setLore(lore);
                meta.getPersistentDataContainer().set(clickAmountKey, PersistentDataType.INTEGER, amt);
                btn.setItemMeta(meta);
            }
            gui.setItem(depositSlots[i], btn);
        }

        int[] withdrawSlots = {11, 12, 13, 14};
        for (int i = 0; i < amounts.length; i++) {
            int amt = amounts[i];
            ItemStack btn = new ItemStack(Material.IRON_INGOT);
            ItemMeta meta = btn.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(parseColor("&c&l型番: 現金を引き出す (&e-" + amt + "円&c&l)"));
                List<String> lore = new ArrayList<>();
                lore.add(parseColor("&7クリックすると、銀行口座から手持ちへ引き出します。"));
                meta.setLore(lore);
                meta.getPersistentDataContainer().set(clickAmountKey, PersistentDataType.INTEGER, amt);
                btn.setItemMeta(meta);
            }
            gui.setItem(withdrawSlots[i], btn);
        }

        player.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String titleStr = plugin.getConfig().getString("bank.gui-title", "&1&l【ペリロッド銀行】預金・引出");
        if (!event.getView().getTitle().equals(parseColor(titleStr))) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getItemMeta() == null) return;

        Economy eco = plugin.getEconomy();
        if (eco == null) return;

        if (slot == 7) {
            plugin.getLoanManager().openLoanGUI(player);
            return;
        }
        if (slot == 8) {
            plugin.getLoginBonusManager().openBonusGUI(player);
            return;
        }

        ItemMeta meta = clickedItem.getItemMeta();
        if (!meta.getPersistentDataContainer().has(clickAmountKey, PersistentDataType.INTEGER)) return;
        int buttonAmount = meta.getPersistentDataContainer().get(clickAmountKey, PersistentDataType.INTEGER);

        UUID uuid = player.getUniqueId();
        int currentBankBalance = getBankBalance(uuid);
        int maxLimit = plugin.getConfig().getInt("bank.max-limit", 10000000);

        if (clickedItem.getType() == Material.EMERALD) {
            if (currentBankBalance >= maxLimit) {
                player.sendMessage(parseColor("&c[銀行] これ以上預金できません。口座の上限に達しています。"));
                return;
            }

            double playerMoney = eco.getBalance(player);
            if (playerMoney <= 0) {
                player.sendMessage(parseColor("&c[銀行] 手持ちの現金がありません。"));
                return;
            }

            int actualDeposit = Math.min(buttonAmount, (int) playerMoney);
            if (currentBankBalance + actualDeposit > maxLimit) {
                actualDeposit = maxLimit - currentBankBalance;
            }

            if (actualDeposit <= 0) return;

            eco.withdrawPlayer(player, actualDeposit);
            setBankBalance(uuid, currentBankBalance + actualDeposit);

            player.sendMessage(parseColor("&a[銀行] &e" + actualDeposit + "円 &fを預金しました。"));
            openBankGUI(player);
            return;
        }

        if (clickedItem.getType() == Material.IRON_INGOT) {
            if (currentBankBalance <= 0) {
                player.sendMessage(parseColor("&c[銀行] 銀行口座に預金がありません。"));
                return;
            }

            int actualWithdraw = Math.min(buttonAmount, currentBankBalance);

            setBankBalance(uuid, currentBankBalance - actualWithdraw);
            eco.depositPlayer(player, actualWithdraw);

            player.sendMessage(parseColor("&a[銀行] &e" + actualWithdraw + "円 &fを引き出しました。"));
            openBankGUI(player);
        }
    }
}
