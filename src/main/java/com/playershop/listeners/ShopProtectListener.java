package com.playershop.listeners;

import com.playershop.PlayerShopPlugin;
import com.playershop.data.Shop;
import com.playershop.util.ChestUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Optional;

public class ShopProtectListener implements Listener {

    private final PlayerShopPlugin plugin;

    public ShopProtectListener(PlayerShopPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!isShopBlock(block)) return;

        Player player = event.getPlayer();
        Location primary = ChestUtil.primaryLocation(block);
        Optional<Shop> shopOpt = plugin.getShopManager().getShopAt(primary);
        if (shopOpt.isEmpty()) return;
        Shop shop = shopOpt.get();

        if (player.hasPermission("playershop.admin") || shop.isOwner(player.getUniqueId())) {
            plugin.getShopManager().removeShop(shop.getId());
            plugin.getStorage().deleteShop(shop.getId());
            player.sendMessage(Component.text("Shop removed.", NamedTextColor.YELLOW));
            // Let the break proceed normally
        } else {
            event.setCancelled(true);
            player.sendMessage(Component.text("You cannot break another player's shop chest.",
                NamedTextColor.RED));
        }
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(this::isShopBlock);
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(this::isShopBlock);
    }

    @EventHandler
    public void onBlockBurn(BlockBurnEvent event) {
        if (isShopBlock(event.getBlock())) event.setCancelled(true);
    }

    @EventHandler
    public void onBlockIgnite(BlockIgniteEvent event) {
        if (isShopBlock(event.getBlock())) event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        if (isShopInventory(event.getSource()) || isShopInventory(event.getDestination())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block b : event.getBlocks()) {
            if (isShopBlock(b)) { event.setCancelled(true); return; }
        }
    }

    @EventHandler
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block b : event.getBlocks()) {
            if (isShopBlock(b)) { event.setCancelled(true); return; }
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private boolean isShopBlock(Block block) {
        if (!(block.getBlockData() instanceof Chest)) return false;
        return plugin.getShopManager().getShopAt(ChestUtil.primaryLocation(block)).isPresent();
    }

    private boolean isShopInventory(Inventory inv) {
        InventoryHolder holder = inv.getHolder();
        if (holder instanceof org.bukkit.block.Chest single) {
            return isShopBlock(single.getBlock());
        }
        if (holder instanceof org.bukkit.block.DoubleChest dc) {
            // getLeftSide() returns InventoryHolder; check both via location fallback
            InventoryHolder left  = dc.getLeftSide();
            InventoryHolder right = dc.getRightSide();
            if (left  instanceof org.bukkit.block.Chest lc && isShopBlock(lc.getBlock())) return true;
            if (right instanceof org.bukkit.block.Chest rc && isShopBlock(rc.getBlock())) return true;
            // Fallback: use the double-chest inventory's reported location
            Location loc = inv.getLocation();
            if (loc != null) {
                Block b = loc.getBlock();
                if (b.getBlockData() instanceof Chest) return isShopBlock(b);
            }
        }
        return false;
    }
}
