package com.playershop.data;

import org.bukkit.inventory.ItemStack;
import java.util.UUID;

public final class Listing {
    private final UUID      id;
    private final ItemStack template; // amount always normalised to 1
    private double pricePerItem;
    private int    stock;             // virtual global stock, not tied to any physical chest

    public Listing(UUID id, ItemStack template, double pricePerItem, int stock) {
        this.id = id;
        ItemStack copy = template.clone();
        copy.setAmount(1);
        this.template     = copy;
        this.pricePerItem = pricePerItem;
        this.stock        = Math.max(0, stock);
    }

    public UUID      getId()           { return id; }
    public ItemStack getTemplate()     { return template.clone(); }
    public double    getPricePerItem() { return pricePerItem; }
    public void      setPricePerItem(double price) { this.pricePerItem = price; }
    public int       getStock()        { return stock; }
    public void      setStock(int s)   { this.stock = Math.max(0, s); }
    public void      addStock(int n)   { this.stock += n; }
}
