package com.playershop.economy;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.UUID;

public class EconomyManager {

    private Economy economy;

    public boolean setup() {
        economy = null;
        RegisteredServiceProvider<Economy> rsp =
            Bukkit.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        economy = rsp.getProvider();
        return economy != null;
    }

    public boolean isAvailable() { return economy != null; }

    public boolean hasBalance(UUID uuid, double amount) {
        if (economy == null) return false;
        return economy.has(Bukkit.getOfflinePlayer(uuid), amount);
    }

    public boolean withdraw(UUID uuid, double amount) {
        if (economy == null) return false;
        return economy.withdrawPlayer(Bukkit.getOfflinePlayer(uuid), amount).transactionSuccess();
    }

    public void deposit(UUID uuid, double amount) {
        if (economy == null) return;
        economy.depositPlayer(Bukkit.getOfflinePlayer(uuid), amount);
    }

    public double getBalance(UUID uuid) {
        if (economy == null) return 0;
        return economy.getBalance(Bukkit.getOfflinePlayer(uuid));
    }
}
