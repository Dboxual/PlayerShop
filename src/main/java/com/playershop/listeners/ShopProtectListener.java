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
import org.bukkit.block.BlockFace;
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

import java.util.List;
import java.util.Optional;

public class ShopProtectListener implements Listener {

    private static final BlockFace[] HORIZONTAL_FACES =
        { BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST };

    private final PlayerShopPlugin plugin;

    public ShopProtectListener(PlayerShopPlugin plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block placed = event.getBlockPlaced();
        if (placed.getType() != Material.CHEST) return;

        // Ghost shop detection: if a chest is placed at a location that still has a
        // stale shop record (the original chest was destroyed), purge the ghost data
        // so the new chest doesn't inherit the dead shop.
        Location loc = placed.getLocation();
        if (plugin.getShopManager().getPlayerShopByChest(loc).isPresent()) {
            Material previous = event.getBlockReplacedState().getType();
            if (previous != Material.CHEST) {
                // Ghost record — original chest is gone, new chest being placed here
                plugin.removeShopAt(loc, RemovalReason.GHOST_DETECTED);
            }
        }

        // Prevent turning a shop chest into a double chest
        for (BlockFace face : HORIZONTAL_FACES) {
            Block neighbor = placed.getRelative(face);
            if (neighbor.getType() != Material.CHEST) continue;
            Location neighborPrimary = ChestUtil.primaryLocation(neighbor);
            if (plugin.getShopManager().getPlayerShopByChest(neighborPrimary).isPresent()) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(Component.text(
                    "You cannot turn a PlayerShop into a double chest.", NamedTextColor.RED));
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!isShopBlock(event.getBlock())) return;
        Player player = event.getPlayer();
        Location primary = ChestUtil.primaryLocation(event.getBlock());
        Optional<PlayerShop> shopOpt = plugin.getShopManager().getPlayerShopByChest(primary);
        if (shopOpt.isEmpty()) { event.setCancelled(true); return; } // safety
        PlayerShop shop = shopOpt.get();

        if (shop.isOwner(player.getUniqueId())) {
            // Allow the break — close active viewers, remove chest from data, keep all listings/stock
            plugin.getShopGUI().closeShopViewers(shop);
            shop.removeChest(primary);
            plugin.getShopManager().unindexChest(primary);
            plugin.getStorage().saveShop(shop);
            plugin.getHolograms().createOrUpdate(shop);
            if (shop.getChests().isEmpty()) {
                player.sendMessage(Component.text(
                    "Shop chest removed. Your shop data has been preserved.", NamedTextColor.GREEN));
            } else {
                player.sendMessage(Component.text("Shop chest removed.", NamedTextColor.GREEN));
            }
            // event NOT cancelled — chest breaks and drops normally
        } else {
            event.setCancelled(true);
            if (player.hasPermission("playershop.admin")) {
                player.sendMessage(Component.text(
                    "Use the Remove button in your shop menu to remove this shop chest.", NamedTextColor.RED));
            } else {
                player.sendMessage(Component.text(
                    "You cannot break another player's shop.", NamedTextColor.RED));
            }
        }
    }

    // HIGH priority so we run after most plugins but still have the final say on
    // which blocks survive. We collect blocks we tried to protect and schedule a
    // next-tick fallback to detect any that somehow still got destroyed.
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityExplode(EntityExplodeEvent event) {
        List<Block> shopBlocks = event.blockList().stream()
                .filter(this::isShopBlock)
                .toList();
        event.blockList().removeAll(shopBlocks);
        scheduleIntegrityCheck(shopBlocks);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockExplode(BlockExplodeEvent event) {
        List<Block> shopBlocks = event.blockList().stream()
                .filter(this::isShopBlock)
                .toList();
        event.blockList().removeAll(shopBlocks);
        scheduleIntegrityCheck(shopBlocks);
    }

    /**
     * Schedules a next-tick check for blocks we tried to protect from an explosion.
     * If any are no longer a chest block despite our protection attempt, the shop is
     * fully removed so stale data doesn't linger.
     */
    private void scheduleIntegrityCheck(List<Block> protectedBlocks) {
        if (protectedBlocks.isEmpty()) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (Block b : protectedBlocks) {
                if (!(b.getBlockData() instanceof Chest)) {
                    plugin.removeShopAt(b.getLocation(), RemovalReason.EXPLOSION);
                }
            }
        });
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
        if (isShopInventory(event.getSource()) || isShopInventory(event.getDestination()))
            event.setCancelled(true);
    }

    @EventHandler
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block b : event.getBlocks())
            if (isShopBlock(b)) { event.setCancelled(true); return; }
    }

    @EventHandler
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block b : event.getBlocks())
            if (isShopBlock(b)) { event.setCancelled(true); return; }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isShopBlock(Block block) {
        if (!(block.getBlockData() instanceof Chest)) return false;
        Location primary = ChestUtil.primaryLocation(block);
        return plugin.getShopManager().getPlayerShopByChest(primary).isPresent();
    }

    private boolean isShopInventory(Inventory inv) {
        InventoryHolder holder = inv.getHolder();
        if (holder instanceof org.bukkit.block.Chest single)
            return isShopBlock(single.getBlock());
        if (holder instanceof org.bukkit.block.DoubleChest dc) {
            InventoryHolder left  = dc.getLeftSide();
            InventoryHolder right = dc.getRightSide();
            if (left  instanceof org.bukkit.block.Chest lc && isShopBlock(lc.getBlock())) return true;
            if (right instanceof org.bukkit.block.Chest rc && isShopBlock(rc.getBlock())) return true;
            Location loc = inv.getLocation();
            if (loc != null && loc.getBlock().getBlockData() instanceof Chest)
                return isShopBlock(loc.getBlock());
        }
        return false;
    }
}
