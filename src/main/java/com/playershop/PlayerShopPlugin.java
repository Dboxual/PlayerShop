package com.playershop;

import com.playershop.commands.ShopAdminCommand;
import com.playershop.data.ShopManager;
import com.playershop.data.ShopStorage;
import com.playershop.economy.EconomyManager;
import com.playershop.gui.ShopGUI;
import com.playershop.listeners.ShopInteractListener;
import com.playershop.listeners.ShopProtectListener;
import org.bukkit.plugin.java.JavaPlugin;

public class PlayerShopPlugin extends JavaPlugin {

    private ShopManager  shopManager;
    private ShopStorage  storage;
    private EconomyManager economy;
    private ShopGUI      shopGUI;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        shopManager = new ShopManager();
        storage     = new ShopStorage(this);
        economy     = new EconomyManager();
        shopGUI     = new ShopGUI(this);

        if (!economy.setup()) {
            getLogger().warning("Vault/Economy not found — shops will not function until Vault is installed.");
        }

        storage.load();
        storage.loadAll().forEach(shopManager::addShop);
        getLogger().info("Loaded " + shopManager.getAllShops().size() + " shop(s).");

        getServer().getPluginManager().registerEvents(shopGUI,                       this);
        getServer().getPluginManager().registerEvents(new ShopInteractListener(this), this);
        getServer().getPluginManager().registerEvents(new ShopProtectListener(this),  this);

        var cmd = getCommand("playershop");
        if (cmd != null) cmd.setExecutor(new ShopAdminCommand(this));
    }

    @Override
    public void onDisable() {
        // Data is saved on every mutation — nothing extra needed.
    }

    public void reload() {
        reloadConfig();
        shopManager.clear();
        storage.load();
        storage.loadAll().forEach(shopManager::addShop);
        if (!economy.setup()) {
            getLogger().warning("Vault/Economy not found after reload.");
        }
        getLogger().info("Reloaded " + shopManager.getAllShops().size() + " shop(s).");
    }

    public ShopManager   getShopManager() { return shopManager; }
    public ShopStorage   getStorage()     { return storage; }
    public EconomyManager getEconomy()    { return economy; }
    public ShopGUI       getShopGUI()     { return shopGUI; }
}
