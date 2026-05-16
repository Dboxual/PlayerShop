package com.playershop.data;

import com.playershop.PlayerShopPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

public class ShopStorage {

    private final PlayerShopPlugin plugin;
    private File dataFile;
    private YamlConfiguration data;

    public ShopStorage(PlayerShopPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        dataFile = new File(plugin.getDataFolder(), "shops.yml");
        if (!dataFile.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create shops.yml", e);
            }
        }
        data = YamlConfiguration.loadConfiguration(dataFile);
    }

    private void save() {
        try {
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save shops.yml", e);
        }
    }

    public Collection<Shop> loadAll() {
        List<Shop> shops = new ArrayList<>();
        if (!data.contains("shops")) return shops;

        for (String key : data.getConfigurationSection("shops").getKeys(false)) {
            String path = "shops." + key + ".";
            try {
                UUID id        = UUID.fromString(key);
                UUID ownerUuid = UUID.fromString(Objects.requireNonNull(data.getString(path + "owner-uuid")));
                String ownerName = data.getString(path + "owner-name", "Unknown");
                String worldName = data.getString(path + "world", "world");
                int x = data.getInt(path + "x");
                int y = data.getInt(path + "y");
                int z = data.getInt(path + "z");
                double price = data.getDouble(path + "price", 0);

                World world = Bukkit.getWorld(worldName);
                if (world == null) {
                    plugin.getLogger().warning("World '" + worldName + "' not found for shop " + id + ", skipping.");
                    continue;
                }

                Location loc       = new Location(world, x, y, z);
                ItemStack sellItem = data.getItemStack(path + "sell-item");
                shops.add(new Shop(id, ownerUuid, ownerName, loc, sellItem, price));
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load shop " + key + ": " + e.getMessage());
            }
        }
        return shops;
    }

    public void saveShop(Shop shop) {
        String path = "shops." + shop.getId() + ".";
        data.set(path + "owner-uuid", shop.getOwnerUuid().toString());
        data.set(path + "owner-name", shop.getOwnerName());
        data.set(path + "world", shop.getWorldName());
        data.set(path + "x", shop.getX());
        data.set(path + "y", shop.getY());
        data.set(path + "z", shop.getZ());
        data.set(path + "price", shop.getPrice());
        data.set(path + "sell-item", shop.getSellItem());
        save();
    }

    public void deleteShop(UUID id) {
        data.set("shops." + id, null);
        save();
    }
}
