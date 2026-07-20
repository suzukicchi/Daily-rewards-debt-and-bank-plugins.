package com.example.loginbonus;

import com.example.loginbonus.command.MainCommand;
import com.example.loginbonus.loginbonus.LoginBonusManager;
import com.example.loginbonus.loan.LoanManager;
import com.example.loginbonus.bank.BankManager;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class MainPlugin extends JavaPlugin {

    private Economy econ = null;
    private LoginBonusManager loginBonusManager;
    private LoanManager loanManager;
    private BankManager bankManager;

    private FileConfiguration messagesConfig = null;
    private File messagesFile = null;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveDefaultMessagesConfig();

        if (!setupEconomy()) {
            getLogger().severe("Vaultまたは対応する経済プラグインが見つかりません。プラグインを無効化します。");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.loanManager = new LoanManager(this);
        this.loginBonusManager = new LoginBonusManager(this);
        this.bankManager = new BankManager(this);

        getServer().getPluginManager().registerEvents(loginBonusManager, this);
        getServer().getPluginManager().registerEvents(loanManager, this);
        getServer().getPluginManager().registerEvents(bankManager, this);

        MainCommand mainCommand = new MainCommand(this);
        Objects.requireNonNull(getCommand("loginbonus")).setExecutor(mainCommand);
        com.example.loginbonus.command.BankCommand bankCommand = new com.example.loginbonus.command.BankCommand(this);
        Objects.requireNonNull(getCommand("bank")).setExecutor(bankCommand);

        getLogger().info("LoginBonus, LoanSystem & BankSystem プラグインが有効化されました。");
    }

    @Override
    public void onDisable() {
        getLogger().info("LoginBonus, LoanSystem & BankSystem プラグインが無効化されました。");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        econ = rsp.getProvider();
        return econ != null;
    }

    @Override
    public void reloadConfig() {
        super.reloadConfig();
        reloadMessagesConfig(); 
        if (this.bankManager != null) {
            this.bankManager.loadBankData();
        }
    }

    public FileConfiguration getMessagesConfig() {
        if (this.messagesConfig == null) {
            reloadMessagesConfig();
        }
        return this.messagesConfig;
    }

    public void reloadMessagesConfig() {
        if (this.messagesFile == null) {
            this.messagesFile = new File(getDataFolder(), "messages.yml");
        }
        this.messagesConfig = YamlConfiguration.loadConfiguration(this.messagesFile);

        InputStream defaultStream = getResource("messages.yml");
        if (defaultStream != null) {
            YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultStream, StandardCharsets.UTF_8)
            );
            this.messagesConfig.setDefaults(defConfig);
        }
    }

    public void saveDefaultMessagesConfig() {
        if (this.messagesFile == null) {
            this.messagesFile = new File(getDataFolder(), "messages.yml");
        }
        if (!this.messagesFile.exists()) {
            saveResource("messages.yml", false);
        }
    }

    public String getMessage(String path, String prefixPath) {
        String prefix = getMessagesConfig().getString(prefixPath, "");
        String message = getMessagesConfig().getString(path, "");
        return prefix + message;
    }

    public Economy getEconomy() {
        return econ;
    }

    public LoginBonusManager getLoginBonusManager() {
        return loginBonusManager;
    }

    public LoanManager getLoanManager() {
        return loanManager;
    }

    public BankManager getBankManager() {
        return bankManager;
    }
}
