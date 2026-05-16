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

    // ── Session types ──────────────────────────────────────────────────────────
    private sealed interface Session
        permits OwnerSession, ItemSelectorSession, PriceSession,
                BuyerStockSession, BuyAmountSession {}

    /** Owner setup GUI (set item, price, stock, delete). */
    private record OwnerSession(Shop shop, Inventory inventory) implements Session {}

    /** Shows chest contents so owner can pick the sell item. */
    private record ItemSelectorSession(Shop shop, Inventory inventory) implements Session {}

    /** Price-edit GUI (delta buttons). */
    private static final class PriceSession implements Session {
        final Shop shop; final Inventory inventory; double pending;
        PriceSession(Shop shop, Inventory inv, double init) {
            this.shop = shop; this.inventory = inv; this.pending = init;
        }
    }

    /** Buyer first screen — shows what the shop sells. */
    private record BuyerStockSession(Shop shop, Inventory inventory) implements Session {}

    /** Buyer selects how many to buy. */
    private static final class BuyAmountSession implements Session {
        final Shop shop; final Inventory inventory;
        int amount; boolean purchased;
        BuyAmountSession(Shop shop, Inventory inv, int initial) {
            this.shop = shop; this.inventory = inv; this.amount = initial;
        }
    }

    // ── Owner GUI slots (27-slot) ──────────────────────────────────────────────
    private static final int OWN_ITEM   = 10;
    private static final int OWN_PRICE  = 12;
    private static final int OWN_STOCK  = 14;
    private static final int OWN_DELETE = 16;

    // ── Price-edit GUI slots (27-slot) ─────────────────────────────────────────
    private static final int PE_M1000 = 0, PE_M100 = 1, PE_M10 = 2, PE_M1 = 3;
    private static final int PE_DISP  = 4;
    private static final int PE_P1    = 5, PE_P10  = 6, PE_P100 = 7, PE_P1000 = 8;
    private static final int PE_RESET = 20, PE_BACK = 22, PE_CONFIRM = 24;

    // ── Buyer stock GUI slots (27-slot) ────────────────────────────────────────
    private static final int BS_INFO = 4, BS_ITEM = 13;

    // ── Buy-amount GUI slots (27-slot, sparse — no glass) ─────────────────────
    // Buy buttons: row 0, slots 0-5
    private static final int BA_BUY1 = 0, BA_BUY8 = 1, BA_BUY16 = 2,
                              BA_BUY32 = 3, BA_BUY64 = 4, BA_BUYMAX = 5;
    private static final int BA_DISP    = 13;
    private static final int BA_BACK    = 18, BA_CONFIRM = 26;

    private static final int GUI_SIZE = 27;

    private final PlayerShopPlugin plugin;
    private final Map<UUID, Session> sessions = new HashMap<>();

    public ShopGUI(PlayerShopPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Entry points ───────────────────────────────────────────────────────────

    public void open(Player player, Shop shop) {
        boolean ownerMode = shop.isOwner(player.getUniqueId())
            || player.hasPermission("playershop.admin");
        if (ownerMode) openOwner(player, shop);
        else           openBuyerStock(player, shop);
    }

    private void openOwner(Player player, Shop shop) {
        Inventory inv = buildOwnerInventory(shop);
        sessions.put(player.getUniqueId(), new OwnerSession(shop, inv));
        player.openInventory(inv);
    }

    private void openItemSelector(Player player, Shop shop) {
        org.bukkit.block.Block block = shop.getChestLocation().getBlock();
        if (!(block.getState() instanceof org.bukkit.block.Chest cs)) {
            player.sendMessage(Component.text("Chest not found.", NamedTextColor.RED));
            return;
        }
        Inventory chestInv = cs.getInventory();
        int size = chestInv.getSize(); // 27 single, 54 double

        boolean hasItems = false;
        for (ItemStack s : chestInv.getContents()) {
            if (s != null && s.getType() != Material.AIR) { hasItems = true; break; }
        }

        Inventory selectorInv = Bukkit.createInventory(null, size,
            Component.text("Select Sell Item", NamedTextColor.DARK_GREEN));

        if (hasItems) {
            for (int i = 0; i < size; i++) {
                ItemStack src = chestInv.getItem(i);
                if (src != null) selectorInv.setItem(i, src.clone());
            }
        } else {
            ItemStack placeholder = new ItemStack(Material.BARRIER);
            ItemMeta m = placeholder.getItemMeta();
            m.displayName(Component.text("Chest is empty!", NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
            m.lore(List.of(Component.text("Add items to the chest first, then come back.",
                NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
            placeholder.setItemMeta(m);
            selectorInv.setItem(size / 2 - 1, placeholder); // rough center
        }

        sessions.put(player.getUniqueId(), new ItemSelectorSession(shop, selectorInv));
        player.openInventory(selectorInv);
    }

    private void openPriceGUI(Player player, Shop shop) {
        Inventory inv = buildPriceInventory(shop.getPrice());
        PriceSession ps = new PriceSession(shop, inv, shop.getPrice());
        sessions.put(player.getUniqueId(), ps);
        player.openInventory(inv);
    }

    private void openBuyerStock(Player player, Shop shop) {
        Inventory inv = buildBuyerStockInventory(shop);
        sessions.put(player.getUniqueId(), new BuyerStockSession(shop, inv));
        player.openInventory(inv);
    }

    private void openBuyAmount(Player player, Shop shop) {
        Inventory inv = buildBuyAmountInventory(shop, 1);
        BuyAmountSession bas = new BuyAmountSession(shop, inv, 1);
        sessions.put(player.getUniqueId(), bas);
        player.openInventory(inv);
    }

    // ── Inventory builders ─────────────────────────────────────────────────────

    private Inventory buildOwnerInventory(Shop shop) {
        Inventory inv = Bukkit.createInventory(null, GUI_SIZE,
            Component.text("Shop Setup", NamedTextColor.DARK_GREEN));
        fill(inv, makeFiller());
        inv.setItem(OWN_ITEM,   makeOwnerItemSlot(shop));
        inv.setItem(OWN_PRICE,  makeOwnerPriceButton(shop.getPrice()));
        inv.setItem(OWN_STOCK,  makeOwnerStockItem(shop));
        inv.setItem(OWN_DELETE, makeDeleteButton());
        return inv;
    }

    private Inventory buildPriceInventory(double price) {
        Inventory inv = Bukkit.createInventory(null, GUI_SIZE,
            Component.text("Set Price", NamedTextColor.GOLD));
        fill(inv, makeFiller());
        inv.setItem(PE_M1000, makeDelta(-1000)); inv.setItem(PE_M100, makeDelta(-100));
        inv.setItem(PE_M10,   makeDelta(-10));   inv.setItem(PE_M1,   makeDelta(-1));
        inv.setItem(PE_DISP,  makePriceDisplay(price));
        inv.setItem(PE_P1,    makeDelta(+1));    inv.setItem(PE_P10,  makeDelta(+10));
        inv.setItem(PE_P100,  makeDelta(+100));  inv.setItem(PE_P1000, makeDelta(+1000));
        inv.setItem(PE_RESET,   makeResetButton());
        inv.setItem(PE_BACK,    makeBackButton());
        inv.setItem(PE_CONFIRM, makeConfirmButton());
        return inv;
    }

    private Inventory buildBuyerStockInventory(Shop shop) {
        Inventory inv = Bukkit.createInventory(null, GUI_SIZE,
            Component.text(shop.getOwnerName() + "'s Shop", NamedTextColor.DARK_AQUA));
        fill(inv, makeFiller());
        inv.setItem(BS_INFO, makeBuyerInfoItem(shop));
        inv.setItem(BS_ITEM, makeBuyerStockItem(shop));
        return inv;
    }

    private Inventory buildBuyAmountInventory(Shop shop, int amount) {
        // Sparse — no filler glass (controller-friendly open layout)
        Inventory inv = Bukkit.createInventory(null, GUI_SIZE,
            Component.text("Buy – " + friendlyName(shop.getSellItem()), NamedTextColor.GREEN));
        inv.setItem(BA_BUY1,   makeBuyButton(1));
        inv.setItem(BA_BUY8,   makeBuyButton(8));
        inv.setItem(BA_BUY16,  makeBuyButton(16));
        inv.setItem(BA_BUY32,  makeBuyButton(32));
        inv.setItem(BA_BUY64,  makeBuyButton(64));
        inv.setItem(BA_BUYMAX, makeBuyMaxButton());
        inv.setItem(BA_DISP,   makeBuyDisplay(shop, amount));
        inv.setItem(BA_BACK,   makeBackButton());
        inv.setItem(BA_CONFIRM, makeConfirmButton());
        return inv;
    }

    private void refreshOwner(Inventory inv, Shop shop) {
        inv.setItem(OWN_ITEM,  makeOwnerItemSlot(shop));
        inv.setItem(OWN_PRICE, makeOwnerPriceButton(shop.getPrice()));
        inv.setItem(OWN_STOCK, makeOwnerStockItem(shop));
    }

    private void refreshBuyDisplay(BuyAmountSession bas) {
        bas.inventory.setItem(BA_DISP, makeBuyDisplay(bas.shop, bas.amount));
    }

    // ── Events ─────────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Session session = sessions.get(player.getUniqueId());
        if (session == null) return;
        if (!event.getInventory().equals(sessionInventory(session))) return;

        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true); return;
        }
        int raw = event.getRawSlot();
        int topSize = event.getView().getTopInventory().getSize();
        if (raw >= topSize && event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            event.setCancelled(true); return;
        }
        if (raw >= topSize) return;

        event.setCancelled(true);
        switch (session) {
            case OwnerSession os         -> handleOwnerClick(player, os, raw, event);
            case ItemSelectorSession is  -> handleSelectorClick(player, is, raw);
            case PriceSession ps         -> handlePriceClick(player, ps, raw);
            case BuyerStockSession bss   -> handleBuyerStockClick(player, bss, raw);
            case BuyAmountSession bas    -> handleBuyAmountClick(player, bas, raw);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Session session = sessions.get(player.getUniqueId());
        if (session == null) return;
        if (!event.getInventory().equals(sessionInventory(session))) return;
        int topSize = event.getView().getTopInventory().getSize();
        for (int slot : event.getRawSlots()) {
            if (slot < topSize) { event.setCancelled(true); return; }
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
            case OwnerSession os -> {
                // Safety wipe — prevent any clone from persisting in memory
                event.getInventory().setItem(OWN_ITEM, null);
            }
            case ItemSelectorSession is -> {
                // Reopen owner GUI (whether ESC or item was selected)
                scheduleOpen(player, () -> openOwner(player, is.shop()));
            }
            case PriceSession ps -> scheduleOpen(player, () -> openOwner(player, ps.shop));
            case BuyerStockSession ignored -> { /* plain close */ }
            case BuyAmountSession bas -> {
                if (!bas.purchased) {
                    // Back or ESC: return to buyer stock
                    scheduleOpen(player, () -> openBuyerStock(player, bas.shop));
                }
                // If purchased: already handled, just close
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
    }

    // ── Click handlers ─────────────────────────────────────────────────────────

    private void handleOwnerClick(Player player, OwnerSession os, int slot,
                                  InventoryClickEvent event) {
        switch (slot) {
            case OWN_ITEM   -> openItemSelector(player, os.shop());
            case OWN_PRICE  -> openPriceGUI(player, os.shop());
            case OWN_STOCK  -> {
                refreshOwner(os.inventory(), os.shop());
                player.sendMessage(Component.text("Stock refreshed.", NamedTextColor.GRAY));
            }
            case OWN_DELETE -> {
                plugin.getShopManager().removeShop(os.shop().getId());
                plugin.getStorage().deleteShop(os.shop().getId());
                plugin.getHolograms().delete(os.shop().getId());
                player.closeInventory();
                player.sendMessage(Component.text("Shop deleted.", NamedTextColor.RED));
            }
        }
    }

    private void handleSelectorClick(Player player, ItemSelectorSession is, int slot) {
        ItemStack clicked = is.inventory().getItem(slot);
        if (clicked == null || clicked.getType() == Material.AIR
                || clicked.getType() == Material.BARRIER) return;

        ItemStack template = clicked.clone();
        template.setAmount(1);
        is.shop().setSellItem(template);
        plugin.getStorage().saveShop(is.shop());
        plugin.getHolograms().createOrUpdate(is.shop());
        player.sendMessage(Component.text("Sell item set to ", NamedTextColor.GREEN)
            .append(Component.text(friendlyName(template), NamedTextColor.YELLOW))
            .append(Component.text(".", NamedTextColor.GREEN)));
        player.closeInventory(); // onInventoryClose reopens owner GUI
    }

    private void handlePriceClick(Player player, PriceSession ps, int slot) {
        switch (slot) {
            case PE_M1000 -> applyDelta(ps, -1000);
            case PE_M100  -> applyDelta(ps, -100);
            case PE_M10   -> applyDelta(ps, -10);
            case PE_M1    -> applyDelta(ps, -1);
            case PE_P1    -> applyDelta(ps, +1);
            case PE_P10   -> applyDelta(ps, +10);
            case PE_P100  -> applyDelta(ps, +100);
            case PE_P1000 -> applyDelta(ps, +1000);
            case PE_RESET -> { ps.pending = 0; ps.inventory.setItem(PE_DISP, makePriceDisplay(0)); }
            case PE_BACK  -> player.closeInventory();
            case PE_CONFIRM -> {
                ps.shop.setPrice(ps.pending);
                plugin.getStorage().saveShop(ps.shop);
                plugin.getHolograms().createOrUpdate(ps.shop);
                player.sendMessage(Component.text(
                    "Price set to $" + String.format("%.2f", ps.pending) + ".",
                    NamedTextColor.GREEN));
                player.closeInventory();
            }
        }
    }

    private void applyDelta(PriceSession ps, double delta) {
        ps.pending = Math.max(0, ps.pending + delta);
        ps.inventory.setItem(PE_DISP, makePriceDisplay(ps.pending));
    }

    private void handleBuyerStockClick(Player player, BuyerStockSession bss, int slot) {
        if (slot != BS_ITEM) return;
        Shop shop = bss.shop();
        if (!shop.isConfigured()) {
            player.sendMessage(Component.text("This shop is not configured yet.", NamedTextColor.RED));
            return;
        }
        openBuyAmount(player, shop);
    }

    private void handleBuyAmountClick(Player player, BuyAmountSession bas, int slot) {
        Shop shop = bas.shop;
        switch (slot) {
            case BA_BUY1   -> setAmount(bas, 1,  player);
            case BA_BUY8   -> setAmount(bas, 8,  player);
            case BA_BUY16  -> setAmount(bas, 16, player);
            case BA_BUY32  -> setAmount(bas, 32, player);
            case BA_BUY64  -> setAmount(bas, 64, player);
            case BA_BUYMAX -> {
                int max = calcMax(player, shop);
                setAmount(bas, Math.max(1, max), player);
            }
            case BA_BACK   -> player.closeInventory(); // onInventoryClose reopens stock view
            case BA_CONFIRM -> executePurchase(player, bas);
        }
    }

    private void setAmount(BuyAmountSession bas, int requested, Player player) {
        bas.amount = requested;
        refreshBuyDisplay(bas);
    }

    private void executePurchase(Player player, BuyAmountSession bas) {
        Shop shop = bas.shop;
        int qty = bas.amount;

        if (qty < 1) {
            player.sendMessage(Component.text("Select how many to buy first.", NamedTextColor.RED));
            return;
        }
        if (!plugin.getEconomy().isAvailable()) {
            player.sendMessage(Component.text("Economy is unavailable.", NamedTextColor.RED));
            return;
        }
        double total = shop.getPrice() * qty;
        if (!plugin.getEconomy().hasBalance(player.getUniqueId(), total)) {
            player.sendMessage(Component.text(
                "Insufficient funds. You need $" + String.format("%.2f", total) + ".",
                NamedTextColor.RED));
            return;
        }

        org.bukkit.block.Block block = shop.getChestLocation().getBlock();
        if (!(block.getState() instanceof org.bukkit.block.Chest cs)) {
            player.sendMessage(Component.text("Shop chest not found.", NamedTextColor.RED));
            return;
        }
        Inventory chestInv = cs.getInventory();
        ItemStack sellItem = shop.getSellItem();

        int stock = countStock(chestInv, sellItem);
        if (stock < qty) {
            player.sendMessage(Component.text(
                "Not enough stock. Available: " + stock + ".", NamedTextColor.RED));
            return;
        }

        int invSpace = countInventorySpace(player, sellItem);
        if (invSpace < qty) {
            player.sendMessage(Component.text(
                "Not enough inventory space for " + qty + " items.", NamedTextColor.RED));
            return;
        }

        // Atomic: withdraw → remove from chest × qty → give → pay owner
        if (!plugin.getEconomy().withdraw(player.getUniqueId(), total)) {
            player.sendMessage(Component.text("Payment failed.", NamedTextColor.RED));
            return;
        }
        int removed = 0;
        while (removed < qty) {
            if (!removeOneFromChest(chestInv, sellItem)) {
                // Partial removal — refund remainder
                plugin.getEconomy().deposit(player.getUniqueId(), (qty - removed) * shop.getPrice());
                qty = removed;
                break;
            }
            removed++;
        }
        if (qty < 1) {
            plugin.getEconomy().deposit(player.getUniqueId(), total);
            player.sendMessage(Component.text("Could not retrieve item from chest. Refunded.",
                NamedTextColor.RED));
            return;
        }

        ItemStack toGive = sellItem.clone();
        toGive.setAmount(qty);
        player.getInventory().addItem(toGive);
        plugin.getEconomy().deposit(shop.getOwnerUuid(), shop.getPrice() * qty);
        plugin.getHolograms().createOrUpdate(shop); // refresh stock count in hologram

        bas.purchased = true;
        player.closeInventory();
        player.sendMessage(Component.text("Purchased ", NamedTextColor.GREEN)
            .append(Component.text(qty + "× " + friendlyName(sellItem), NamedTextColor.YELLOW))
            .append(Component.text(" for $" + String.format("%.2f", shop.getPrice() * qty) + ".",
                NamedTextColor.GREEN)));
    }

    // ── Stock helpers ──────────────────────────────────────────────────────────

    private int countStock(Inventory inv, ItemStack template) {
        int n = 0;
        for (ItemStack s : inv.getContents()) {
            if (s != null && s.isSimilar(template)) n += s.getAmount();
        }
        return n;
    }

    private int getChestStock(Shop shop) {
        if (shop.getSellItem() == null) return 0;
        org.bukkit.block.Block b = shop.getChestLocation().getBlock();
        if (!(b.getState() instanceof org.bukkit.block.Chest cs)) return 0;
        return countStock(cs.getInventory(), shop.getSellItem());
    }

    private boolean removeOneFromChest(Inventory inv, ItemStack template) {
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s != null && s.isSimilar(template)) {
                if (s.getAmount() > 1) { s.setAmount(s.getAmount() - 1); inv.setItem(i, s); }
                else inv.setItem(i, null);
                return true;
            }
        }
        return false;
    }

    private int countInventorySpace(Player player, ItemStack template) {
        int space = 0;
        for (ItemStack s : player.getInventory().getStorageContents()) {
            if (s == null || s.getType() == Material.AIR) space += template.getMaxStackSize();
            else if (s.isSimilar(template)) space += template.getMaxStackSize() - s.getAmount();
        }
        return space;
    }

    private int calcMax(Player player, Shop shop) {
        if (!plugin.getEconomy().isAvailable() || shop.getPrice() <= 0) return 0;
        int byBalance  = (int) Math.floor(
            plugin.getEconomy().getBalance(player.getUniqueId()) / shop.getPrice());
        int byStock    = getChestStock(shop);
        int byInvSpace = countInventorySpace(player, shop.getSellItem());
        return Math.max(0, Math.min(byBalance, Math.min(byStock, byInvSpace)));
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private void scheduleOpen(Player player, Runnable action) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) action.run();
        });
    }

    private Inventory sessionInventory(Session s) {
        return switch (s) {
            case OwnerSession os        -> os.inventory();
            case ItemSelectorSession is -> is.inventory();
            case PriceSession ps        -> ps.inventory;
            case BuyerStockSession bss  -> bss.inventory();
            case BuyAmountSession bas   -> bas.inventory;
        };
    }

    private String friendlyName(ItemStack item) {
        if (item == null) return "???";
        return item.getType().name().replace('_', ' ');
    }

    // ── Item factories ─────────────────────────────────────────────────────────

    private void fill(Inventory inv, ItemStack filler) {
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);
    }

    private ItemStack makeFiller() {
        ItemStack it = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false));
        it.setItemMeta(m);
        return it;
    }

    // Owner GUI ----------------------------------------------------------------
    private ItemStack makeOwnerItemSlot(Shop shop) {
        if (shop.getSellItem() == null) {
            ItemStack it = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
            ItemMeta m = it.getItemMeta();
            m.displayName(colored("Set Sell Item", NamedTextColor.GREEN));
            m.lore(List.of(plain("Click to choose from chest contents")));
            it.setItemMeta(m);
            return it;
        }
        ItemStack display = shop.getSellItem().clone();
        ItemMeta m = display.hasItemMeta()
            ? display.getItemMeta() : Bukkit.getItemFactory().getItemMeta(display.getType());
        List<Component> lore = m.lore() != null ? new ArrayList<>(m.lore()) : new ArrayList<>();
        lore.add(Component.empty());
        lore.add(colored("Sell Item", NamedTextColor.GREEN).decoration(TextDecoration.BOLD, true));
        lore.add(plain("Click to change (opens chest picker)"));
        m.lore(lore);
        display.setItemMeta(m);
        return display;
    }

    private ItemStack makeOwnerPriceButton(double price) {
        ItemStack it = new ItemStack(Material.GOLD_NUGGET);
        ItemMeta m = it.getItemMeta();
        m.displayName(colored("Set Price", NamedTextColor.GOLD));
        m.lore(List.of(
            plain("Current: ").append(colored("$" + fmt(price), NamedTextColor.GREEN)),
            Component.empty(), plain("Click to adjust")
        ));
        it.setItemMeta(m);
        return it;
    }

    private ItemStack makeOwnerStockItem(Shop shop) {
        int stock = getChestStock(shop);
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta m = it.getItemMeta();
        m.displayName(colored("Stock", NamedTextColor.AQUA));
        m.lore(List.of(
            plain("In chest: ").append(colored(String.valueOf(stock),
                stock > 0 ? NamedTextColor.GREEN : NamedTextColor.RED)),
            Component.empty(), plain("Click to refresh")
        ));
        it.setItemMeta(m);
        return it;
    }

    private ItemStack makeDeleteButton() {
        ItemStack it = new ItemStack(Material.BARRIER);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text("Delete Shop", NamedTextColor.RED)
            .decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
        m.lore(List.of(plain("Click to permanently delete this shop")));
        it.setItemMeta(m);
        return it;
    }

    // Price-edit GUI -----------------------------------------------------------
    private ItemStack makePriceDisplay(double price) {
        ItemStack it = new ItemStack(price > 0 ? Material.GOLD_BLOCK : Material.IRON_BLOCK);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text("$" + fmt(price), NamedTextColor.GOLD)
            .decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
        m.lore(List.of(plain("Use the buttons to adjust")));
        it.setItemMeta(m);
        return it;
    }

    private ItemStack makeDelta(double delta) {
        boolean pos = delta > 0;
        double abs = Math.abs(delta);
        Material mat = pos
            ? (abs >= 1000 ? Material.LIME_CONCRETE  : abs >= 100 ? Material.LIME_WOOL
             : abs >= 10   ? Material.LIME_TERRACOTTA : Material.LIME_STAINED_GLASS_PANE)
            : (abs >= 1000 ? Material.RED_CONCRETE   : abs >= 100 ? Material.RED_WOOL
             : abs >= 10   ? Material.RED_TERRACOTTA  : Material.RED_STAINED_GLASS_PANE);
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text((pos ? "+" : "") + String.format("%.0f", delta),
            pos ? NamedTextColor.GREEN : NamedTextColor.RED)
            .decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
        it.setItemMeta(m);
        return it;
    }

    private ItemStack makeResetButton() {
        ItemStack it = new ItemStack(Material.ORANGE_CONCRETE);
        ItemMeta m = it.getItemMeta();
        m.displayName(colored("Reset to $0.00", NamedTextColor.GOLD));
        it.setItemMeta(m);
        return it;
    }

    private ItemStack makeBackButton() {
        ItemStack it = new ItemStack(Material.ARROW);
        ItemMeta m = it.getItemMeta();
        m.displayName(colored("Back", NamedTextColor.GRAY));
        m.lore(List.of(plain("Discard changes")));
        it.setItemMeta(m);
        return it;
    }

    private ItemStack makeConfirmButton() {
        ItemStack it = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta m = it.getItemMeta();
        m.displayName(colored("Confirm", NamedTextColor.GREEN)
            .decoration(TextDecoration.BOLD, true));
        m.lore(List.of(plain("Save and return")));
        it.setItemMeta(m);
        return it;
    }

    // Buyer stock view ---------------------------------------------------------
    private ItemStack makeBuyerInfoItem(Shop shop) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta m = it.getItemMeta();
        m.displayName(colored(shop.getOwnerName() + "'s Shop", NamedTextColor.GOLD)
            .decoration(TextDecoration.BOLD, true));
        m.lore(List.of(
            plain("Price: ").append(colored("$" + fmt(shop.getPrice()), NamedTextColor.GREEN)),
            plain("Stock: ").append(colored(String.valueOf(getChestStock(shop)), NamedTextColor.YELLOW))
        ));
        it.setItemMeta(m);
        return it;
    }

    private ItemStack makeBuyerStockItem(Shop shop) {
        if (!shop.isConfigured()) {
            ItemStack it = new ItemStack(Material.RED_STAINED_GLASS_PANE);
            ItemMeta m = it.getItemMeta();
            m.displayName(colored("Shop not configured yet", NamedTextColor.RED));
            it.setItemMeta(m);
            return it;
        }
        ItemStack display = shop.getSellItem().clone();
        ItemMeta m = display.hasItemMeta()
            ? display.getItemMeta() : Bukkit.getItemFactory().getItemMeta(display.getType());
        List<Component> lore = m.lore() != null ? new ArrayList<>(m.lore()) : new ArrayList<>();
        lore.add(Component.empty());
        lore.add(plain("Price: ").append(colored("$" + fmt(shop.getPrice()), NamedTextColor.GREEN)));
        lore.add(plain("Stock: ").append(colored(String.valueOf(getChestStock(shop)), NamedTextColor.YELLOW)));
        lore.add(Component.empty());
        lore.add(colored("Click to buy", NamedTextColor.AQUA));
        m.lore(lore);
        display.setItemMeta(m);
        return display;
    }

    // Buy-amount GUI -----------------------------------------------------------
    private ItemStack makeBuyButton(int qty) {
        ItemStack it = new ItemStack(Material.GREEN_CONCRETE);
        ItemMeta m = it.getItemMeta();
        m.displayName(colored("Buy " + qty, NamedTextColor.GREEN)
            .decoration(TextDecoration.BOLD, true));
        it.setItemMeta(m);
        return it;
    }

    private ItemStack makeBuyMaxButton() {
        ItemStack it = new ItemStack(Material.LIME_CONCRETE);
        ItemMeta m = it.getItemMeta();
        m.displayName(colored("Buy Max", NamedTextColor.GREEN)
            .decoration(TextDecoration.BOLD, true));
        m.lore(List.of(plain("Buys as much as you can afford/carry")));
        it.setItemMeta(m);
        return it;
    }

    private ItemStack makeBuyDisplay(Shop shop, int qty) {
        ItemStack display = shop.getSellItem() != null
            ? shop.getSellItem().clone() : new ItemStack(Material.PAPER);
        display.setAmount(Math.min(qty, 64));
        ItemMeta m = display.hasItemMeta()
            ? display.getItemMeta() : Bukkit.getItemFactory().getItemMeta(display.getType());

        double priceEach = shop.getPrice();
        double total     = priceEach * qty;

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Buying: ", NamedTextColor.GRAY)
            .append(Component.text(qty + "× " + friendlyName(shop.getSellItem()),
                NamedTextColor.WHITE))
            .decoration(TextDecoration.ITALIC, false));
        lore.add(plain("Price each: ").append(colored("$" + fmt(priceEach), NamedTextColor.GREEN)));
        lore.add(plain("Total:      ").append(colored("$" + fmt(total), NamedTextColor.GOLD)));
        lore.add(Component.empty());
        lore.add(plain("Stock: ").append(colored(String.valueOf(getChestStock(shop)),
            NamedTextColor.YELLOW)));
        m.lore(lore);
        m.displayName(colored(qty + "× " + friendlyName(shop.getSellItem()), NamedTextColor.WHITE)
            .decoration(TextDecoration.BOLD, true));
        display.setItemMeta(m);
        return display;
    }

    // ── Component helpers ──────────────────────────────────────────────────────
    private Component plain(String t)  {
        return Component.text(t, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
    }
    private Component colored(String t, NamedTextColor c) {
        return Component.text(t, c).decoration(TextDecoration.ITALIC, false);
    }
    private String fmt(double v) { return String.format("%.2f", v); }
}
