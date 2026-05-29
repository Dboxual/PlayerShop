package com.playershop.hologram;

import com.playershop.PlayerShopPlugin;
import com.playershop.data.PlayerShop;
import com.playershop.util.ChestUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

import java.util.*;

public class HologramManager implements Listener {

    private static final double LINE_GAP = 0.28;

    private final PlayerShopPlugin plugin;
    private boolean enabled;
    // Key: ownerUuid → all armor stands for all this player's chests
    private final Map<UUID, List<ArmorStand>> holograms = new HashMap<>();

    public HologramManager(PlayerShopPlugin plugin) { this.plugin = plugin; }

    public void reload() {
        enabled = plugin.getConfig().getBoolean("holograms.enabled", true);
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /** Destroys and recreates holograms above every loaded chest registered to this shop. */
    public void createOrUpdate(PlayerShop shop) {
        if (!enabled) return;
        delete(shop.getOwnerUuid());
        List<Component> lines     = buildLines(shop);
        List<ArmorStand> allStands = new ArrayList<>();
        for (Location chestLoc : shop.getChests()) {
            Location hologramLoc = ChestUtil.hologramLocation(chestLoc.getBlock());
            if (hologramLoc.getWorld() == null) continue;
            // Skip unloaded chunks — ChunkLoadEvent will trigger re-creation when they load
            if (!hologramLoc.getChunk().isLoaded()) continue;
            allStands.addAll(spawnLines(hologramLoc, lines));
        }
        if (!allStands.isEmpty()) holograms.put(shop.getOwnerUuid(), allStands);
    }

    public void delete(UUID ownerUuid) {
        List<ArmorStand> stands = holograms.remove(ownerUuid);
        if (stands != null) stands.forEach(Entity::remove);
    }

    public void deleteAll() {
        holograms.values().forEach(list -> list.forEach(Entity::remove));
        holograms.clear();
    }

    // ── Chunk load event ───────────────────────────────────────────────────────

    /**
     * When a chunk loads, respawn holograms for any shop that has a chest in that chunk.
     * This ensures holograms appear when players approach, not just on server start.
     */
    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!enabled) return;
        Chunk chunk = event.getChunk();
        for (PlayerShop shop : plugin.getShopManager().getAllShops()) {
            for (Location chestLoc : shop.getChests()) {
                if (chestLoc.getWorld() != null
                        && chestLoc.getWorld().equals(chunk.getWorld())
                        && (chestLoc.getBlockX() >> 4) == chunk.getX()
                        && (chestLoc.getBlockZ() >> 4) == chunk.getZ()) {
                    createOrUpdate(shop);
                    break; // one chest match per shop is enough to trigger a full update
                }
            }
        }
    }

    // ── Line content ───────────────────────────────────────────────────────────

    private List<Component> buildLines(PlayerShop shop) {
        return List.of(
            Component.text(shop.getOwnerName() + "'s Shop", NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true),
            Component.text("Right-click to manage", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, true)
        );
    }

    // ── Armor stand spawning ───────────────────────────────────────────────────

    private List<ArmorStand> spawnLines(Location base, List<Component> lines) {
        List<ArmorStand> stands = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            final Component text = lines.get(i);
            Location loc = base.clone().subtract(0, i * LINE_GAP, 0);
            ArmorStand stand = base.getWorld().spawn(loc, ArmorStand.class, as -> {
                as.setInvisible(true);
                as.setGravity(false);
                as.setSmall(true);
                as.setMarker(true);
                as.setCustomNameVisible(true);
                as.setCollidable(false);
                as.setPersistent(false);
            });
            stand.customName(text);
            stands.add(stand);
        }
        return stands;
    }
}
