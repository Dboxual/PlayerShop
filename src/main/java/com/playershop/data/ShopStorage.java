package com.playershop.data;

import com.playershop.PlayerShopPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.Base64;
import java.util.logging.Level;

public class ShopStorage {

    private final PlayerShopPlugin plugin;
    private File               dataFile;
    private YamlConfiguration  data;

    public ShopStorage(PlayerShopPlugin plugin) { this.plugin = plugin; }

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
        try { data.save(dataFile); }
        catch (IOException e) { plugin.getLogger().log(Level.SEVERE, "Could not save shops.yml", e); }
    }

    // ── Public entry point ────────────────────────────────────────────────────

    /**
     * Returns all PlayerShops from shops.yml.
     * If the old per-chest format (shops.*) is present and the new format
     * (player-shops.*) is absent, migrates automatically before loading.
     * The old section is never deleted — data loss is impossible on migration.
     */
    public Collection<PlayerShop> loadAll() {
        if (data.contains("shops") && !data.contains("player-shops")) {
            plugin.getLogger().info("[PlayerShop] Old v1 format detected — migrating to v2...");
            migrateV1();
        }
        return loadNewFormat();
    }

    // ── New format ────────────────────────────────────────────────────────────

    private Collection<PlayerShop> loadNewFormat() {
        List<PlayerShop> result = new ArrayList<>();
        ConfigurationSection root = data.getConfigurationSection("player-shops");
        if (root == null) return result;

        for (String ownerKey : root.getKeys(false)) {
            try {
                UUID ownerUuid = UUID.fromString(ownerKey);
                ConfigurationSection sec = root.getConfigurationSection(ownerKey);
                String ownerName = sec.getString("owner-name", "Unknown");
                List<Location> chests   = readChests(sec, ownerKey);
                List<Listing>  listings = readListings(sec);
                result.add(new PlayerShop(ownerUuid, ownerName, chests, listings));
            } catch (Exception e) {
                plugin.getLogger().warning("[PlayerShop] Failed to load player-shop "
                        + ownerKey + ": " + e.getMessage());
            }
        }
        return result;
    }

    private List<Location> readChests(ConfigurationSection sec, String ownerKey) {
        List<Location> out = new ArrayList<>();
        for (Map<?,?> entry : sec.getMapList("chests")) {
            try {
                String worldName = (String) entry.get("world");
                int x = ((Number) entry.get("x")).intValue();
                int y = ((Number) entry.get("y")).intValue();
                int z = ((Number) entry.get("z")).intValue();
                World world = Bukkit.getWorld(worldName);
                if (world == null) {
                    plugin.getLogger().warning("[PlayerShop] World '" + worldName
                            + "' missing for a chest of " + ownerKey + " — chest skipped.");
                    continue;
                }
                out.add(new Location(world, x, y, z));
            } catch (Exception e) {
                plugin.getLogger().warning("[PlayerShop] Malformed chest entry for "
                        + ownerKey + ": " + e.getMessage());
            }
        }
        return out;
    }

    private List<Listing> readListings(ConfigurationSection sec) {
        List<Listing> out = new ArrayList<>();
        ConfigurationSection listingsSec = sec.getConfigurationSection("listings");
        if (listingsSec == null) return out;
        for (String key : listingsSec.getKeys(false)) {
            try {
                UUID listingId = UUID.fromString(key);
                ConfigurationSection ls = listingsSec.getConfigurationSection(key);
                int stock = ls.getInt("stock", 0);

                // v1.0.24+: prefer binary serialization — preserves all item components,
                // custom NBT, PersistentDataContainer data, and plugin metadata exactly.
                ItemStack template = null;
                String itemData = ls.getString("sell-item-data");
                if (itemData != null) {
                    try {
                        template = ItemStack.deserializeBytes(Base64.getDecoder().decode(itemData));
                    } catch (Exception e) {
                        plugin.getLogger().warning("[PlayerShop] Failed to deserialize item bytes for listing "
                                + key + ", trying YAML fallback: " + e.getMessage());
                    }
                }
                // Pre-v1.0.24 fallback: Bukkit YAML format (may not preserve all custom data)
                if (template == null) template = ls.getItemStack("sell-item");
                if (template == null) continue;

                double pricePerItem;
                if (ls.contains("price-per-item")) {
                    pricePerItem = ls.getDouble("price-per-item");
                } else {
                    // Pre-v1.0.20: "price" was price per stack
                    pricePerItem = ls.getDouble("price", 0) / template.getMaxStackSize();
                }
                out.add(new Listing(listingId, template, pricePerItem, stock));
            } catch (Exception e) {
                plugin.getLogger().warning("[PlayerShop] Failed to load listing " + key + ": " + e.getMessage());
            }
        }
        return out;
    }

    // ── V1 migration ──────────────────────────────────────────────────────────

    /**
     * Reads every entry under shops.* (old per-chest format), groups them by
     * owner UUID, and writes the result under player-shops.*.
     *
     * Safety: new data is written and flushed BEFORE the method returns.
     * The old shops.* section is intentionally left intact so that nothing
     * is ever deleted.  If the server crashes mid-migration the next startup
     * will detect shops.* again and re-run the migration, producing the same
     * result.
     */
    private void migrateV1() {
        ConfigurationSection oldRoot = data.getConfigurationSection("shops");
        if (oldRoot == null) return;

        Map<UUID, String>         ownerNames    = new LinkedHashMap<>();
        Map<UUID, List<Location>> ownerChests   = new LinkedHashMap<>();
        Map<UUID, List<Listing>>  ownerListings = new LinkedHashMap<>();

        for (String key : oldRoot.getKeys(false)) {
            try {
                String path = "shops." + key + ".";
                UUID   ownerUuid = UUID.fromString(
                    Objects.requireNonNull(data.getString(path + "owner-uuid")));
                String ownerName = data.getString(path + "owner-name", "Unknown");
                String worldName = data.getString(path + "world", "world");
                int    x         = data.getInt(path + "x");
                int    y         = data.getInt(path + "y");
                int    z         = data.getInt(path + "z");
                double price     = data.getDouble(path + "price", 0);
                ItemStack sellItem = data.getItemStack(path + "sell-item");

                ownerNames.putIfAbsent(ownerUuid, ownerName);

                World world = Bukkit.getWorld(worldName);
                if (world != null) {
                    ownerChests.computeIfAbsent(ownerUuid, k -> new ArrayList<>())
                               .add(new Location(world, x, y, z));
                } else {
                    plugin.getLogger().warning("[PlayerShop] Migration: world '"
                            + worldName + "' missing for shop " + key + " — chest skipped.");
                }

                if (sellItem != null) {
                    double pricePerItem = price / sellItem.getMaxStackSize();
                    ownerListings.computeIfAbsent(ownerUuid, k -> new ArrayList<>())
                                 .add(new Listing(UUID.randomUUID(), sellItem, pricePerItem, 0));
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[PlayerShop] Migration: could not read old shop "
                        + key + ": " + e.getMessage());
            }
        }

        // Write new format — old shops.* section is preserved untouched
        for (UUID ownerUuid : ownerNames.keySet()) {
            String base = "player-shops." + ownerUuid + ".";
            data.set(base + "owner-name", ownerNames.get(ownerUuid));
            data.set(base + "chests",
                buildChestMaps(ownerChests.getOrDefault(ownerUuid, List.of())));
            for (Listing l : ownerListings.getOrDefault(ownerUuid, List.of())) {
                String lp = base + "listings." + l.getId() + ".";
                data.set(lp + "price-per-item", l.getPricePerItem());
                serializeItem(lp, l.getTemplate());
            }
        }

        save();
        plugin.getLogger().info("[PlayerShop] Migration complete — "
                + ownerNames.size() + " player shop(s) written. "
                + "Old 'shops' section preserved for safety.");
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    public void saveShop(PlayerShop shop) {
        String base = "player-shops." + shop.getOwnerUuid() + ".";
        data.set(base + "owner-name", shop.getOwnerName());
        data.set(base + "chests", buildChestMaps(shop.getChests()));
        // Clear and rewrite listings atomically within the section
        data.set(base + "listings", null);
        for (Listing l : shop.getListings()) {
            String lp = base + "listings." + l.getId() + ".";
            data.set(lp + "price-per-item", l.getPricePerItem());
            data.set(lp + "stock",          l.getStock());
            serializeItem(lp, l.getTemplate());
        }
        save();
    }

    public void deleteShop(UUID ownerUuid) {
        data.set("player-shops." + ownerUuid, null);
        save();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Writes an ItemStack to the YAML config under {@code basePath + "sell-item-data"}
     * using Paper's binary serialization (preserves all item components, custom NBT,
     * and PDC data exactly). Falls back to Bukkit YAML format if bytes serialization
     * fails for any reason.
     */
    private void serializeItem(String basePath, ItemStack item) {
        try {
            byte[] bytes = item.serializeAsBytes();
            data.set(basePath + "sell-item-data", Base64.getEncoder().encodeToString(bytes));
        } catch (Exception e) {
            plugin.getLogger().warning("[PlayerShop] Failed to serialize item as bytes at "
                    + basePath + ", using YAML fallback: " + e.getMessage());
            data.set(basePath + "sell-item", item);
        }
    }

    private List<Map<String, Object>> buildChestMaps(List<Location> locs) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Location loc : locs) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("world", loc.getWorld().getName());
            m.put("x",     loc.getBlockX());
            m.put("y",     loc.getBlockY());
            m.put("z",     loc.getBlockZ());
            list.add(m);
        }
        return list;
    }
}
