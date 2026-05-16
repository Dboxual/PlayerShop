package com.playershop.data;

import org.bukkit.Location;

import java.util.*;

public class ShopManager {

    private final Map<UUID, Shop> shopsById      = new HashMap<>();
    private final Map<String, UUID> locationIndex = new HashMap<>();

    public void addShop(Shop shop) {
        shopsById.put(shop.getId(), shop);
        locationIndex.put(locationKey(shop.getChestLocation()), shop.getId());
    }

    public void removeShop(UUID id) {
        Shop shop = shopsById.remove(id);
        if (shop != null) locationIndex.remove(locationKey(shop.getChestLocation()));
    }

    public Optional<Shop> getShopAt(Location loc) {
        UUID id = locationIndex.get(locationKey(loc));
        return id != null ? Optional.ofNullable(shopsById.get(id)) : Optional.empty();
    }

    public Optional<Shop> getShop(UUID id) {
        return Optional.ofNullable(shopsById.get(id));
    }

    public Collection<Shop> getAllShops() {
        return Collections.unmodifiableCollection(shopsById.values());
    }

    public void clear() {
        shopsById.clear();
        locationIndex.clear();
    }

    public static String locationKey(Location loc) {
        return loc.getWorld().getName() + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }
}
