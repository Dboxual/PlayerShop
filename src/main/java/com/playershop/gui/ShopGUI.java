package com.playershop.gui;

import com.playershop.PlayerShopPlugin;
import com.playershop.data.Shop;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class ShopGUI implements Listener {

    private static final int GUI_SIZE    = 27;
    private static final int INFO_SLOT   = 4;
    private static final int DISPLAY_SLOT = 13;
    private static final int ACTION_SLOT  = 22;

    private final PlayerShopPlugin plugin;
    private final Map<UUID, ShopSession> sessions = new HashMap<>();

    private record ShopSession(Shop shop, Inventory inventory, boolean ownerMode) {}

    public ShopGUI(PlayerShopPlugin plugin) {
        this.plugin = plugin;
    }

    // ─── Open ─────────────────────────────────────────────────────────────────

    public void open(Player player, Shop shop) {
        boolean ownerMode = shop.isOwner(player.getUniqueId())
            || player.hasPermission("playershop.admin");
        Inventory inv = ownerMode ? buildOwnerInventory(shop) : buildBuyerInventory(shop);
        // Register session BEFORE openInventory so the close-event of the previous
        // GUI sees a different inventory object and cannot clobber the new session.
        sessions.put(player.getUniqueId(), new ShopSession(shop, inv, ownerMode));
        player.openInventory(inv);
    }

    // ─── Inventory builders ───────────────────────────────────────────────────

    private Inventory buildOwnerInventory(Shop shop) {
        Inventory inv = Bukkit.createInventory(null, GUI_SIZE,
            Component.text("Shop Setup", NamedTextColor.DARK_GREEN));
        fill(inv, makeFiller());
        inv.setItem(INFO_SLOT,    makeOwnerInfo(shop));
        inv.setItem(DISPLAY_SLOT, makeOwnerDisplay(shop));
        inv.setItem(ACTION_SLOT,  makeRemoveButton());
        return inv;
    }

    private Inventory buildBuyerInventory(Shop shop) {
        Inventory inv = Bukkit.createInventory(null, GUI_SIZE,
            Component.text("Player Shop", NamedTextColor.DARK_AQUA));
        fill(inv, makeFiller());
        inv.setItem(INFO_SLOT,    makeBuyerInfo(shop));
        inv.setItem(DISPLAY_SLOT, makeBuyerDisplay(shop));
        return inv;
    }

    private void refreshOwnerDisplay(Inventory inv, Shop shop) {
        inv.setItem(INFO_SLOT,    makeOwnerInfo(shop));
        inv.setItem(DISPLAY_SLOT, makeOwnerDisplay(shop));
    }

    // ─── Event handlers ───────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ShopSession session = sessions.get(player.getUniqueId());
        if (session == null) return;
        if (!event.getInventory().equals(session.inventory())) return;

        // Block cursor-collect globally — it can pull the display item off any slot
        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
            return;
        }

        int raw = event.getRawSlot();

        // Block shift-click out of bottom inventory into the GUI
        if (raw >= GUI_SIZE && event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            event.setCancelled(true);
            return;
        }

        if (raw >= GUI_SIZE) return; // bottom inventory — allow normally

        event.setCancelled(true); // cancel ALL top-inventory interactions by default

        if (session.ownerMode()) handleOwnerClick(player, session, raw, event);
        else                     handleBuyerClick(player, session, raw);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ShopSession session = sessions.get(player.getUniqueId());
        if (session == null) return;
        if (!event.getInventory().equals(session.inventory())) return;
        for (int slot : event.getRawSlots()) {
            if (slot < GUI_SIZE) { event.setCancelled(true); return; }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();
        ShopSession session = sessions.get(uuid);
        if (session == null) return;
        if (!event.getInventory().equals(session.inventory())) return;
        sessions.remove(uuid);
        // Wipe display slot — safety net against any edge-case item leakage
        event.getInventory().setItem(DISPLAY_SLOT, null);
    }

    // ─── Click handlers ───────────────────────────────────────────────────────

    private void handleOwnerClick(Player player, ShopSession session, int slot,
                                  InventoryClickEvent event) {
        Shop shop = session.shop();
        if (slot == DISPLAY_SLOT) {
            // Read cursor BEFORE cancel (already cancelled above; cursor is untouched)
            ItemStack cursor = event.getCursor();
            if (cursor != null && cursor.getType() != Material.AIR) {
                shop.setSellItem(cursor.clone());
                plugin.getStorage().saveShop(shop);
                refreshOwnerDisplay(session.inventory(), shop);
                player.sendMessage(Component.text("Sell item set to ", NamedTextColor.GREEN)
                    .append(Component.text(cursor.getType().name(), NamedTextColor.YELLOW))
                    .append(Component.text(".", NamedTextColor.GREEN)));
            } else {
                player.sendMessage(Component.text(
                    "Hold an item in your cursor and click here to set it as the sell item.",
                    NamedTextColor.YELLOW));
            }
        } else if (slot == ACTION_SLOT) {
            plugin.getShopManager().removeShop(shop.getId());
            plugin.getStorage().deleteShop(shop.getId());
            player.closeInventory();
            player.sendMessage(Component.text("Shop removed.", NamedTextColor.RED));
        } else if (slot == INFO_SLOT) {
            player.sendMessage(Component.text(
                "Look at your chest and use /playershop setprice <amount> to set the price.",
                NamedTextColor.YELLOW));
        }
    }

    private void handleBuyerClick(Player player, ShopSession session, int slot) {
        if (slot != DISPLAY_SLOT) return;
        Shop shop = session.shop();

        if (!shop.isConfigured()) {
            player.sendMessage(Component.text("This shop is not configured yet.", NamedTextColor.RED));
            return;
        }
        if (!plugin.getEconomy().isAvailable()) {
            player.sendMessage(Component.text("Economy is unavailable on this server.", NamedTextColor.RED));
            return;
        }
        if (!plugin.getEconomy().hasBalance(player.getUniqueId(), shop.getPrice())) {
            player.sendMessage(Component.text(
                "You need $" + String.format("%.2f", shop.getPrice()) + " to buy this.",
                NamedTextColor.RED));
            return;
        }

        org.bukkit.block.Block block = shop.getChestLocation().getBlock();
        if (!(block.getState() instanceof org.bukkit.block.Chest chestState)) {
            player.sendMessage(Component.text("Shop chest not found.", NamedTextColor.RED));
            return;
        }
        Inventory chestInv = chestState.getInventory();
        ItemStack sellItem = shop.getSellItem();

        if (countStock(chestInv, sellItem) < 1) {
            player.sendMessage(Component.text("This shop is out of stock.", NamedTextColor.RED));
            return;
        }
        if (!hasInventorySpace(player, sellItem)) {
            player.sendMessage(Component.text("Your inventory is full.", NamedTextColor.RED));
            return;
        }

        // Atomic: withdraw → remove from chest → give to buyer → pay owner
        if (!plugin.getEconomy().withdraw(player.getUniqueId(), shop.getPrice())) {
            player.sendMessage(Component.text("Payment failed.", NamedTextColor.RED));
            return;
        }
        if (!removeOneFromChest(chestInv, sellItem)) {
            plugin.getEconomy().deposit(player.getUniqueId(), shop.getPrice()); // refund
            player.sendMessage(Component.text("Could not retrieve item from chest. Refunded.", NamedTextColor.RED));
            return;
        }

        ItemStack toGive = sellItem.clone();
        toGive.setAmount(1);
        player.getInventory().addItem(toGive);
        plugin.getEconomy().deposit(shop.getOwnerUuid(), shop.getPrice());

        player.closeInventory();
        player.sendMessage(Component.text("Purchased ", NamedTextColor.GREEN)
            .append(Component.text(sellItem.getType().name(), NamedTextColor.YELLOW))
            .append(Component.text(" for $" + String.format("%.2f", shop.getPrice()) + ".",
                NamedTextColor.GREEN)));
    }

    // ─── Stock helpers ────────────────────────────────────────────────────────

    private int countStock(Inventory inv, ItemStack template) {
        int count = 0;
        for (ItemStack stack : inv.getContents()) {
            if (stack != null && stack.isSimilar(template)) count += stack.getAmount();
        }
        return count;
    }

    private boolean removeOneFromChest(Inventory inv, ItemStack template) {
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack != null && stack.isSimilar(template)) {
                if (stack.getAmount() > 1) {
                    stack.setAmount(stack.getAmount() - 1);
                    inv.setItem(i, stack);
                } else {
                    inv.setItem(i, null);
                }
                return true;
            }
        }
        return false;
    }

    private boolean hasInventorySpace(Player player, ItemStack template) {
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack == null || stack.getType() == Material.AIR) return true;
            if (stack.isSimilar(template) && stack.getAmount() < stack.getMaxStackSize()) return true;
        }
        return false;
    }

    // ─── Item factories ───────────────────────────────────────────────────────

    private void fill(Inventory inv, ItemStack filler) {
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);
    }

    private ItemStack makeFiller() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeOwnerInfo(Shop shop) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Shop Settings", NamedTextColor.GREEN)
            .decoration(TextDecoration.ITALIC, false)
            .decoration(TextDecoration.BOLD, true));
        List<Component> lore = new ArrayList<>();
        lore.add(plain("Price: ").append(colored("$" + String.format("%.2f", shop.getPrice()), NamedTextColor.GREEN)));
        lore.add(plain("Item:  ").append(shop.getSellItem() != null
            ? colored(shop.getSellItem().getType().name(), NamedTextColor.YELLOW)
            : colored("Not set", NamedTextColor.RED)));
        lore.add(Component.empty());
        lore.add(plain("Click center slot to set sell item"));
        lore.add(plain("/playershop setprice <amount> to set price"));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeOwnerDisplay(Shop shop) {
        if (shop.getSellItem() == null) {
            ItemStack item = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text("Click to set sell item", NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(plain("Hold an item and click to set it")));
            item.setItemMeta(meta);
            return item;
        }
        ItemStack display = shop.getSellItem().clone();
        ItemMeta meta = display.hasItemMeta()
            ? display.getItemMeta()
            : Bukkit.getItemFactory().getItemMeta(display.getType());
        List<Component> lore = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        lore.add(Component.empty());
        lore.add(plain("Click to change item"));
        meta.lore(lore);
        display.setItemMeta(meta);
        return display;
    }

    private ItemStack makeRemoveButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Remove Shop", NamedTextColor.RED)
            .decoration(TextDecoration.ITALIC, false)
            .decoration(TextDecoration.BOLD, true));
        meta.lore(List.of(plain("Click to permanently delete this shop")));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeBuyerInfo(Shop shop) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Shop Info", NamedTextColor.AQUA)
            .decoration(TextDecoration.ITALIC, false)
            .decoration(TextDecoration.BOLD, true));
        List<Component> lore = new ArrayList<>();
        lore.add(plain("Owner: ").append(colored(shop.getOwnerName(), NamedTextColor.YELLOW)));
        lore.add(plain("Price: ").append(colored("$" + String.format("%.2f", shop.getPrice()), NamedTextColor.GREEN)));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeBuyerDisplay(Shop shop) {
        if (!shop.isConfigured()) {
            ItemStack item = new ItemStack(Material.RED_STAINED_GLASS_PANE);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text("Shop not configured", NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
            item.setItemMeta(meta);
            return item;
        }
        ItemStack display = shop.getSellItem().clone();
        ItemMeta meta = display.hasItemMeta()
            ? display.getItemMeta()
            : Bukkit.getItemFactory().getItemMeta(display.getType());
        List<Component> lore = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        lore.add(Component.empty());
        lore.add(plain("Price: ").append(colored("$" + String.format("%.2f", shop.getPrice()), NamedTextColor.GREEN)));
        lore.add(colored("Click to purchase", NamedTextColor.YELLOW));
        meta.lore(lore);
        display.setItemMeta(meta);
        return display;
    }

    // ─── Component helpers ────────────────────────────────────────────────────

    private Component plain(String text) {
        return Component.text(text, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
    }

    private Component colored(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }
}
