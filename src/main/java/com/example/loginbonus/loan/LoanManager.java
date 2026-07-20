package com.example.loginbonus.loan;

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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class LoanManager implements Listener {

    private final MainPlugin plugin;
    private final NamespacedKey loanAmountKey;
    private final NamespacedKey loanStartDateKey;
    private final NamespacedKey clickAmountKey;

    public LoanManager(MainPlugin plugin) {
        this.plugin = plugin;
        this.loanAmountKey = new NamespacedKey(plugin, "loan_amount");
        this.loanStartDateKey = new NamespacedKey(plugin, "loan_start_date");
        this.clickAmountKey = new NamespacedKey(plugin, "loan_click_amount");
    }

    private String parseColor(String text) {
        if (text == null) return "";
        return text.replace("&", "§");
    }

    public long getLoanStartDate(Player player) {
        Long time = player.getPersistentDataContainer().get(loanStartDateKey, PersistentDataType.LONG);
        return time != null ? time : 0;
    }

    public int calculateInterestAndGetAmount(Player player) {
        int currentLoan = player.getPersistentDataContainer().getOrDefault(loanAmountKey, PersistentDataType.INTEGER, 0);
        if (currentLoan <= 0) return 0;

        long startDate = getLoanStartDate(player);
        if (startDate == 0) return currentLoan;

        long now = System.currentTimeMillis();
        long elapsedMs = now - startDate;
        long elapsedDays = elapsedMs / (24 * 60 * 60 * 1000);

        if (elapsedDays > 0) {
            double rate = plugin.getConfig().getDouble("loan.interest-rate", 0.05);
            double newAmount = currentLoan * Math.pow(1 + rate, elapsedDays);
            int finalAmount = (int) Math.round(newAmount);

            if (finalAmount != currentLoan) {
                player.getPersistentDataContainer().set(loanAmountKey, PersistentDataType.INTEGER, finalAmount);
                player.getPersistentDataContainer().set(loanStartDateKey, PersistentDataType.LONG, now);
                return finalAmount;
            }
        }
        return currentLoan;
    }

    public boolean checkAndEnforceSeizure(Player player) {
        int currentLoan = calculateInterestAndGetAmount(player);
        if (currentLoan <= 0) return false;

        int maxAllowedBeforeBlock = plugin.getConfig().getInt("loan.block-bonus-threshold", 50000);
        if (currentLoan >= maxAllowedBeforeBlock) return true;

        long startDate = getLoanStartDate(player);
        long now = System.currentTimeMillis();
        long elapsedMs = now - startDate;
        long elapsedDays = elapsedMs / (24 * 60 * 60 * 1000);
        int dueDays = plugin.getConfig().getInt("loan.due-days", 7);

        return elapsedDays >= dueDays;
    }

    public void openLoanGUI(Player player) {
        int currentLoan = calculateInterestAndGetAmount(player);

        String titleStr = plugin.getConfig().getString("loan.gui-title", "&4&l【ローン窓口】借入・返済");
        Inventory gui = Bukkit.createInventory(player, 18, parseColor(titleStr));

        int maxLoan = plugin.getConfig().getInt("loan.max-limit", 100000);
        int dueDays = plugin.getConfig().getInt("loan.due-days", 7);
        double interestPercent = plugin.getConfig().getDouble("loan.interest-rate", 0.05) * 100;

        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta infoMeta = info.getItemMeta();
        if (infoMeta != null) {
            infoMeta.setDisplayName(parseColor("&e&l【ご利用状況】"));
            List<String> lore = new ArrayList<>();
            lore.add(parseColor("&7・現在の借入総額: &c" + currentLoan + "円"));
            lore.add(parseColor("&7・限度額: &e" + maxLoan + "円"));
            lore.add(parseColor("&7・利息: &e24時間ごとに " + interestPercent + "% 加算"));
            
            if (currentLoan > 0) {
                long startDate = getLoanStartDate(player);
                long dueDateMs = startDate + ((long) dueDays * 24 * 60 * 60 * 1000);
                String formattedDueDate = new SimpleDateFormat("yyyy/MM/dd HH:mm").format(new Date(dueDateMs));
                lore.add(parseColor("&7・全額返済期限: &b" + formattedDueDate + " まで"));
            } else {
                lore.add(parseColor("&7・全額返済期限: &a借入はありません"));
            }

            if (checkAndEnforceSeizure(player)) {
                lore.add(parseColor("&c⚠ 警告: 借入過多、または返済期限超過により制限中！"));
            }
            infoMeta.setLore(lore);
            info.setItemMeta(infoMeta);
        }
        gui.setItem(0, info);

        ItemStack bankJump = new ItemStack(Material.EMERALD);
        ItemMeta bankJumpMeta = bankJump.getItemMeta();
        if (bankJumpMeta != null) {
            bankJumpMeta.setDisplayName(parseColor("&1&l🏦 銀行画面へ"));
            bankJump.setItemMeta(bankJumpMeta);
        }
        gui.setItem(7, bankJump);

        ItemStack dailyJump = new ItemStack(Material.CHEST_MINECART);
        ItemMeta jumpMeta = dailyJump.getItemMeta();
        if (jumpMeta != null) {
            jumpMeta.setDisplayName(parseColor("&d&l📆 デイリー報酬画面へ"));
            dailyJump.setItemMeta(jumpMeta);
        }
        gui.setItem(8, dailyJump);

        int[] borrowAmounts = {100, 1000, 10000, 50000};
        int[] borrowSlots = {2, 3, 4, 5};

        for (int i = 0; i < borrowAmounts.length; i++) {
            int amount = borrowAmounts[i];
            ItemStack borrowBtn = new ItemStack(Material.REDSTONE_TORCH);
            ItemMeta meta = borrowBtn.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(parseColor("&a&l💰 現金を借り入れる (&e+" + amount + "円&a&l)"));
                List<String> lore = new ArrayList<>();
                lore.add(parseColor("&7クリックすると、即座に口座へ現金が振り込まれます。"));
                lore.add(parseColor("&c※返済期限は借りた当日から " + dueDays + "日間 です。"));
                meta.setLore(lore);
                meta.getPersistentDataContainer().set(clickAmountKey, PersistentDataType.INTEGER, amount);
                borrowBtn.setItemMeta(meta);
            }
            gui.setItem(borrowSlots[i], borrowBtn);
        }

        int[] repayAmounts = {100, 1000, 10000, 50000};
        int[] repaySlots = {11, 12, 13, 14};

        for (int i = 0; i < repayAmounts.length; i++) {
            int amount = repayAmounts[i];
            ItemStack repayBtn = new ItemStack(Material.GOLD_INGOT);
            ItemMeta meta = repayBtn.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(parseColor("&d&l💳 返済する (&e-" + amount + "円&d&l)"));
                List<String> lore = new ArrayList<>();
                lore.add(parseColor("&7クリックすると、手持ちから最大 &e" + amount + "円 &7返済します。"));
                lore.add(parseColor("&a※借金がボタンの額面未満の場合は、その残額だけを引き落とします。"));
                meta.setLore(lore);
                meta.getPersistentDataContainer().set(clickAmountKey, PersistentDataType.INTEGER, amount);
                repayBtn.setItemMeta(meta);
            }
            gui.setItem(repaySlots[i], repayBtn);
        }

        player.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String titleStr = plugin.getConfig().getString("loan.gui-title", "&4&l【ローン窓口】借入・返済");
        if (!event.getView().getTitle().equals(parseColor(titleStr))) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getItemMeta() == null) return;

        Economy eco = plugin.getEconomy();
        if (eco == null) return;

        int currentLoan = calculateInterestAndGetAmount(player);
        int maxLoan = plugin.getConfig().getInt("loan.max-limit", 100000);

        if (slot == 7) {
            plugin.getBankManager().openBankGUI(player);
            return;
        }
        if (slot == 8) {
            plugin.getLoginBonusManager().openBonusGUI(player);
            return;
        }

        ItemMeta meta = clickedItem.getItemMeta();
        if (!meta.getPersistentDataContainer().has(clickAmountKey, PersistentDataType.INTEGER)) return;
        int buttonAmount = meta.getPersistentDataContainer().get(clickAmountKey, PersistentDataType.INTEGER);

        if (clickedItem.getType() == Material.REDSTONE_TORCH) {
            if (currentLoan + buttonAmount > maxLoan) {
                String overLimitMsg = plugin.getMessage("loan.over-limit", "prefix.loan")
                                            .replace("%limit%", String.valueOf(maxLoan));
                player.sendMessage(parseColor(overLimitMsg));
                return;
            }

            if (currentLoan == 0) {
                player.getPersistentDataContainer().set(loanStartDateKey, PersistentDataType.LONG, System.currentTimeMillis());
            }

            player.getPersistentDataContainer().set(loanAmountKey, PersistentDataType.INTEGER, currentLoan + buttonAmount);
            eco.depositPlayer(player, buttonAmount);

            String borrowMsg = plugin.getMessage("loan.borrow-success", "prefix.loan")
                                     .replace("%amount%", String.valueOf(buttonAmount))
                                     .replace("%total%", String.valueOf(currentLoan + buttonAmount));
            player.sendMessage(parseColor(borrowMsg));
            openLoanGUI(player);
            return;
        }

        if (clickedItem.getType() == Material.GOLD_INGOT) {
            if (currentLoan <= 0) {
                String noDebtMsg = plugin.getMessage("loan.no-debt", "prefix.loan");
                player.sendMessage(parseColor(noDebtMsg));
                return;
            }

            int actualRepayAmount = Math.min(currentLoan, buttonAmount);

            if (eco.getBalance(player) < actualRepayAmount) {
                String insufficientMsg = plugin.getMessage("loan.insufficient-funds", "prefix.loan")
                                               .replace("%amount%", String.valueOf(actualRepayAmount));
                player.sendMessage(parseColor(insufficientMsg));
                return;
            }

            eco.withdrawPlayer(player, actualRepayAmount);
            int nextLoanAmount = currentLoan - actualRepayAmount;

            if (nextLoanAmount <= 0) {
                player.getPersistentDataContainer().remove(loanAmountKey);
                player.getPersistentDataContainer().remove(loanStartDateKey);
                nextLoanAmount = 0;
            } else {
                player.getPersistentDataContainer().set(loanAmountKey, PersistentDataType.INTEGER, nextLoanAmount);
            }

            String repayMsg = plugin.getMessage("loan.repay-success", "prefix.loan")
                                    .replace("%amount%", String.valueOf(actualRepayAmount))
                                    .replace("%total%", String.valueOf(nextLoanAmount));
            player.sendMessage(parseColor(repayMsg));
            openLoanGUI(player);
        }
    }
}
