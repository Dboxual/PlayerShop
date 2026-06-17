package com.playershop.listeners;

import com.playershop.PlayerShopPlugin;
import com.playershop.data.PlayerShop;
import com.playershop.data.RemovalReason;
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

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class ShopInteractListener implements Listener {

    private static final Set<Material> AXES = Set.of(
        Material.WOODEN_AXE, Material.STONE_AXE,  Material.IRON_AXE,
        Material.GOLDEN_AXE, Material.DIAMOND_AXE, Material.NETHERITE_AXE
    );

    private final PlayerShopPlugin plugin;

    public ShopInteractListener(PlayerShopPlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Block block = event.getClickedBlock();
        if (block == null) return;
        if (!(block.getBlockData() instanceof Chest)) return;

        Player   player  = event.getPlayer();
        Location primary = ChestUtil.primaryLocation(block);
        Optional<PlayerShop> shopOpt = plugin.getShopManager().getPlayerShopByChest(primary);
        boolean  axeShift = player.isSneaking() && isAxe(event.getItem());

        if (axeShift) {
            event.setCancelled(true);
            if (shopOpt.isPresent()) {
                PlayerShop shop = shopOpt.get();
                if (isGhostShop(shop)) {
                    plugin.removeShopAt(primary, RemovalReason.GHOST_DETECTED);
                    return;
                }
                if (shop.isOwner(player.getUniqueId()) || player.hasPermission("playershop.admin")) {
                    plugin.getShopGUI().openOwnerGUI(player, shop);
                } else {
                    player.sendMessage(Component.text(
                        "This chest belongs to another player's shop.", NamedTextColor.RED));
                }
            } else {
                handleCreate(player, primary);
            }
        } else if (shopOpt.isPresent()) {
            PlayerShop shop = shopOpt.get();
            if (isGhostShop(shop)) {
                plugin.removeShopAt(primary, RemovalReason.GHOST_DETECTED);
                return;
            }
            event.setCancelled(true);
            if (shop.isOwner(player.getUniqueId()) || player.hasPermission("playershop.admin")) {
                plugin.getShopGUI().openOwnerGUI(player, shop);
            } else {
                plugin.getShopGUI().openBuyerGUI(player, shop);
            }
        }
        // No shop, no axe-shift → vanilla chest behaviour (no cancel)
    }

    private void handleCreate(Player player, Location primary) {
        // Reject if the target chest is already part of a double chest
        if (primary.getBlock().getBlockData() instanceof Chest chestData
                && chestData.getType() != Chest.Type.SINGLE) {
            player.sendMessage(Component.text(
                "PlayerShop chests must be single chests.", NamedTextColor.RED));
            return;
        }

        Optional<PlayerShop> existing = plugin.getShopManager().getPlayerShop(player.getUniqueId());

        if (existing.isPresent()) {
            // Player already has a shop — link this chest to it
            PlayerShop shop     = existing.get();
            int        maxChests = plugin.getConfig().getInt("settings.max-shops-per-player", 3);
            if (!player.hasPermission("playershop.admin") && shop.getChests().size() >= maxChests) {
                player.sendMessage(Component.text(
                    "You have reached the shop chest limit (" + maxChests + ").", NamedTextColor.RED));
                return;
            }
            shop.addChest(primary);
            plugin.getShopManager().indexChest(shop, primary);
            plugin.getStorage().saveShop(shop);
            plugin.getHolograms().createOrUpdate(shop);
            plugin.getShopGUI().openOwnerGUI(player, shop);
            player.sendMessage(Component.text(
                "Shop chest linked! All your chests share the same shop.", NamedTextColor.GREEN));
        } else {
            // First chest — create the player's PlayerShop
            PlayerShop shop = new PlayerShop(
                player.getUniqueId(), player.getName(), List.of(primary), List.of());
            plugin.getShopManager().addShop(shop);
            plugin.getStorage().saveShop(shop);
            plugin.getHolograms().createOrUpdate(shop);
            plugin.getShopGUI().openOwnerGUI(player, shop);
            player.sendMessage(Component.text(
                "Shop created! Use the menu to set it up.", NamedTextColor.GREEN));
        }
    }

    /**
     * Returns true if any of the shop's registered chest locations no longer
     * holds a valid chest block — indicating stale data from a destroyed chest.
     * The entire shop is treated as a ghost even if only one location is invalid,
     * because a partially-destroyed shop cannot be used reliably.
     */
    private boolean isGhostShop(PlayerShop shop) {
        for (Location loc : shop.getChests()) {
            if (loc.getWorld() == null) return true;
            if (!(loc.getBlock().getBlockData() instanceof Chest)) return true;
        }
        return false;
    }

    private boolean isAxe(ItemStack item) {
        return item != null && AXES.contains(item.getType());
    }
}
