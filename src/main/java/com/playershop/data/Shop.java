package com.playershop.data;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class Shop {

    private final UUID id;
    private final UUID ownerUuid;
    private final String ownerName;
    private final Location chestLocation;
    private ItemStack sellItem;
    private double price;

    public Shop(UUID id, UUID ownerUuid, String ownerName, Location chestLocation,
                ItemStack sellItem, double price) {
        this.id = id;
        this.ownerUuid = ownerUuid;
        this.ownerName = ownerName;
        this.chestLocation = chestLocation;
        this.sellItem = sellItem;
        this.price = price;
    }

    public UUID getId()              { return id; }
    public UUID getOwnerUuid()       { return ownerUuid; }
    public String getOwnerName()     { return ownerName; }
    public Location getChestLocation() { return chestLocation; }
    public ItemStack getSellItem()   { return sellItem; }
    public void setSellItem(ItemStack item) { this.sellItem = item; }
    public double getPrice()         { return price; }
    public void setPrice(double price) { this.price = price; }

    public boolean isOwner(UUID uuid) { return ownerUuid.equals(uuid); }
    public boolean isConfigured()     { return sellItem != null && price > 0; }

    public String getWorldName() { return chestLocation.getWorld().getName(); }
    public int getX()            { return chestLocation.getBlockX(); }
    public int getY()            { return chestLocation.getBlockY(); }
    public int getZ()            { return chestLocation.getBlockZ(); }
}
