package com.playershop.hologram;

import com.playershop.PlayerShopPlugin;
import com.playershop.data.Shop;
import com.playershop.util.ChestUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Manages shop holograms using invisible armor stands.
 *
 * DecentHolograms is listed as a soft-depend in plugin.yml. If it is ever
 * added as a compile-time dep (jar in libs/), swap the spawnLines() body for
 * a DHAPI.createHologram() call while keeping the same public API surface.
 */
public class HologramManager {

    private static final double LINE_GAP = 0.28;

    private final PlayerShopPlugin plugin;
    private boolean enabled;
    private final Map<UUID, List<ArmorStand>> holograms = new HashMap<>();

    public HologramManager(PlayerShopPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        enabled = plugin.getConfig().getBoolean("holograms.enabled", true);
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    public void createOrUpdate(Shop shop) {
        if (!enabled) return;
        delete(shop.getId());
        Location loc = ChestUtil.hologramLocation(shop.getChestLocation().getBlock());
        if (loc.getWorld() == null) return;
        spawnLines(shop.getId(), loc, buildLines(shop));
    }

    public void delete(UUID shopId) {
        List<ArmorStand> stands = holograms.remove(shopId);
        if (stands != null) stands.forEach(Entity::remove);
    }

    public void deleteAll() {
        holograms.values().forEach(list -> list.forEach(Entity::remove));
        holograms.clear();
    }

    // ── Line content ───────────────────────────────────────────────────────────

    private List<Component> buildLines(Shop shop) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.text(shop.getOwnerName() + "'s Shop", NamedTextColor.GOLD)
            .decoration(TextDecoration.ITALIC, false)
            .decoration(TextDecoration.BOLD, true));
        if (shop.getSellItem() != null) {
            String name = friendlyName(shop.getSellItem());
            lines.add(Component.text("Selling: ", NamedTextColor.GRAY)
                .append(Component.text(name, NamedTextColor.YELLOW))
                .decoration(TextDecoration.ITALIC, false));
            lines.add(Component.text("Price: ", NamedTextColor.GRAY)
                .append(Component.text("$" + String.format("%.2f", shop.getPrice()), NamedTextColor.GREEN))
                .decoration(TextDecoration.ITALIC, false));
            int stock = getStock(shop);
            NamedTextColor stockColor = stock > 0 ? NamedTextColor.GREEN : NamedTextColor.RED;
            lines.add(Component.text("Stock: ", NamedTextColor.GRAY)
                .append(Component.text(String.valueOf(stock), stockColor))
                .decoration(TextDecoration.ITALIC, false));
        } else {
            lines.add(Component.text("Not configured", NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, true));
        }
        lines.add(Component.text("Right-click to buy", NamedTextColor.AQUA)
            .decoration(TextDecoration.ITALIC, true));
        return lines;
    }

    // ── Armor stand spawning ───────────────────────────────────────────────────

    private void spawnLines(UUID shopId, Location base, List<Component> lines) {
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
        holograms.put(shopId, stands);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private int getStock(Shop shop) {
        if (shop.getSellItem() == null) return 0;
        org.bukkit.block.Block block = shop.getChestLocation().getBlock();
        if (!(block.getState() instanceof org.bukkit.block.Chest cs)) return 0;
        int count = 0;
        for (ItemStack s : cs.getInventory().getContents()) {
            if (s != null && s.isSimilar(shop.getSellItem())) count += s.getAmount();
        }
        return count;
    }

    private String friendlyName(ItemStack item) {
        return item.getType().name().replace('_', ' ');
    }
}
