package com.playershop.data;

import org.bukkit.Location;
import java.util.*;

public class ShopManager {

    // Primary store: one PlayerShop per owner UUID
    private final Map<UUID, PlayerShop> byOwner    = new HashMap<>();
    // Fast reverse lookup: chest location key → ownerUuid
    private final Map<String, UUID>     chestIndex = new HashMap<>();

    public void addShop(PlayerShop shop) {
        byOwner.put(shop.getOwnerUuid(), shop);
        for (Location loc : shop.getChests()) {
            chestIndex.put(locationKey(loc), shop.getOwnerUuid());
        }
    }

    /** Adds a chest location to the index without touching the PlayerShop object. */
    public void indexChest(PlayerShop shop, Location loc) {
        chestIndex.put(locationKey(loc), shop.getOwnerUuid());
    }

    /** Removes a chest location from the index without touching the PlayerShop object. */
    public void unindexChest(Location loc) {
        chestIndex.remove(locationKey(loc));
    }

    public void removeShop(UUID ownerUuid) {
        PlayerShop shop = byOwner.remove(ownerUuid);
        if (shop != null) shop.getChests().forEach(loc -> chestIndex.remove(locationKey(loc)));
    }

    public Optional<PlayerShop> getPlayerShop(UUID ownerUuid) {
        return Optional.ofNullable(byOwner.get(ownerUuid));
    }

    public Optional<PlayerShop> getPlayerShopByChest(Location loc) {
        UUID ownerUuid = chestIndex.get(locationKey(loc));
        return ownerUuid != null ? Optional.ofNullable(byOwner.get(ownerUuid)) : Optional.empty();
    }

    public Collection<PlayerShop> getAllShops() {
        return Collections.unmodifiableCollection(byOwner.values());
    }

    public void clear() {
        byOwner.clear();
        chestIndex.clear();
    }

    public static String locationKey(Location loc) {
        return loc.getWorld().getName() + "," + loc.getBlockX()
             + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }
}
