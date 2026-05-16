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
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class ShopGUI implements Listener {

    // ── Layout constants ───────────────────────────────────────────────────────
    private static final int GUI_SIZE = 27;

    // Owner main GUI  (27-slot, row 1 center four slots)
    private static final int OWNER_ITEM_SLOT   = 10;
    private static final int OWNER_PRICE_SLOT  = 12;
    private static final int OWNER_STOCK_SLOT  = 14;
    private static final int OWNER_DELETE_SLOT = 16;

    // Buyer GUI
    private static final int BUYER_INFO_SLOT    = 4;
    private static final int BUYER_DISPLAY_SLOT = 13;

    // Price-edit GUI  (row 0 = adjustment strip, row 2 = controls)
    private static final int PE_MINUS_1000 = 0;
    private static final int PE_MINUS_100  = 1;
    private static final int PE_MINUS_10   = 2;
    private static final int PE_MINUS_1    = 3;
    private static final int PE_DISPLAY    = 4;
    private static final int PE_PLUS_1     = 5;
    private static final int PE_PLUS_10    = 6;
    private static final int PE_PLUS_100   = 7;
    private static final int PE_PLUS_1000  = 8;
    private static final int PE_RESET      = 20;
    private static final int PE_BACK       = 22;
    private static final int PE_CONFIRM    = 24;

    // ── Session types ──────────────────────────────────────────────────────────
    private sealed interface Session permits MainSession, PriceSession {}
    private record MainSession(Shop shop, Inventory inventory, boolean ownerMode) implements Session {}
    // PriceSession is a class (not record) so pending can be mutated without rebuilding
    private static final class PriceSession implements Session {
        final Shop shop;
        final Inventory inventory;
        double pending;
        PriceSession(Shop shop, Inventory inv, double initial) {
            this.shop = shop;
            this.inventory = inv;
            this.pending = initial;
        }
    }

    private final Map<UUID, Session> sessions = new HashMap<>();
    private final PlayerShopPlugin plugin;

    public ShopGUI(PlayerShopPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Public open ────────────────────────────────────────────────────────────

    public void open(Player player, Shop shop) {
        boolean ownerMode = shop.isOwner(player.getUniqueId())
            || player.hasPermission("playershop.admin");
        Inventory inv = ownerMode ? buildOwnerInventory(shop) : buildBuyerInventory(shop);
        // Register BEFORE openInventory so the closing previous GUI's event sees
        // a different inventory and cannot clobber this new session.
        sessions.put(player.getUniqueId(), new MainSession(shop, inv, ownerMode));
        player.openInventory(inv);
    }

    private void openPriceGUI(Player player, Shop shop) {
        Inventory inv = buildPriceInventory(shop.getPrice());
        PriceSession ps = new PriceSession(shop, inv, shop.getPrice());
        sessions.put(player.getUniqueId(), ps);
        player.openInventory(inv);
    }

    // ── Inventory builders ─────────────────────────────────────────────────────

    private Inventory buildOwnerInventory(Shop shop) {
        Inventory inv = Bukkit.createInventory(null, GUI_SIZE,
            Component.text("Shop Setup", NamedTextColor.DARK_GREEN));
        fill(inv, makeFiller());
        inv.setItem(OWNER_ITEM_SLOT,   makeItemSlot(shop));
        inv.setItem(OWNER_PRICE_SLOT,  makePriceButton(shop.getPrice()));
        inv.setItem(OWNER_STOCK_SLOT,  makeStockItem(shop));
        inv.setItem(OWNER_DELETE_SLOT, makeDeleteButton());
        return inv;
    }

    private Inventory buildBuyerInventory(Shop shop) {
        Inventory inv = Bukkit.createInventory(null, GUI_SIZE,
            Component.text("Player Shop", NamedTextColor.DARK_AQUA));
        fill(inv, makeFiller());
        inv.setItem(BUYER_INFO_SLOT,    makeBuyerInfo(shop));
        inv.setItem(BUYER_DISPLAY_SLOT, makeBuyerDisplay(shop));
        return inv;
    }

    private Inventory buildPriceInventory(double price) {
        Inventory inv = Bukkit.createInventory(null, GUI_SIZE,
            Component.text("Set Price", NamedTextColor.GOLD));
        fill(inv, makeFiller());
        inv.setItem(PE_MINUS_1000, makeDeltaButton(-1000));
        inv.setItem(PE_MINUS_100,  makeDeltaButton(-100));
        inv.setItem(PE_MINUS_10,   makeDeltaButton(-10));
        inv.setItem(PE_MINUS_1,    makeDeltaButton(-1));
        inv.setItem(PE_DISPLAY,    makePriceDisplay(price));
        inv.setItem(PE_PLUS_1,     makeDeltaButton(+1));
        inv.setItem(PE_PLUS_10,    makeDeltaButton(+10));
        inv.setItem(PE_PLUS_100,   makeDeltaButton(+100));
        inv.setItem(PE_PLUS_1000,  makeDeltaButton(+1000));
        inv.setItem(PE_RESET,      makeResetButton());
        inv.setItem(PE_BACK,       makeBackButton());
        inv.setItem(PE_CONFIRM,    makeConfirmButton());
        return inv;
    }

    private void refreshOwnerDisplay(Inventory inv, Shop shop) {
        inv.setItem(OWNER_ITEM_SLOT,  makeItemSlot(shop));
        inv.setItem(OWNER_PRICE_SLOT, makePriceButton(shop.getPrice()));
        inv.setItem(OWNER_STOCK_SLOT, makeStockItem(shop));
    }

    // ── Events ─────────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Session session = sessions.get(player.getUniqueId());
        if (session == null) return;
        if (!event.getInventory().equals(sessionInventory(session))) return;

        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
            return;
        }

        int raw = event.getRawSlot();
        if (raw >= GUI_SIZE && event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            event.setCancelled(true);
            return;
        }
        if (raw >= GUI_SIZE) return;

        event.setCancelled(true);
        switch (session) {
            case MainSession ms   -> { if (ms.ownerMode()) handleOwnerClick(player, ms, raw, event);
                                       else                handleBuyerClick(player, ms, raw); }
            case PriceSession ps  -> handlePriceClick(player, ps, raw);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Session session = sessions.get(player.getUniqueId());
        if (session == null) return;
        if (!event.getInventory().equals(sessionInventory(session))) return;
        for (int slot : event.getRawSlots()) {
            if (slot < GUI_SIZE) { event.setCancelled(true); return; }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();
        Session session = sessions.get(uuid);
        if (session == null) return;
        if (!event.getInventory().equals(sessionInventory(session))) return;

        sessions.remove(uuid);

        switch (session) {
            case MainSession ms -> {
                // Wipe display slots as a dupe safety net
                if (ms.ownerMode()) event.getInventory().setItem(OWNER_ITEM_SLOT, null);
                else                event.getInventory().setItem(BUYER_DISPLAY_SLOT, null);
            }
            case PriceSession ps -> {
                // Reopen owner GUI on the next tick regardless of how the price GUI was closed
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;
                    if (plugin.getShopManager().getShop(ps.shop.getId()).isEmpty()) return;
                    open(player, ps.shop);
                });
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
    }

    // ── Click handlers ─────────────────────────────────────────────────────────

    private void handleOwnerClick(Player player, MainSession session, int slot,
                                  InventoryClickEvent event) {
        Shop shop = session.shop();
        switch (slot) {
            case OWNER_ITEM_SLOT -> {
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
                        "Hold an item and click here to set it as the sell item.",
                        NamedTextColor.YELLOW));
                }
            }
            case OWNER_PRICE_SLOT  -> openPriceGUI(player, shop);
            case OWNER_STOCK_SLOT  -> {
                refreshOwnerDisplay(session.inventory(), shop);
                player.sendMessage(Component.text("Stock refreshed.", NamedTextColor.GRAY));
            }
            case OWNER_DELETE_SLOT -> {
                plugin.getShopManager().removeShop(shop.getId());
                plugin.getStorage().deleteShop(shop.getId());
                player.closeInventory();
                player.sendMessage(Component.text("Shop deleted.", NamedTextColor.RED));
            }
        }
    }

    private void handlePriceClick(Player player, PriceSession ps, int slot) {
        switch (slot) {
            case PE_MINUS_1000 -> applyDelta(ps, -1000);
            case PE_MINUS_100  -> applyDelta(ps, -100);
            case PE_MINUS_10   -> applyDelta(ps, -10);
            case PE_MINUS_1    -> applyDelta(ps, -1);
            case PE_PLUS_1     -> applyDelta(ps, +1);
            case PE_PLUS_10    -> applyDelta(ps, +10);
            case PE_PLUS_100   -> applyDelta(ps, +100);
            case PE_PLUS_1000  -> applyDelta(ps, +1000);
            case PE_RESET -> {
                ps.pending = 0;
                ps.inventory.setItem(PE_DISPLAY, makePriceDisplay(0));
            }
            case PE_BACK    -> player.closeInventory(); // onInventoryClose reopens owner GUI
            case PE_CONFIRM -> {
                ps.shop.setPrice(ps.pending);
                plugin.getStorage().saveShop(ps.shop);
                player.sendMessage(Component.text(
                    "Price set to $" + String.format("%.2f", ps.pending) + ".",
                    NamedTextColor.GREEN));
                player.closeInventory(); // onInventoryClose reopens owner GUI
            }
        }
    }

    private void applyDelta(PriceSession ps, double delta) {
        ps.pending = Math.max(0, ps.pending + delta);
        ps.inventory.setItem(PE_DISPLAY, makePriceDisplay(ps.pending));
    }

    private void handleBuyerClick(Player player, MainSession session, int slot) {
        if (slot != BUYER_DISPLAY_SLOT) return;
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

        // Atomic: withdraw → remove from chest → give → pay owner
        if (!plugin.getEconomy().withdraw(player.getUniqueId(), shop.getPrice())) {
            player.sendMessage(Component.text("Payment failed.", NamedTextColor.RED));
            return;
        }
        if (!removeOneFromChest(chestInv, sellItem)) {
            plugin.getEconomy().deposit(player.getUniqueId(), shop.getPrice());
            player.sendMessage(Component.text("Could not retrieve item from chest. Refunded.",
                NamedTextColor.RED));
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

    // ── Stock helpers ──────────────────────────────────────────────────────────

    private int countStock(Inventory inv, ItemStack template) {
        int count = 0;
        for (ItemStack stack : inv.getContents()) {
            if (stack != null && stack.isSimilar(template)) count += stack.getAmount();
        }
        return count;
    }

    private int getChestStock(Shop shop) {
        if (shop.getSellItem() == null) return 0;
        org.bukkit.block.Block block = shop.getChestLocation().getBlock();
        if (!(block.getState() instanceof org.bukkit.block.Chest cs)) return 0;
        return countStock(cs.getInventory(), shop.getSellItem());
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

    // ── Helpers ────────────────────────────────────────────────────────────────

    private Inventory sessionInventory(Session session) {
        return switch (session) {
            case MainSession ms  -> ms.inventory();
            case PriceSession ps -> ps.inventory;
        };
    }

    // ── Item factories ─────────────────────────────────────────────────────────

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

    // Owner GUI
    private ItemStack makeItemSlot(Shop shop) {
        if (shop.getSellItem() == null) {
            ItemStack item = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(colored("Set Sell Item", NamedTextColor.GREEN));
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
        lore.add(colored("Sell Item", NamedTextColor.GREEN).decoration(TextDecoration.BOLD, true));
        lore.add(plain("Hold a new item and click to change"));
        meta.lore(lore);
        display.setItemMeta(meta);
        return display;
    }

    private ItemStack makePriceButton(double price) {
        ItemStack item = new ItemStack(Material.GOLD_NUGGET);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(colored("Set Price", NamedTextColor.GOLD));
        meta.lore(List.of(
            plain("Current: ").append(colored("$" + String.format("%.2f", price), NamedTextColor.GREEN)),
            Component.empty(),
            plain("Click to adjust")
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeStockItem(Shop shop) {
        int stock = getChestStock(shop);
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(colored("Stock", NamedTextColor.AQUA));
        meta.lore(List.of(
            plain("In chest: ").append(colored(String.valueOf(stock), NamedTextColor.WHITE)),
            Component.empty(),
            plain("Click to refresh")
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeDeleteButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Delete Shop", NamedTextColor.RED)
            .decoration(TextDecoration.ITALIC, false)
            .decoration(TextDecoration.BOLD, true));
        meta.lore(List.of(plain("Click to permanently delete this shop")));
        item.setItemMeta(meta);
        return item;
    }

    // Price-edit GUI
    private ItemStack makePriceDisplay(double price) {
        Material mat = price > 0 ? Material.GOLD_BLOCK : Material.IRON_BLOCK;
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("$" + String.format("%.2f", price), NamedTextColor.GOLD)
            .decoration(TextDecoration.ITALIC, false)
            .decoration(TextDecoration.BOLD, true));
        meta.lore(List.of(plain("Use the buttons to adjust")));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeDeltaButton(double delta) {
        boolean pos = delta > 0;
        double abs = Math.abs(delta);
        Material mat = pos
            ? (abs >= 1000 ? Material.LIME_CONCRETE    : abs >= 100 ? Material.LIME_WOOL
             : abs >= 10   ? Material.LIME_TERRACOTTA   : Material.LIME_STAINED_GLASS_PANE)
            : (abs >= 1000 ? Material.RED_CONCRETE     : abs >= 100 ? Material.RED_WOOL
             : abs >= 10   ? Material.RED_TERRACOTTA    : Material.RED_STAINED_GLASS_PANE);
        String label = (pos ? "+" : "") + String.format("%.0f", delta);
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(label, pos ? NamedTextColor.GREEN : NamedTextColor.RED)
            .decoration(TextDecoration.ITALIC, false)
            .decoration(TextDecoration.BOLD, true));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeResetButton() {
        ItemStack item = new ItemStack(Material.ORANGE_CONCRETE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(colored("Reset", NamedTextColor.GOLD));
        meta.lore(List.of(plain("Set price back to $0.00")));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeBackButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(colored("Back", NamedTextColor.GRAY));
        meta.lore(List.of(plain("Discard changes")));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeConfirmButton() {
        ItemStack item = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(colored("Confirm", NamedTextColor.GREEN).decoration(TextDecoration.BOLD, true));
        meta.lore(List.of(plain("Save this price")));
        item.setItemMeta(meta);
        return item;
    }

    // Buyer GUI
    private ItemStack makeBuyerInfo(Shop shop) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(colored("Shop Info", NamedTextColor.AQUA).decoration(TextDecoration.BOLD, true));
        meta.lore(List.of(
            plain("Owner: ").append(colored(shop.getOwnerName(), NamedTextColor.YELLOW)),
            plain("Price: ").append(colored("$" + String.format("%.2f", shop.getPrice()), NamedTextColor.GREEN))
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeBuyerDisplay(Shop shop) {
        if (!shop.isConfigured()) {
            ItemStack item = new ItemStack(Material.RED_STAINED_GLASS_PANE);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(colored("Shop not configured", NamedTextColor.RED));
            item.setItemMeta(meta);
            return item;
        }
        ItemStack display = shop.getSellItem().clone();
        ItemMeta meta = display.hasItemMeta()
            ? display.getItemMeta()
            : Bukkit.getItemFactory().getItemMeta(display.getType());
        List<Component> lore = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        lore.add(Component.empty());
        lore.add(plain("Price: ").append(colored("$" + String.format("%.2f", shop.getPrice()),
            NamedTextColor.GREEN)));
        lore.add(colored("Click to purchase", NamedTextColor.YELLOW));
        meta.lore(lore);
        display.setItemMeta(meta);
        return display;
    }

    // ── Component helpers ──────────────────────────────────────────────────────

    private Component plain(String text) {
        return Component.text(text, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
    }

    private Component colored(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }
}
