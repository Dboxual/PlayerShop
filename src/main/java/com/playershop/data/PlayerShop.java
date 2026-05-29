package com.playershop.data;

import org.bukkit.Location;
import java.util.*;

public final class PlayerShop {
    private final UUID         ownerUuid;
    private       String       ownerName;
    private final List<Location> chests   = new ArrayList<>();
    private final List<Listing>  listings = new ArrayList<>();

    public PlayerShop(UUID ownerUuid, String ownerName,
                      List<Location> chests, List<Listing> listings) {
        this.ownerUuid = ownerUuid;
        this.ownerName = ownerName;
        this.chests.addAll(chests);
        this.listings.addAll(listings);
    }

    public UUID   getOwnerUuid() { return ownerUuid; }
    public String getOwnerName() { return ownerName; }
    public void   setOwnerName(String name) { this.ownerName = name; }

    public List<Location> getChests()   { return Collections.unmodifiableList(chests); }
    public List<Listing>  getListings() { return Collections.unmodifiableList(listings); }

    public void addChest(Location loc) {
        chests.add(loc);
    }

    public void removeChest(Location loc) {
        chests.removeIf(l ->
            l.getWorld().equals(loc.getWorld()) &&
            l.getBlockX() == loc.getBlockX() &&
            l.getBlockY() == loc.getBlockY() &&
            l.getBlockZ() == loc.getBlockZ());
    }

    public void addListing(Listing listing)   { listings.add(listing); }
    public void removeListing(UUID listingId) { listings.removeIf(l -> l.getId().equals(listingId)); }

    public boolean isOwner(UUID uuid) { return ownerUuid.equals(uuid); }
}
