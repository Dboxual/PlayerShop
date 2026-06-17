package com.playershop;

import com.playershop.commands.ShopAdminCommand;
import com.playershop.data.PlayerShop;
import com.playershop.data.RemovalReason;
import com.playershop.data.ShopManager;
import com.playershop.data.ShopStorage;
import com.playershop.economy.EconomyManager;
import com.playershop.gui.ShopGUI;
import com.playershop.hologram.HologramManager;
import com.playershop.listeners.ShopInteractListener;
import com.playershop.listeners.ShopProtectListener;
import org.bukkit.Location;
import org.bukkit.block.data.type.Chest;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public class PlayerShopPlugin extends JavaPlugin {

    private ShopManager     shopManager;
    private ShopStorage     storage;
    private EconomyManager  economy;
    private ShopGUI         shopGUI;
    private HologramManager holograms;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        shopManager = new ShopManager();
        storage     = new ShopStorage(this);
        economy     = new EconomyManager();
        shopGUI     = new ShopGUI(this);
        holograms   = new HologramManager(this);

        if (!economy.setup()) {
            getLogger().warning("Vault/Economy not found — economy features unavailable until Vault is installed.");
        }

        storage.load();
        storage.loadAll().forEach(shopManager::addShop);
        getLogger().info("Loaded " + shopManager.getAllShops().size() + " player shop(s).");

        validateShops();

        holograms.reload();
        for (PlayerShop shop : shopManager.getAllShops()) {
            holograms.createOrUpdate(shop);
        }

        getServer().getPluginManager().registerEvents(shopGUI,                        this);
        getServer().getPluginManager().registerEvents(holograms,                       this);
        getServer().getPluginManager().registerEvents(new ShopInteractListener(this),  this);
        getServer().getPluginManager().registerEvents(new ShopProtectListener(this),   this);

        var cmd = getCommand("playershop");
        if (cmd != null) cmd.setExecutor(new ShopAdminCommand(this));
    }

    @Override
    public void onDisable() {
        holograms.deleteAll();
    }

    public void reload() {
        reloadConfig();
        holograms.deleteAll();
        shopManager.clear();
        storage.load();
        storage.loadAll().forEach(shopManager::addShop);
        validateShops();
        if (!economy.setup()) {
            getLogger().warning("Vault/Economy not found after reload.");
        }
        holograms.reload();
        for (PlayerShop shop : shopManager.getAllShops()) {
            holograms.createOrUpdate(shop);
        }
        getLogger().info("Reloaded " + shopManager.getAllShops().size() + " player shop(s).");
    }

    // ── Shop lifecycle ────────────────────────────────────────────────────────

    /**
     * Fully removes the shop whose chest index contains the given location.
     * Covers storage, hologram, manager cache, and any open GUI sessions.
     */
    public void removeShopAt(Location loc, RemovalReason reason) {
        shopManager.getPlayerShopByChest(loc).ifPresent(shop -> removeShopInternal(shop, reason));
    }

    private void removeShopInternal(PlayerShop shop, RemovalReason reason) {
        shopGUI.closeShopViewers(shop);
        holograms.delete(shop.getOwnerUuid());
        shopManager.removeShop(shop.getOwnerUuid());
        storage.deleteShop(shop.getOwnerUuid());
        getLogger().warning("[PlayerShop] Removed shop for '" + shop.getOwnerName()
                + "' (reason: " + reason + ")");
    }

    /**
     * Scans all loaded shops and removes any whose registered chest locations
     * no longer contain a valid chest block. Called on enable and reload to
     * eliminate ghost shops left over from before explosion protection was active.
     */
    private void validateShops() {
        List<PlayerShop> toRemove = new ArrayList<>();
        for (PlayerShop shop : shopManager.getAllShops()) {
            if (shop.getChests().isEmpty()) {
                toRemove.add(shop);
                continue;
            }
            for (Location loc : shop.getChests()) {
                if (loc.getWorld() == null || !(loc.getBlock().getBlockData() instanceof Chest)) {
                    toRemove.add(shop);
                    break;
                }
            }
        }
        for (PlayerShop shop : toRemove) {
            removeShopInternal(shop, RemovalReason.STARTUP_VALIDATION);
        }
        if (!toRemove.isEmpty()) {
            getLogger().warning("[PlayerShop] Startup validation removed "
                    + toRemove.size() + " ghost shop(s).");
        }
    }

    public ShopManager     getShopManager() { return shopManager; }
    public ShopStorage     getStorage()     { return storage; }
    public EconomyManager  getEconomy()     { return economy; }
    public ShopGUI         getShopGUI()     { return shopGUI; }
    public HologramManager getHolograms()   { return holograms; }
}
