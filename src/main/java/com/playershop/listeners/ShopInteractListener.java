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
        if (event.getHand() != EquipmentSlot.HAND) return; // prevent double-fire
        Block block = event.getClickedBlock();
        if (block == null || !(block.getBlockData() instanceof Chest)) return;

        Player player = event.getPlayer();
        Location primary = ChestUtil.primaryLocation(block);
        Optional<Shop> shopOpt = plugin.getShopManager().getShopAt(primary);
        boolean shovelShift = player.isSneaking() && isShovel(event.getItem());

        debug(player, "chest@" + primary.getBlockX() + "," + primary.getBlockY() + "," + primary.getBlockZ()
            + " shop=" + shopOpt.isPresent() + " shovelShift=" + shovelShift);

        if (shovelShift) {
            event.setCancelled(true);
            if (shopOpt.isPresent()) {
                Shop shop = shopOpt.get();
                if (shop.isOwner(player.getUniqueId()) || player.hasPermission("playershop.admin")) {
                    plugin.getShopGUI().enterSetupMode(player, shop);
                } else {
                    player.sendMessage(Component.text("This chest belongs to another player's shop.",
                        NamedTextColor.RED));
                }
            } else {
                handleCreate(player, primary);
            }
        } else if (shopOpt.isPresent()) {
            Shop shop = shopOpt.get();
            if (shop.isOwner(player.getUniqueId()) || player.hasPermission("playershop.admin")) {
                // Owner/admin → vanilla chest opens normally for restocking
            } else {
                // Non-owner → buyer GUI (only if configured)
                event.setCancelled(true);
                if (!shop.isConfigured()) {
                    player.sendMessage(Component.text("This shop hasn't been set up yet.", NamedTextColor.GRAY));
                } else {
                    plugin.getShopGUI().open(player, shop);
                }
            }
        }
        // No shop, no shovel-shift → vanilla chest behaviour
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

    private boolean isShovel(ItemStack item) {
        return item != null && SHOVELS.contains(item.getType());
    }

    private void debug(Player player, String msg) {
        if (!plugin.getConfig().getBoolean("settings.debug", false)) return;
        plugin.getLogger().info("[Debug] " + player.getName() + " | " + msg);
    }
}
