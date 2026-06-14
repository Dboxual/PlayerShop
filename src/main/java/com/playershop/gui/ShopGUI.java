package com.playershop.gui;

import com.playershop.PlayerShopPlugin;
import com.playershop.data.Listing;
import com.playershop.data.PlayerShop;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
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

    // ── Session types ─────────────────────────────────────────────────────────
    private sealed interface Session
        permits OwnerSession, DepositSession, PricingSession, BuyerSession,
                StockSession, AmountSession, ConfirmSession {}

    private static final class OwnerSession implements Session {
        final PlayerShop shop;
        final Inventory  inventory;
        OwnerSession(PlayerShop shop, Inventory inv) { this.shop = shop; this.inventory = inv; }
    }

    private static final class DepositSession implements Session {
        final PlayerShop shop;
        final Inventory  inventory;
        DepositSession(PlayerShop shop, Inventory inv) { this.shop = shop; this.inventory = inv; }
    }

    private static final class PricingSession implements Session {
        final PlayerShop      shop;
        final Inventory       inventory;
        final ItemStack       depositItem;
        final List<ItemStack> depositedItems;
        final int             depositTotal;
        int priceCents; // integer cents; $1.00 = 100

        PricingSession(PlayerShop shop, Inventory inv,
                       ItemStack depositItem, List<ItemStack> depositedItems) {
            this.shop = shop;
            this.inventory = inv;
            ItemStack rep = depositItem.clone();
            rep.setAmount(1);
            this.depositItem    = rep;
            this.depositedItems = depositedItems;
            this.depositTotal   = depositedItems.stream().mapToInt(ItemStack::getAmount).sum();
            this.priceCents     = 100; // default $1.00
        }
    }

    private static final class BuyerSession implements Session {
        final PlayerShop shop;
        final UUID       playerUuid;
        final Inventory  inventory;
        BuyerSession(PlayerShop shop, UUID playerUuid, Inventory inv) {
            this.shop = shop; this.playerUuid = playerUuid; this.inventory = inv;
        }
    }

    private static final class StockSession implements Session {
        final PlayerShop shop;
        final Listing    listing;
        final Inventory  inventory;
        StockSession(PlayerShop shop, Listing listing, Inventory inv) {
            this.shop = shop; this.listing = listing; this.inventory = inv;
        }
    }

    private static final class AmountSession implements Session {
        final PlayerShop shop;
        final Listing    listing;
        final int        listingIndex;
        final Inventory  inventory;
        AmountSession(PlayerShop shop, Listing listing, int listingIndex, Inventory inv) {
            this.shop = shop; this.listing = listing;
            this.listingIndex = listingIndex; this.inventory = inv;
        }
    }

    private static final class ConfirmSession implements Session {
        final PlayerShop shop;
        final Listing    listing;
        final int        listingIndex; // slot in buyer GUI to refresh on return
        final int        quantity;
        final double     totalCost;
        final Inventory  inventory;
        ConfirmSession(PlayerShop shop, Listing listing, int listingIndex,
                       int quantity, double totalCost, Inventory inv) {
            this.shop = shop; this.listing = listing; this.listingIndex = listingIndex;
            this.quantity = quantity; this.totalCost = totalCost; this.inventory = inv;
        }
    }

    // ── Slot constants ────────────────────────────────────────────────────────
    //  In 54-slot GUIs: slots 0-44 = content area, 45 = left, 49 = center, 53 = right
    private static final int GUI_SIZE         = 54;
    private static final int LISTING_SLOTS    = 45;
    private static final int PLACEHOLDER_SLOT = 22;

    // Owner GUI — sell at center, no remove button
    private static final int OW_SELL = 49;

    // Deposit GUI
    private static final int DS_CANCEL = 45;
    private static final int DS_HELP   = 49;
    private static final int DS_FINISH = 53;

    // Pricing GUI
    private static final int PR_CANCEL        = 45;
    private static final int PR_DEC_100       = 9;
    private static final int PR_DEC_10        = 10;
    private static final int PR_DEC_1         = 11;
    private static final int PR_DEC_010       = 12;
    private static final int PR_ITEM_PREVIEW  = 13;
    private static final int PR_INC_010       = 14;
    private static final int PR_INC_1         = 15;
    private static final int PR_INC_10        = 16;
    private static final int PR_INC_100       = 17;
    private static final int PR_PRICE_DISPLAY = 22;
    private static final int PR_CONFIRM       = 53;

    // Stock GUI
    private static final int SK_REMOVE = 45;

    // Amount selector GUI
    private static final int AM_BACK           = 45;
    private static final int AM_BUY_1          = 46;
    private static final int AM_BUY_8          = 47;
    private static final int AM_BUY_16         = 48;
    private static final int AM_BUY_32         = 49;
    private static final int AM_BUY_MAX_STACK  = 50;
    private static final int AM_BUY_MAX_AFFORD = 51;

    // Buyer GUI — bottom row toggle
    private static final int BU_TOGGLE = 49;

    // Confirmation GUI — 27 slots (3 rows)
    private static final int CF_SIZE    = 27;
    private static final int CF_PREVIEW = 13; // center of 3-row GUI
    private static final int CF_CANCEL  = 18; // left of bottom row
    private static final int CF_CONFIRM = 26; // right of bottom row

    private final PlayerShopPlugin   plugin;
    private final Map<UUID, Session> sessions         = new HashMap<>();
    private final Map<UUID, Boolean> autoConfirmStack = new HashMap<>(); // per-player, in-memory

    public ShopGUI(PlayerShopPlugin plugin) { this.plugin = plugin; }

    // ── Entry points ──────────────────────────────────────────────────────────

    public void openOwnerGUI(Player player, PlayerShop shop) {
        Inventory inv = Bukkit.createInventory(null, GUI_SIZE,
            Component.text(shop.getOwnerName() + "'s Shop", NamedTextColor.WHITE));
        OwnerSession os = new OwnerSession(shop, inv);
        renderOwnerGUI(os);
        sessions.put(player.getUniqueId(), os);
        player.openInventory(inv);
    }

    public void openDepositGUI(Player player, PlayerShop shop) {
        Inventory inv = Bukkit.createInventory(null, GUI_SIZE,
            Component.text("New Listing: Add Item", NamedTextColor.AQUA));
        DepositSession ds = new DepositSession(shop, inv);
        renderDepositGUI(ds);
        sessions.put(player.getUniqueId(), ds);
        player.openInventory(inv);
    }

    public void openBuyerGUI(Player player, PlayerShop shop) {
        Inventory inv = Bukkit.createInventory(null, GUI_SIZE,
            Component.text(shop.getOwnerName() + "'s Shop", NamedTextColor.WHITE));
        BuyerSession bs = new BuyerSession(shop, player.getUniqueId(), inv);
        renderBuyerGUI(bs);
        sessions.put(player.getUniqueId(), bs);
        player.openInventory(inv);
    }

    public void openStockGUI(Player player, PlayerShop shop, Listing listing) {
        Inventory inv = Bukkit.createInventory(null, GUI_SIZE,
            Component.text("Stock: " + prettyName(listing), NamedTextColor.AQUA));
        StockSession ss = new StockSession(shop, listing, inv);
        fillStockSlots(ss);
        sessions.put(player.getUniqueId(), ss);
        player.openInventory(inv);
    }

    private void openAmountSelectorGUI(Player player, PlayerShop shop,
                                       Listing listing, int listingIndex) {
        Inventory inv = Bukkit.createInventory(null, GUI_SIZE,
            Component.text("Select Amount", NamedTextColor.AQUA));
        AmountSession as = new AmountSession(shop, listing, listingIndex, inv);
        renderAmountSelectorGUI(player, as);
        sessions.put(player.getUniqueId(), as);
        player.openInventory(inv);
    }

    private void openConfirmGUI(Player player, PlayerShop shop,
                                Listing listing, int listingIndex, int quantity) {
        double totalCost = listing.getPricePerItem() * quantity;
        Inventory inv = Bukkit.createInventory(null, CF_SIZE,
            Component.text("Confirm Purchase", NamedTextColor.AQUA));
        ConfirmSession cs = new ConfirmSession(shop, listing, listingIndex, quantity, totalCost, inv);
        renderConfirmGUI(cs);
        sessions.put(player.getUniqueId(), cs);
        player.openInventory(inv);
    }

    private void openPricingGUI(Player player, PlayerShop shop,
                                ItemStack depositItem, List<ItemStack> depositedItems) {
        Inventory inv = Bukkit.createInventory(null, GUI_SIZE,
            Component.text("Set Listing Price", NamedTextColor.AQUA));
        PricingSession ps = new PricingSession(shop, inv, depositItem, depositedItems);
        renderPricingGUI(ps);
        sessions.put(player.getUniqueId(), ps);
        player.openInventory(inv);
    }

    // ── Renderers ─────────────────────────────────────────────────────────────

    private void renderOwnerGUI(OwnerSession os) {
        List<Listing> listings = os.shop.getListings();
        if (listings.isEmpty()) {
            os.inventory.setItem(PLACEHOLDER_SLOT, makePlaceholder());
        } else {
            int limit = Math.min(listings.size(), LISTING_SLOTS);
            for (int i = 0; i < limit; i++) {
                os.inventory.setItem(i, makeListingItem(listings.get(i)));
            }
        }
        os.inventory.setItem(OW_SELL, makeSellButton());
    }

    private void renderDepositGUI(DepositSession ds) {
        ds.inventory.setItem(DS_CANCEL, makeCancelButton());
        ds.inventory.setItem(DS_HELP,   makeDepositHelpItem());
        ds.inventory.setItem(DS_FINISH, makeFinishButton());
    }

    private void renderPricingGUI(PricingSession ps) {
        ps.inventory.setItem(PR_DEC_100,       makePriceButton("-$100",  false));
        ps.inventory.setItem(PR_DEC_10,        makePriceButton("-$10",   false));
        ps.inventory.setItem(PR_DEC_1,         makePriceButton("-$1",    false));
        ps.inventory.setItem(PR_DEC_010,       makePriceButton("-$0.10", false));
        ps.inventory.setItem(PR_ITEM_PREVIEW,  makeItemPreview(ps));
        ps.inventory.setItem(PR_INC_010,       makePriceButton("+$0.10", true));
        ps.inventory.setItem(PR_INC_1,         makePriceButton("+$1",    true));
        ps.inventory.setItem(PR_INC_10,        makePriceButton("+$10",   true));
        ps.inventory.setItem(PR_INC_100,       makePriceButton("+$100",  true));
        ps.inventory.setItem(PR_PRICE_DISPLAY, makePriceDisplay(ps.priceCents));
        ps.inventory.setItem(PR_CANCEL,        makeCancelButton());
        ps.inventory.setItem(PR_CONFIRM,       makeFinishButton());
    }

    private void renderBuyerGUI(BuyerSession bs) {
        List<Listing> listings = bs.shop.getListings();
        if (listings.isEmpty()) {
            bs.inventory.setItem(PLACEHOLDER_SLOT, makeBuyerPlaceholder());
        } else {
            int limit = Math.min(listings.size(), LISTING_SLOTS);
            for (int i = 0; i < limit; i++) {
                bs.inventory.setItem(i, makeBuyerListingItem(listings.get(i)));
            }
        }
        bs.inventory.setItem(BU_TOGGLE,
            makeAutoConfirmToggle(autoConfirmStack.getOrDefault(bs.playerUuid, false)));
    }

    private void fillStockSlots(StockSession ss) {
        ItemStack template = ss.listing.getTemplate();
        int maxStack  = template.getMaxStackSize();
        int remaining = ss.listing.getStock();
        for (int i = 0; i < LISTING_SLOTS; i++) {
            if (remaining <= 0) {
                ss.inventory.setItem(i, null);
            } else {
                int amount = Math.min(remaining, maxStack);
                ItemStack stack = template.clone();
                stack.setAmount(amount);
                ss.inventory.setItem(i, stack);
                remaining -= amount;
            }
        }
        ss.inventory.setItem(SK_REMOVE, makeRemoveListingButton());
    }

    private void renderAmountSelectorGUI(Player player, AmountSession as) {
        as.inventory.setItem(PLACEHOLDER_SLOT, makeAmountPreview(as.listing));

        int    maxStack  = as.listing.getTemplate().getMaxStackSize();
        double ppi       = as.listing.getPricePerItem();
        int    stock     = as.listing.getStock();
        int    canAfford = (ppi > 0)
            ? (int) Math.floor(plugin.getEconomy().getBalance(player.getUniqueId()) / ppi)
            : Integer.MAX_VALUE;
        int canFit     = inventoryCapacity(player, as.listing.getTemplate());
        int maxBuyable = Math.min(stock, Math.min(canAfford, canFit));

        as.inventory.setItem(AM_BACK,          makeBackButton());
        as.inventory.setItem(AM_BUY_1,
            makeAmountButton("Buy 1",           1,        maxStack, maxBuyable, ppi));
        as.inventory.setItem(AM_BUY_8,
            makeAmountButton("Buy 8",           8,        maxStack, maxBuyable, ppi));
        as.inventory.setItem(AM_BUY_16,
            makeAmountButton("Buy 16",          16,       maxStack, maxBuyable, ppi));
        as.inventory.setItem(AM_BUY_32,
            makeAmountButton("Buy 32",          32,       maxStack, maxBuyable, ppi));
        as.inventory.setItem(AM_BUY_MAX_STACK,
            makeAmountButton("Buy " + maxStack, maxStack, maxStack, maxBuyable, ppi));
        int safeMax = Math.min(maxStack, maxBuyable);
        as.inventory.setItem(AM_BUY_MAX_AFFORD,
            makeAmountButton("Buy Max",         safeMax,  maxStack, maxBuyable, ppi));
    }

    private void renderConfirmGUI(ConfirmSession cs) {
        cs.inventory.setItem(CF_PREVIEW, makeConfirmPreview(cs));
        cs.inventory.setItem(CF_CANCEL,  makeCancelButton());
        cs.inventory.setItem(CF_CONFIRM, makeFinishButton());
    }

    // ── Events ────────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Session session = sessions.get(player.getUniqueId());
        if (session == null) return;
        if (!event.getInventory().equals(sessionInventory(session))) return;

        ClickType ct  = event.getClick();
        int raw       = event.getRawSlot();
        int topSize   = event.getView().getTopInventory().getSize();

        switch (session) {
            case OwnerSession os -> {
                if (ct == ClickType.DOUBLE_CLICK || ct == ClickType.UNKNOWN
                        || ct == ClickType.SWAP_OFFHAND || ct == ClickType.CREATIVE) {
                    event.setCancelled(true);
                    return;
                }
                if (raw >= topSize) { event.setCancelled(true); return; }
                event.setCancelled(true);
                handleOwnerClick(player, os, raw);
            }
            case DepositSession ds -> {
                if (ct == ClickType.UNKNOWN || ct == ClickType.SWAP_OFFHAND) {
                    event.setCancelled(true);
                    return;
                }
                if (raw >= topSize) {
                    if (ct == ClickType.DOUBLE_CLICK || ct == ClickType.CREATIVE)
                        event.setCancelled(true);
                    return;
                }
                if (raw >= LISTING_SLOTS) {
                    event.setCancelled(true);
                    if (raw == DS_FINISH) handleDepositContinue(player, ds);
                    else if (raw == DS_CANCEL) handleDepositCancel(player, ds);
                    return;
                }
                if (ct == ClickType.DOUBLE_CLICK || ct == ClickType.CREATIVE)
                    event.setCancelled(true);
            }
            case PricingSession ps -> {
                if (ct == ClickType.DOUBLE_CLICK || ct == ClickType.UNKNOWN
                        || ct == ClickType.SWAP_OFFHAND || ct == ClickType.CREATIVE) {
                    event.setCancelled(true);
                    return;
                }
                if (raw >= topSize) { event.setCancelled(true); return; }
                event.setCancelled(true);
                handlePricingClick(player, ps, raw);
            }
            case BuyerSession bs -> {
                event.setCancelled(true);
                if (raw >= topSize) return;
                // Bottom row: only the toggle is interactive
                if (raw >= LISTING_SLOTS) {
                    if (raw == BU_TOGGLE && ct == ClickType.LEFT) {
                        boolean newVal = !autoConfirmStack.getOrDefault(player.getUniqueId(), false);
                        autoConfirmStack.put(player.getUniqueId(), newVal);
                        bs.inventory.setItem(BU_TOGGLE, makeAutoConfirmToggle(newVal));
                    }
                    return;
                }
                // Listing area
                // Discard unusual click types before routing to listing actions
                if (ct == ClickType.DOUBLE_CLICK || ct == ClickType.UNKNOWN
                        || ct == ClickType.SWAP_OFFHAND || ct == ClickType.CREATIVE
                        || ct == ClickType.MIDDLE || ct == ClickType.DROP
                        || ct == ClickType.CONTROL_DROP || ct == ClickType.NUMBER_KEY) {
                    return; // already cancelled above
                }
                List<Listing> listings = bs.shop.getListings();
                if (raw >= listings.size()) return;
                Listing listing = listings.get(raw);

                // isShiftClick() checked BEFORE ClickType.LEFT — any edge case where
                // SHIFT_LEFT is mis-classified as LEFT would incorrectly buy 1 otherwise
                if (event.isShiftClick()) {
                    if (ct == ClickType.SHIFT_LEFT) {
                        // SHIFT_LEFT only: stack-buy flow
                        int qty = safeQuantity(player, listing, listing.getTemplate().getMaxStackSize());
                        if (qty <= 0) { sendCannotPurchase(player, listing); return; }
                        if (autoConfirmStack.getOrDefault(player.getUniqueId(), false)) {
                            if (executePurchase(player, bs.shop, listing, qty))
                                bs.inventory.setItem(raw, makeBuyerListingItem(listing));
                        } else {
                            openConfirmGUI(player, bs.shop, listing, raw, qty);
                        }
                    } else {
                        // SHIFT_RIGHT: treat same as plain RIGHT (amount selector)
                        if (listing.getStock() <= 0) {
                            sendCannotPurchase(player, listing);
                        } else {
                            openAmountSelectorGUI(player, bs.shop, listing, raw);
                        }
                    }
                } else if (ct == ClickType.LEFT) {
                    // Plain left-click: buy 1
                    int qty = safeQuantity(player, listing, 1);
                    if (qty <= 0) { sendCannotPurchase(player, listing); return; }
                    if (executePurchase(player, bs.shop, listing, qty))
                        bs.inventory.setItem(raw, makeBuyerListingItem(listing));
                } else if (event.isRightClick()) {
                    // RIGHT (isRightClick catches RIGHT; SHIFT_RIGHT already handled above)
                    if (listing.getStock() <= 0) {
                        sendCannotPurchase(player, listing);
                    } else {
                        openAmountSelectorGUI(player, bs.shop, listing, raw);
                    }
                }
            }
            case StockSession ss -> {
                if (ct == ClickType.UNKNOWN || ct == ClickType.SWAP_OFFHAND
                        || ct == ClickType.DOUBLE_CLICK || ct == ClickType.CREATIVE) {
                    event.setCancelled(true);
                    return;
                }
                if (raw >= topSize) {
                    // Player inventory — only filter shift-clicks of non-matching items
                    if (ct == ClickType.SHIFT_LEFT || ct == ClickType.SHIFT_RIGHT) {
                        ItemStack clickedItem = event.getCurrentItem();
                        if (clickedItem != null && clickedItem.getType() != Material.AIR
                                && !ss.listing.getTemplate().isSimilar(clickedItem)) {
                            event.setCancelled(true);
                            sendStockReject(player, ss.listing);
                        }
                    }
                    return;
                }
                // Top inventory
                if (raw == SK_REMOVE) {
                    event.setCancelled(true);
                    handleRemoveListing(player, ss);
                    return;
                }
                if (raw >= LISTING_SLOTS) {
                    // Slots 46-53 — empty guard slots
                    event.setCancelled(true);
                    return;
                }
                // Slots 0-44: stock area — allow take-out freely, filter non-matching placements
                if (ct == ClickType.NUMBER_KEY) {
                    ItemStack hotbarItem = player.getInventory().getItem(event.getHotbarButton());
                    if (hotbarItem != null && hotbarItem.getType() != Material.AIR
                            && !ss.listing.getTemplate().isSimilar(hotbarItem)) {
                        event.setCancelled(true);
                        sendStockReject(player, ss.listing);
                    }
                    return;
                }
                ItemStack cursor = event.getCursor();
                if (cursor != null && cursor.getType() != Material.AIR
                        && !ss.listing.getTemplate().isSimilar(cursor)) {
                    event.setCancelled(true);
                    sendStockReject(player, ss.listing);
                }
            }
            case AmountSession as -> {
                event.setCancelled(true);
                if (raw >= topSize) return;
                if (ct != ClickType.LEFT) return;
                handleAmountClick(player, as, raw);
            }
            case ConfirmSession cs -> {
                event.setCancelled(true);
                if (raw >= topSize) return;
                if (ct != ClickType.LEFT) return;
                if (raw == CF_CONFIRM) handleConfirmPurchase(player, cs);
                else if (raw == CF_CANCEL) openBuyerGUI(player, cs.shop);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Session session = sessions.get(player.getUniqueId());
        if (session == null) return;
        if (!event.getInventory().equals(sessionInventory(session))) return;
        int topSize = event.getView().getTopInventory().getSize();

        boolean touchesTop = event.getRawSlots().stream().anyMatch(s -> s < topSize);
        if (!touchesTop) return;

        switch (session) {
            case OwnerSession os    -> event.setCancelled(true);
            case PricingSession ps  -> event.setCancelled(true);
            case BuyerSession bs    -> event.setCancelled(true);
            case AmountSession as   -> event.setCancelled(true);
            case ConfirmSession cs  -> event.setCancelled(true);
            case DepositSession ds  -> {
                if (event.getRawSlots().stream().anyMatch(s -> s < topSize && s >= LISTING_SLOTS))
                    event.setCancelled(true);
            }
            case StockSession ss -> {
                if (event.getRawSlots().stream().anyMatch(s -> s < topSize && s >= LISTING_SLOTS)) {
                    event.setCancelled(true);
                    return;
                }
                ItemStack dragItem = event.getOldCursor();
                if (dragItem != null && dragItem.getType() != Material.AIR
                        && !ss.listing.getTemplate().isSimilar(dragItem)) {
                    event.setCancelled(true);
                    sendStockReject(player, ss.listing);
                }
            }
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
            case DepositSession ds -> returnDepositItems(player, ds);
            case PricingSession ps -> returnPricingItems(player, ps);
            case StockSession ss   -> handleStockClose(player, ss);
            case OwnerSession os   -> {}
            case BuyerSession bs   -> {}
            case AmountSession as  -> {}
            case ConfirmSession cs -> {}
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Session session = sessions.remove(player.getUniqueId());
        if      (session instanceof DepositSession ds) returnDepositItems(player, ds);
        else if (session instanceof PricingSession ps) returnPricingItems(player, ps);
        else if (session instanceof StockSession ss)   handleStockClose(player, ss);
    }

    // ── Shop viewer management ────────────────────────────────────────────────

    /**
     * Closes all active GUI sessions for a given shop.
     * Items held in deposit/pricing/restock sessions are returned via the normal
     * InventoryCloseEvent path. Viewers receive a notification message.
     */
    public void closeShopViewers(PlayerShop shop) {
        new HashMap<>(sessions).forEach((uuid, session) -> {
            if (shopOf(session).getOwnerUuid().equals(shop.getOwnerUuid())) {
                Player viewer = plugin.getServer().getPlayer(uuid);
                if (viewer != null) {
                    viewer.sendMessage(Component.text("[PlayerShop] ", NamedTextColor.GOLD)
                        .append(Component.text("This shop was closed.", NamedTextColor.RED)));
                    viewer.closeInventory(); // fires InventoryCloseEvent → item return handled there
                }
            }
        });
    }

    /** Extracts the PlayerShop from any session type. */
    private PlayerShop shopOf(Session s) {
        return switch (s) {
            case OwnerSession os   -> os.shop;
            case DepositSession ds -> ds.shop;
            case PricingSession ps -> ps.shop;
            case BuyerSession bs   -> bs.shop;
            case StockSession ss   -> ss.shop;
            case AmountSession as  -> as.shop;
            case ConfirmSession cs -> cs.shop;
        };
    }

    // ── Click handlers ────────────────────────────────────────────────────────

    private void handleOwnerClick(Player player, OwnerSession os, int slot) {
        if (slot == OW_SELL) {
            openDepositGUI(player, os.shop);
            return;
        }
        if (slot < LISTING_SLOTS) {
            List<Listing> listings = os.shop.getListings();
            if (slot < listings.size()) {
                openStockGUI(player, os.shop, listings.get(slot));
            }
        }
    }

    private void handleDepositContinue(Player player, DepositSession ds) {
        ItemStack representative = null;
        List<ItemStack> depositedItems = new ArrayList<>();

        for (int i = 0; i < LISTING_SLOTS; i++) {
            ItemStack item = ds.inventory.getItem(i);
            if (item == null || item.getType() == Material.AIR) continue;
            if (representative == null) {
                representative = item.clone();
            } else if (!representative.isSimilar(item)) {
                player.sendMessage(Component.text("[PlayerShop] ", NamedTextColor.GOLD)
                    .append(Component.text(
                        "All deposited items must be the same type.", NamedTextColor.RED)));
                return;
            }
            depositedItems.add(item.clone());
        }

        if (representative == null) {
            player.sendMessage(Component.text("[PlayerShop] ", NamedTextColor.GOLD)
                .append(Component.text(
                    "Add at least one item before continuing.", NamedTextColor.RED)));
            return;
        }

        for (int i = 0; i < LISTING_SLOTS; i++) ds.inventory.setItem(i, null);
        openPricingGUI(player, ds.shop, representative, depositedItems);
    }

    private void handleDepositCancel(Player player, DepositSession ds) {
        returnDepositItems(player, ds);
        sessions.remove(player.getUniqueId());
        player.closeInventory();
    }

    private void handlePricingClick(Player player, PricingSession ps, int slot) {
        switch (slot) {
            case PR_DEC_100 -> adjustPrice(ps, -10000);
            case PR_DEC_10  -> adjustPrice(ps, -1000);
            case PR_DEC_1   -> adjustPrice(ps, -100);
            case PR_DEC_010 -> adjustPrice(ps, -10);
            case PR_INC_010 -> adjustPrice(ps, 10);
            case PR_INC_1   -> adjustPrice(ps, 100);
            case PR_INC_10  -> adjustPrice(ps, 1000);
            case PR_INC_100 -> adjustPrice(ps, 10000);
            case PR_CONFIRM -> handlePricingConfirm(player, ps);
            case PR_CANCEL  -> handlePricingCancel(player, ps);
        }
    }

    private void adjustPrice(PricingSession ps, int deltaCents) {
        ps.priceCents = Math.max(0, ps.priceCents + deltaCents);
        ps.inventory.setItem(PR_PRICE_DISPLAY, makePriceDisplay(ps.priceCents));
    }

    private void handlePricingConfirm(Player player, PricingSession ps) {
        for (Listing existing : ps.shop.getListings()) {
            if (existing.getTemplate().isSimilar(ps.depositItem)) {
                returnPricingItems(player, ps);
                sessions.remove(player.getUniqueId());
                player.closeInventory();
                player.sendMessage(Component.text("[PlayerShop] ", NamedTextColor.GOLD)
                    .append(Component.text(
                        "This item already has a listing. Click the existing listing to restock it.",
                        NamedTextColor.RED)));
                return;
            }
        }

        int stock = ps.depositTotal;
        ps.depositedItems.clear();

        Listing listing = new Listing(
            UUID.randomUUID(), ps.depositItem, ps.priceCents / 100.0, stock);
        ps.shop.addListing(listing);

        plugin.getStorage().saveShop(ps.shop);
        plugin.getHolograms().createOrUpdate(ps.shop);

        openOwnerGUI(player, ps.shop);
        player.sendMessage(Component.text("[PlayerShop] ", NamedTextColor.GOLD)
            .append(Component.text("Listing created! ", NamedTextColor.GREEN))
            .append(Component.text(stock + " item(s) added to stock.", NamedTextColor.GRAY)));
    }

    private void handlePricingCancel(Player player, PricingSession ps) {
        returnPricingItems(player, ps);
        sessions.remove(player.getUniqueId());
        player.closeInventory();
    }

    private void handleStockClose(Player player, StockSession ss) {
        int count = 0;
        for (int i = 0; i < LISTING_SLOTS; i++) {
            ItemStack item = ss.inventory.getItem(i);
            if (item == null || item.getType() == Material.AIR) continue;
            if (ss.listing.getTemplate().isSimilar(item)) {
                count += item.getAmount();
            } else {
                giveOrDrop(player, item);
            }
        }
        ss.listing.setStock(count);
        plugin.getStorage().saveShop(ss.shop);
        plugin.getHolograms().createOrUpdate(ss.shop);
    }

    private void handleRemoveListing(Player player, StockSession ss) {
        int currentStock = 0;
        for (int i = 0; i < LISTING_SLOTS; i++) {
            ItemStack item = ss.inventory.getItem(i);
            if (item == null || item.getType() == Material.AIR) continue;
            if (ss.listing.getTemplate().isSimilar(item)) currentStock += item.getAmount();
        }
        if (currentStock > 0) {
            player.sendMessage(Component.text("[PlayerShop] ", NamedTextColor.GOLD)
                .append(Component.text(
                    "Withdraw all stock before removing this listing.", NamedTextColor.RED)));
            return;
        }
        ss.shop.removeListing(ss.listing.getId());
        plugin.getStorage().saveShop(ss.shop);
        plugin.getHolograms().createOrUpdate(ss.shop);
        openOwnerGUI(player, ss.shop);
        player.sendMessage(Component.text("[PlayerShop] ", NamedTextColor.GOLD)
            .append(Component.text("Listing removed.", NamedTextColor.GREEN)));
    }

    private void handleAmountClick(Player player, AmountSession as, int slot) {
        if (slot == AM_BACK) {
            openBuyerGUI(player, as.shop);
            return;
        }
        int maxStack = as.listing.getTemplate().getMaxStackSize();
        int safeMax  = safeQuantity(player, as.listing, maxStack);
        int requested = switch (slot) {
            case AM_BUY_1          -> 1;
            case AM_BUY_8          -> 8;
            case AM_BUY_16         -> 16;
            case AM_BUY_32         -> 32;
            case AM_BUY_MAX_STACK  -> maxStack;
            case AM_BUY_MAX_AFFORD -> safeMax;
            default -> 0;
        };
        if (requested <= 0) return;
        int qty = safeQuantity(player, as.listing, requested);
        if (qty <= 0) {
            sendCannotPurchase(player, as.listing);
            return;
        }
        if (executePurchase(player, as.shop, as.listing, qty)) {
            openBuyerGUI(player, as.shop);
        }
    }

    private void handleConfirmPurchase(Player player, ConfirmSession cs) {
        int qty = safeQuantity(player, cs.listing, cs.quantity);
        if (qty <= 0) {
            sendCannotPurchase(player, cs.listing);
        } else {
            executePurchase(player, cs.shop, cs.listing, qty);
        }
        openBuyerGUI(player, cs.shop);
    }

    // ── Purchase flow ─────────────────────────────────────────────────────────

    private boolean executePurchase(Player buyer, PlayerShop shop, Listing listing, int qty) {
        if (qty <= 0 || listing.getStock() < qty) return false;

        double pricePerItem = listing.getPricePerItem();
        double totalCost    = pricePerItem * qty;

        if (!plugin.getEconomy().withdraw(buyer.getUniqueId(), totalCost)) {
            buyer.sendMessage(Component.text("[PlayerShop] ", NamedTextColor.GOLD)
                .append(Component.text("Transaction failed. Please try again.", NamedTextColor.RED)));
            return false;
        }

        ItemStack toGive = listing.getTemplate();
        toGive.setAmount(qty);
        Map<Integer, ItemStack> overflow = buyer.getInventory().addItem(toGive);
        if (!overflow.isEmpty()) {
            plugin.getEconomy().deposit(buyer.getUniqueId(), totalCost);
            for (ItemStack drop : overflow.values())
                buyer.getWorld().dropItemNaturally(buyer.getLocation(), drop);
            buyer.sendMessage(Component.text("[PlayerShop] ", NamedTextColor.GOLD)
                .append(Component.text("Failed to give item(s). Payment refunded.", NamedTextColor.RED)));
            return false;
        }

        listing.setStock(listing.getStock() - qty);
        plugin.getEconomy().deposit(shop.getOwnerUuid(), totalCost);
        plugin.getStorage().saveShop(shop);

        buyer.sendMessage(Component.text("[PlayerShop] ", NamedTextColor.GOLD)
            .append(Component.text(
                String.format("Purchased %d item(s) for $%.2f.", qty, totalCost),
                NamedTextColor.GREEN)));

        Player owner = plugin.getServer().getPlayer(shop.getOwnerUuid());
        if (owner != null && owner.isOnline()) {
            owner.sendMessage(Component.text("[PlayerShop] ", NamedTextColor.GOLD)
                .append(Component.text(
                    String.format("%s bought %d item(s) for $%.2f.", buyer.getName(), qty, totalCost),
                    NamedTextColor.GRAY)));
            if (listing.getStock() == 0) {
                String itemName = listing.getTemplate().getType().name()
                    .toLowerCase().replace('_', ' ');
                owner.sendMessage(Component.text("[PlayerShop] ", NamedTextColor.GOLD)
                    .append(Component.text("Your ", NamedTextColor.RED))
                    .append(Component.text(itemName, NamedTextColor.WHITE))
                    .append(Component.text(" listing is now out of stock!", NamedTextColor.RED)));
                owner.playSound(owner.getLocation(), Sound.UI_TOAST_OUT, 1.0f, 0.8f);
            }
        }
        return true;
    }

    // ── Item return helpers ───────────────────────────────────────────────────

    private void returnDepositItems(Player player, DepositSession ds) {
        for (int i = 0; i < LISTING_SLOTS; i++) {
            ItemStack item = ds.inventory.getItem(i);
            if (item == null || item.getType() == Material.AIR) continue;
            ds.inventory.setItem(i, null);
            giveOrDrop(player, item);
        }
    }

    private void returnPricingItems(Player player, PricingSession ps) {
        for (ItemStack item : ps.depositedItems) {
            if (item == null || item.getType() == Material.AIR) continue;
            giveOrDrop(player, item.clone());
        }
        ps.depositedItems.clear();
    }

    private void giveOrDrop(Player player, ItemStack item) {
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        for (ItemStack drop : overflow.values())
            player.getWorld().dropItemNaturally(player.getLocation(), drop);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private int inventoryCapacity(Player player, ItemStack template) {
        int maxStack = template.getMaxStackSize();
        int total = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack == null)                  total += maxStack;
            else if (stack.isSimilar(template)) total += maxStack - stack.getAmount();
        }
        return total;
    }

    private int safeQuantity(Player player, Listing listing, int requested) {
        int maxStack = listing.getTemplate().getMaxStackSize();
        int qty = Math.min(requested, maxStack);
        qty = Math.min(qty, listing.getStock());
        qty = Math.min(qty, inventoryCapacity(player, listing.getTemplate()));
        double ppi = listing.getPricePerItem();
        if (ppi > 0) {
            qty = Math.min(qty,
                (int) Math.floor(plugin.getEconomy().getBalance(player.getUniqueId()) / ppi));
        }
        return Math.max(0, qty);
    }

    private void sendCannotPurchase(Player player, Listing listing) {
        if (listing.getStock() <= 0) {
            player.sendMessage(Component.text("[PlayerShop] ", NamedTextColor.GOLD)
                .append(Component.text("This item is out of stock.", NamedTextColor.RED)));
        } else if (!plugin.getEconomy().hasBalance(player.getUniqueId(), listing.getPricePerItem())) {
            player.sendMessage(Component.text("[PlayerShop] ", NamedTextColor.GOLD)
                .append(Component.text("You cannot afford this item.", NamedTextColor.RED)));
        } else {
            player.sendMessage(Component.text("[PlayerShop] ", NamedTextColor.GOLD)
                .append(Component.text("Your inventory is full.", NamedTextColor.RED)));
        }
    }

    private void sendStockReject(Player player, Listing listing) {
        player.sendMessage(Component.text("[PlayerShop] ", NamedTextColor.GOLD)
            .append(Component.text(
                "Only " + prettyName(listing) + " can be deposited here.", NamedTextColor.RED)));
    }

    private String prettyName(Listing listing) {
        String raw = listing.getTemplate().getType().name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }

    private Inventory sessionInventory(Session s) {
        return switch (s) {
            case OwnerSession os   -> os.inventory;
            case DepositSession ds -> ds.inventory;
            case PricingSession ps -> ps.inventory;
            case BuyerSession bs   -> bs.inventory;
            case StockSession ss   -> ss.inventory;
            case AmountSession as  -> as.inventory;
            case ConfirmSession cs -> cs.inventory;
        };
    }

    // ── Item builders ─────────────────────────────────────────────────────────

    private ItemStack makeListingItem(Listing listing) {
        ItemStack it = listing.getTemplate();
        ItemMeta m = it.getItemMeta();
        String shortId = listing.getId().toString().substring(0, 8);
        m.lore(List.of(
            colored(String.format("$%.2f each", listing.getPricePerItem()), NamedTextColor.GOLD),
            plain("Stock: " + listing.getStock()),
            colored("Click to manage stock", NamedTextColor.AQUA),
            plain("ID: " + shortId)
        ));
        it.setItemMeta(m);
        return it;
    }

    private ItemStack makeBuyerListingItem(Listing listing) {
        ItemStack it = listing.getTemplate();
        ItemMeta m = it.getItemMeta();
        List<Component> lore = new ArrayList<>();
        lore.add(colored(String.format("$%.2f each", listing.getPricePerItem()), NamedTextColor.GOLD));
        lore.add(plain("Stock: " + listing.getStock()));
        if (listing.getStock() > 0) {
            lore.add(colored("Left-click: buy 1", NamedTextColor.GREEN));
            lore.add(colored("Right-click: select amount", NamedTextColor.AQUA));
            lore.add(colored("Shift-click: buy 1 stack", NamedTextColor.GREEN));
        } else {
            lore.add(colored("Out of stock", NamedTextColor.RED));
        }
        m.lore(lore);
        it.setItemMeta(m);
        return it;
    }

    private ItemStack makeAmountPreview(Listing listing) {
        ItemStack it = listing.getTemplate();
        ItemMeta m = it.getItemMeta();
        m.lore(List.of(
            colored(String.format("$%.2f each", listing.getPricePerItem()), NamedTextColor.GOLD),
            plain("Stock: " + listing.getStock()),
            colored("Select an amount below.", NamedTextColor.AQUA)
        ));
        it.setItemMeta(m);
        return it;
    }

    private ItemStack makeConfirmPreview(ConfirmSession cs) {
        ItemStack it = cs.listing.getTemplate();
        it.setAmount(cs.quantity);
        ItemMeta m = it.getItemMeta();
        m.lore(List.of(
            colored(String.format("Quantity: %d", cs.quantity), NamedTextColor.WHITE),
            colored(String.format("$%.2f each", cs.listing.getPricePerItem()), NamedTextColor.GOLD),
            colored(String.format("Total: $%.2f", cs.totalCost), NamedTextColor.YELLOW),
            plain("Click Finish to confirm."),
            plain("Click Cancel to go back.")
        ));
        it.setItemMeta(m);
        return it;
    }

    private ItemStack makeRemoveListingButton() {
        ItemStack it = new ItemStack(Material.BARRIER);
        ItemMeta m = it.getItemMeta();
        m.displayName(colored("Remove Listing", NamedTextColor.RED).decoration(TextDecoration.BOLD, true));
        m.lore(List.of(plain("Stock must be 0 to remove.")));
        it.setItemMeta(m);
        return it;
    }

    private ItemStack makeAutoConfirmToggle(boolean enabled) {
        ItemStack it = new ItemStack(enabled ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE);
        ItemMeta m = it.getItemMeta();
        m.displayName(colored("Auto-confirm Stack Purchases",
            enabled ? NamedTextColor.GREEN : NamedTextColor.GRAY).decoration(TextDecoration.BOLD, true));
        m.lore(List.of(
            plain(enabled ? "ON — Shift-click buys immediately." : "OFF — Shift-click opens confirmation."),
            plain("When enabled, shift-click purchases happen immediately.")
        ));
        it.setItemMeta(m);
        return it;
    }

    private ItemStack makeAmountButton(String label, int requested,
                                       int maxStack, int maxBuyable, double ppi) {
        int effectiveReq = Math.min(requested, maxStack);
        int safe         = Math.min(effectiveReq, maxBuyable);
        boolean disabled = safe <= 0;
        boolean capped   = safe > 0 && safe < effectiveReq;

        Material mat;
        NamedTextColor nameColor;
        if (disabled) {
            mat = Material.RED_STAINED_GLASS_PANE;
            nameColor = NamedTextColor.RED;
        } else if (capped) {
            mat = Material.YELLOW_STAINED_GLASS_PANE;
            nameColor = NamedTextColor.YELLOW;
        } else {
            mat = Material.GREEN_TERRACOTTA;
            nameColor = NamedTextColor.GREEN;
        }

        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.displayName(colored(label, nameColor).decoration(TextDecoration.BOLD, true));
        List<Component> lore = new ArrayList<>();
        if (!disabled) {
            lore.add(colored(String.format("Total: $%.2f", ppi * safe), NamedTextColor.GOLD));
            if (capped) lore.add(colored("Only " + safe + " available", NamedTextColor.YELLOW));
        } else {
            lore.add(colored("Not available", NamedTextColor.RED));
        }
        m.lore(lore);
        it.setItemMeta(m);
        return it;
    }

    private ItemStack makePlaceholder() {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta m = it.getItemMeta();
        m.displayName(colored("No listings yet.", NamedTextColor.YELLOW));
        m.lore(List.of(plain("Click Sell to add items.")));
        it.setItemMeta(m);
        return it;
    }

    private ItemStack makeBuyerPlaceholder() {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta m = it.getItemMeta();
        m.displayName(colored("No items for sale.", NamedTextColor.YELLOW));
        m.lore(List.of(plain("Check back later!")));
        it.setItemMeta(m);
        return it;
    }

    private ItemStack makeSellButton() {
        ItemStack it = new ItemStack(Material.EMERALD);
        ItemMeta m = it.getItemMeta();
        m.displayName(colored("Sell", NamedTextColor.GREEN).decoration(TextDecoration.BOLD, true));
        m.lore(List.of(plain("Create a new listing.")));
        it.setItemMeta(m);
        return it;
    }

    private ItemStack makeFinishButton() {
        ItemStack it = new ItemStack(Material.LIME_CONCRETE);
        ItemMeta m = it.getItemMeta();
        m.displayName(colored("Finish", NamedTextColor.GREEN).decoration(TextDecoration.BOLD, true));
        m.lore(List.of(plain("Click when you're done.")));
        it.setItemMeta(m);
        return it;
    }

    private ItemStack makeCancelButton() {
        ItemStack it = new ItemStack(Material.BARRIER);
        ItemMeta m = it.getItemMeta();
        m.displayName(colored("Cancel", NamedTextColor.RED).decoration(TextDecoration.BOLD, true));
        m.lore(List.of(plain("Return all items and close.")));
        it.setItemMeta(m);
        return it;
    }

    private ItemStack makeBackButton() {
        ItemStack it = new ItemStack(Material.ARROW);
        ItemMeta m = it.getItemMeta();
        m.displayName(colored("Back", NamedTextColor.GRAY).decoration(TextDecoration.BOLD, true));
        m.lore(List.of(plain("Return to shop.")));
        it.setItemMeta(m);
        return it;
    }

    private ItemStack makeDepositHelpItem() {
        ItemStack it = new ItemStack(Material.COMPASS);
        ItemMeta m = it.getItemMeta();
        m.displayName(colored("How to Add Items", NamedTextColor.AQUA).decoration(TextDecoration.BOLD, true));
        m.lore(List.of(
            plain("Place items or stacks of the same kind."),
            plain("Then click Finish to continue.")
        ));
        it.setItemMeta(m);
        return it;
    }

    private ItemStack makeItemPreview(PricingSession ps) {
        ItemStack it = ps.depositItem.clone();
        ItemMeta m = it.getItemMeta();
        m.lore(List.of(plain("Total deposited: " + ps.depositTotal)));
        it.setItemMeta(m);
        return it;
    }

    private ItemStack makePriceDisplay(int priceCents) {
        ItemStack it = new ItemStack(Material.GOLD_INGOT);
        ItemMeta m = it.getItemMeta();
        m.displayName(colored(String.format("$%.2f per item", priceCents / 100.0), NamedTextColor.GOLD)
            .decoration(TextDecoration.BOLD, true));
        m.lore(List.of(plain("Use the buttons above to adjust.")));
        it.setItemMeta(m);
        return it;
    }

    private ItemStack makePriceButton(String label, boolean increase) {
        Material mat = increase ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE;
        NamedTextColor color = increase ? NamedTextColor.GREEN : NamedTextColor.RED;
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.displayName(colored(label, color).decoration(TextDecoration.BOLD, true));
        it.setItemMeta(m);
        return it;
    }

    private Component plain(String t) {
        return Component.text(t, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
    }

    private Component colored(String t, NamedTextColor c) {
        return Component.text(t, c).decoration(TextDecoration.ITALIC, false);
    }
}
