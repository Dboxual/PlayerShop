package com.playershop.listeners;

import com.playershop.PlayerShopPlugin;
import com.playershop.data.Shop;
import com.playershop.util.ChestUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class ShopInteractListener implements Listener {

    private static final Set<Material> SHOVELS = Set.of(
        Material.WOODEN_SHOVEL, Material.STONE_SHOVEL, Material.IRON_SHOVEL,
        Material.GOLDEN_SHOVEL, Material.DIAMOND_SHOVEL, Material.NETHERITE_SHOVEL
    );

    private final PlayerShopPlugin plugin;

    public ShopInteractListener(PlayerShopPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        boolean debug = plugin.getConfig().getBoolean("settings.debug", false);

        // Guard: only process chest blocks. Signs, Pinpoint objects, lodestones,
        // anvils, and all other non-chest blocks are ignored entirely — we never
        // cancel the event for them.
        if (!isEligibleBlock(block)) {
            if (debug) {
                plugin.getLogger().info("[PlayerShop] Skipping interaction: block="
                        + block.getType() + " at " + fmtLoc(block.getLocation())
                        + " is not a chest — no action taken, event untouched.");
            }
            return;
        }

        Player player = event.getPlayer();
        Location primary = ChestUtil.primaryLocation(block);
        Optional<Shop> shopOpt = plugin.getShopManager().getShopAt(primary);
        boolean shovelShift = player.isSneaking() && isShovel(event.getItem());

        if (debug) {
            plugin.getLogger().info("[PlayerShop] Chest click: block=" + block.getType()
                    + " at " + fmtLoc(block.getLocation())
                    + " primaryLoc=" + fmtLoc(primary)
                    + " shop=" + shopOpt.isPresent()
                    + " shovelShift=" + shovelShift
                    + " eventCancelled=" + event.isCancelled());
        }

        if (shovelShift) {
            // Shift+shovel on any chest → setup/create shop. Cancel to prevent chest open.
            event.setCancelled(true);
            if (shopOpt.isPresent()) {
                Shop shop = shopOpt.get();
                if (shop.isOwner(player.getUniqueId()) || player.hasPermission("playershop.admin")) {
                    if (debug) plugin.getLogger().info("[PlayerShop] Opening setup mode for " + player.getName() + " on existing shop.");
                    plugin.getShopGUI().enterSetupMode(player, shop);
                } else {
                    player.sendMessage(Component.text("This chest belongs to another player's shop.",
                        NamedTextColor.RED));
                }
            } else {
                if (debug) plugin.getLogger().info("[PlayerShop] Creating new shop for " + player.getName() + " at " + fmtLoc(primary) + ".");
                handleCreate(player, primary);
            }
        } else if (shopOpt.isPresent()) {
            Shop shop = shopOpt.get();
            if (shop.isOwner(player.getUniqueId()) || player.hasPermission("playershop.admin")) {
                // Owner/admin → vanilla chest opens normally for restocking
                if (debug) plugin.getLogger().info("[PlayerShop] Owner/admin opening own shop chest — vanilla behavior.");
            } else {
                // Non-owner → buyer GUI (only if configured)
                event.setCancelled(true);
                if (!shop.isConfigured()) {
                    if (debug) plugin.getLogger().info("[PlayerShop] Shop not configured; informing " + player.getName() + ".");
                    player.sendMessage(Component.text("This shop hasn't been set up yet.", NamedTextColor.GRAY));
                } else {
                    if (debug) plugin.getLogger().info("[PlayerShop] Opening buyer GUI for " + player.getName() + ".");
                    plugin.getShopGUI().open(player, shop);
                }
            }
        }
        // No shop, no shovel-shift → vanilla chest behaviour (no cancel)
    }

    private void handleCreate(Player player, Location primary) {
        int maxShops = plugin.getConfig().getInt("settings.max-shops-per-player", 3);
        if (!player.hasPermission("playershop.admin")) {
            long owned = plugin.getShopManager().getAllShops().stream()
                .filter(s -> s.isOwner(player.getUniqueId()))
                .count();
            if (owned >= maxShops) {
                player.sendMessage(Component.text(
                    "You have reached the shop limit (" + maxShops + ").", NamedTextColor.RED));
                return;
            }
        }
        Shop shop = new Shop(UUID.randomUUID(), player.getUniqueId(), player.getName(),
            primary, null, 0);
        plugin.getShopManager().addShop(shop);
        plugin.getStorage().saveShop(shop);
        plugin.getHolograms().createOrUpdate(shop);
        plugin.getShopGUI().enterSetupMode(player, shop);
        player.sendMessage(Component.text("Shop created! Add the items you want to sell, then close the chest.", NamedTextColor.GREEN));
    }

    // Only CHEST and TRAPPED_CHEST blocks are eligible shop blocks.
    // Barrel, Sign, and all other blocks are not — we must not cancel their events.
    private boolean isEligibleBlock(Block block) {
        return block.getBlockData() instanceof Chest;
    }

    private boolean isShovel(ItemStack item) {
        return item != null && SHOVELS.contains(item.getType());
    }

    private String fmtLoc(Location loc) {
        return loc.getWorld().getName() + " " + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }
}
