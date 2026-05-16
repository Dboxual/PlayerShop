package com.playershop.gui;

import com.playershop.PlayerShopPlugin;
import com.playershop.data.Shop;
import com.playershop.util.ChestUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.stream.Collectors;

public class ShopGUI implements Listener {

    // ── Session types ─────────────────────────────────────────────────────────
    private sealed interface Session permits PriceSession, BuyerSession {}

    private static final class PriceSession implements Session {
        final Shop shop; final Inventory inventory; double pending;
        PriceSession(Shop shop, Inventory inv, double init) {
            this.shop = shop; this.inventory = inv; this.pending = init;
        }
    }

    private static final class BuyerSession implements Session {
        final Shop shop; final Inventory inventory;
        int amount; boolean purchased;
        BuyerSession(Shop shop, Inventory inv, int init) {
            this.shop = shop; this.inventory = inv; this.amount = init;
        }
    }

    /** Tracks an owner who has the physical shop chest open for setup. */
    private record PendingSetup(Shop shop) {}

    // ── Price GUI slots (27-slot) ─────────────────────────────────────────────
    //  Row 0: [-100][-10][-1][-0.10][ITEM][+0.10][+1][+10][+100]
    //  Row 1: [pad×4][PRICE_DISPLAY][pad×4]
    //  Row 2: [RESET][pad×7][CONFIRM]
    private static final int PE_M100 = 0, PE_M10 = 1, PE_M1 = 2, PE_MD1 = 3;
    private static final int PE_ITEM = 4;
    private static final int PE_PD1  = 5, PE_P1  = 6, PE_P10 = 7, PE_P100 = 8;
    private static final int PE_DISP    = 13;
    private static final int PE_RESET   = 18;
    private static final int PE_CONFIRM = 26;

    // ── Buyer GUI slots (27-slot) ─────────────────────────────────────────────
    //  Row 0: [-stack][-8][-1][pad][ITEM][pad][+1][+8][+stack]
    //  Row 1: [pad×4][PRICE_TOTAL][pad×4]
    //  Row 2: [pad×4][CONFIRM][pad×4]
    private static final int BA_DSTACK = 0, BA_D8 = 1, BA_D1 = 2;
    private static final int BA_ITEM   = 4;
    private static final int BA_I1     = 6, BA_I8 = 7, BA_ISTACK = 8;
    private static final int BA_PRICE  = 13;
    private static final int BA_CONFIRM = 22;

    private static final int GUI_SIZE = 27;

    private final PlayerShopPlugin plugin;
    private final Map<UUID, Session>      sessions      = new HashMap<>();
    private final Map<UUID, PendingSetup> pendingSetups = new HashMap<>();

    public ShopGUI(PlayerShopPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Entry points ──────────────────────────────────────────────────────────

    /**
     * Opens the physical shop chest for the owner to populate with items.
     * Called from ShopInteractListener on shift+shovel click.
     */
    public void enterSetupMode(Player player, Shop shop) {
        Block block = shop.getChestLocation().getBlock();
        if (!(block.getState() instanceof org.bukkit.block.Chest cs)) {
            player.sendMessage(Component.text("Chest not found.", NamedTextColor.RED));
            return;
        }
        pendingSetups.put(player.getUniqueId(), new PendingSetup(shop));
        player.openInventory(cs.getInventory());
    }

    /**
     * Opens the buyer purchase GUI for a non-owner.
     * Called from ShopInteractListener on regular right-click.
     */
    public void open(Player player, Shop shop) {
        Inventory inv = Bukkit.createInventory(null, GUI_SIZE,
            Component.text(shop.getOwnerName() + "'s Shop", NamedTextColor.DARK_AQUA));
        BuyerSession bs = new BuyerSession(shop, inv, 1);
        renderBuyerGUI(bs);
        sessions.put(player.getUniqueId(), bs);
        player.openInventory(inv);
    }

    private void openPriceGUI(Player player, Shop shop) {
        Inventory inv = Bukkit.createInventory(null, GUI_SIZE,
            Component.text("Set Price (per stack)", NamedTextColor.GOLD));
        PriceSession ps = new PriceSession(shop, inv, shop.getPrice());
        renderPriceGUI(ps);
        sessions.put(player.getUniqueId(), ps);
        player.openInventory(inv);
    }

    // ── GUI renderers ─────────────────────────────────────────────────────────

    private void renderPriceGUI(PriceSession ps) {
        Inventory inv = ps.inventory;
        fill(inv, makeFiller());
        // Row 0: red decrease panes | item preview | green increase panes
        inv.setItem(PE_M100, makePricePane(-100));
        inv.setItem(PE_M10,  makePricePane(-10));
        inv.setItem(PE_M1,   makePricePane(-1));
        inv.setItem(PE_MD1,  makePricePane(-0.10));
        if (ps.shop.getSellItem() != null) inv.setItem(PE_ITEM, makeItemPreview(ps.shop));
        inv.setItem(PE_PD1,  makePricePane(+0.10));
        inv.setItem(PE_P1,   makePricePane(+1));
        inv.setItem(PE_P10,  makePricePane(+10));
        inv.setItem(PE_P100, makePricePane(+100));
        // Row 1: price display centered
        inv.setItem(PE_DISP, makePriceDisplay(ps.pending));
        // Row 2: reset bottom-left, confirm bottom-right
        inv.setItem(PE_RESET,   makeResetButton());
        inv.setItem(PE_CONFIRM, makeConfirmButton("Confirm", true));
    }

    private void renderBuyerGUI(BuyerSession bs) {
        Inventory inv = bs.inventory;
        fill(inv, makeFiller());
        inv.setItem(BA_DSTACK, makeAdjustButton(-64, Material.RED_CONCRETE,    "-1 stack"));
        inv.setItem(BA_D8,     makeAdjustButton(-8,  Material.RED_WOOL,        "-8"));
        inv.setItem(BA_D1,     makeAdjustButton(-1,  Material.RED_STAINED_GLASS_PANE, "-1"));
        inv.setItem(BA_ITEM,   makeBuyerItemDisplay(bs));
        inv.setItem(BA_I1,     makeAdjustButton(+1,  Material.LIME_STAINED_GLASS_PANE, "+1"));
        inv.setItem(BA_I8,     makeAdjustButton(+8,  Material.LIME_WOOL,       "+8"));
        inv.setItem(BA_ISTACK, makeAdjustButton(+64, Material.LIME_CONCRETE,   "+1 stack"));
        inv.setItem(BA_PRICE,  makePriceTotalDisplay(bs));
        inv.setItem(BA_CONFIRM, makeConfirmButton("Buy " + bs.amount + " items", bs.amount > 0));
    }

    private void refreshBuyerGUI(BuyerSession bs) {
        bs.inventory.setItem(BA_ITEM,    makeBuyerItemDisplay(bs));
        bs.inventory.setItem(BA_PRICE,   makePriceTotalDisplay(bs));
        bs.inventory.setItem(BA_CONFIRM, makeConfirmButton("Buy " + bs.amount + " items", bs.amount > 0));
    }

    // ── Events ────────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Session session = sessions.get(player.getUniqueId());
        if (session == null) return;
        if (!event.getInventory().equals(sessionInventory(session))) return;

        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true); return;
        }
        int raw     = event.getRawSlot();
        int topSize = event.getView().getTopInventory().getSize();
        if (raw >= topSize && event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            event.setCancelled(true); return;
        }
        if (raw >= topSize) return;

        event.setCancelled(true);
        switch (session) {
            case PriceSession ps -> handlePriceClick(player, ps, raw);
            case BuyerSession  bs -> handleBuyerClick(player, bs, raw);
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

        // Check if owner closed the setup chest
        PendingSetup ps = pendingSetups.get(uuid);
        if (ps != null && isSetupChest(event.getInventory(), ps.shop())) {
            pendingSetups.remove(uuid);
            handleSetupChestClose(player, ps.shop());
            return;
        }

        // Handle custom GUI session
        Session session = sessions.get(uuid);
        if (session == null) return;
        if (!event.getInventory().equals(sessionInventory(session))) return;
        sessions.remove(uuid);
        // No navigation needed — both PriceSession and BuyerSession just close
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        sessions.remove(uuid);
        pendingSetups.remove(uuid);
    }

    // ── Click handlers ────────────────────────────────────────────────────────

    private void handlePriceClick(Player player, PriceSession ps, int slot) {
        switch (slot) {
            case PE_M100    -> applyDelta(ps, -100);
            case PE_M10     -> applyDelta(ps, -10);
            case PE_M1      -> applyDelta(ps, -1);
            case PE_MD1     -> applyDelta(ps, -0.10);
            case PE_PD1     -> applyDelta(ps, +0.10);
            case PE_P1      -> applyDelta(ps, +1);
            case PE_P10     -> applyDelta(ps, +10);
            case PE_P100    -> applyDelta(ps, +100);
            case PE_RESET   -> {
                ps.pending = 0;
                ps.inventory.setItem(PE_DISP, makePriceDisplay(0));
            }
            case PE_CONFIRM -> {
                boolean allowFree = plugin.getConfig().getBoolean("settings.allow-free-shops", false);
                if (!allowFree && ps.pending <= 0) {
                    player.sendMessage(Component.text("Price must be above $0.00.", NamedTextColor.RED));
                    return;
                }
                ps.shop.setPrice(ps.pending);
                plugin.getStorage().saveShop(ps.shop);
                plugin.getHolograms().createOrUpdate(ps.shop);
                player.sendMessage(Component.text("Shop ready! Price: ", NamedTextColor.GREEN)
                    .append(Component.text("$" + fmt(ps.pending) + " per stack", NamedTextColor.GOLD))
                    .append(Component.text(".", NamedTextColor.GREEN)));
                player.closeInventory();
            }
        }
    }

    private void handleBuyerClick(Player player, BuyerSession bs, int slot) {
        switch (slot) {
            case BA_DSTACK  -> adjustAmount(bs, -64, player);
            case BA_D8      -> adjustAmount(bs, -8,  player);
            case BA_D1      -> adjustAmount(bs, -1,  player);
            case BA_I1      -> adjustAmount(bs, +1,  player);
            case BA_I8      -> adjustAmount(bs, +8,  player);
            case BA_ISTACK  -> adjustAmount(bs, +64, player);
            case BA_CONFIRM -> executePurchase(player, bs);
        }
    }

    private void adjustAmount(BuyerSession bs, int delta, Player player) {
        int max = calcMax(player, bs.shop);
        int newAmt = bs.amount + delta;
        bs.amount = Math.max(1, newAmt);
        if (max > 0) bs.amount = Math.min(max, bs.amount);
        refreshBuyerGUI(bs);
    }

    // ── Setup chest close ─────────────────────────────────────────────────────

    private void handleSetupChestClose(Player player, Shop shop) {
        Block block = shop.getChestLocation().getBlock();
        if (!(block.getState() instanceof org.bukkit.block.Chest cs)) return;

        List<ItemStack> items = Arrays.stream(cs.getInventory().getContents())
            .filter(s -> s != null && s.getType() != Material.AIR)
            .collect(Collectors.toList());

        if (items.isEmpty()) {
            player.sendMessage(Component.text(
                "No items in the chest. Add the items you want to sell, then close the chest again.",
                NamedTextColor.RED));
            return;
        }

        ItemStack first = items.get(0);
        if (!items.stream().allMatch(s -> s.isSimilar(first))) {
            player.sendMessage(Component.text(
                "All items in the chest must be the same type! Remove mixed items, then close again.",
                NamedTextColor.RED));
            return;
        }

        ItemStack template = first.clone();
        template.setAmount(1);
        shop.setSellItem(template);
        plugin.getStorage().saveShop(shop);
        plugin.getHolograms().createOrUpdate(shop);

        player.sendMessage(Component.text("Item set to ", NamedTextColor.GREEN)
            .append(Component.text(friendlyName(template), NamedTextColor.YELLOW))
            .append(Component.text(". Now set the price per stack.", NamedTextColor.GREEN)));
        scheduleOpen(player, () -> openPriceGUI(player, shop));
    }

    // ── Purchase ──────────────────────────────────────────────────────────────

    private void executePurchase(Player player, BuyerSession bs) {
        Shop shop = bs.shop;
        int qty = bs.amount;

        if (qty < 1) {
            player.sendMessage(Component.text("Select an amount first.", NamedTextColor.RED));
            return;
        }
        if (!plugin.getEconomy().isAvailable()) {
            player.sendMessage(Component.text("Economy unavailable.", NamedTextColor.RED));
            return;
        }

        double pricePerItem = shop.getPrice() / 64.0;
        double total = Math.round(qty * pricePerItem * 100.0) / 100.0;

        if (!plugin.getEconomy().hasBalance(player.getUniqueId(), total)) {
            player.sendMessage(Component.text("Not enough money. Need $" + fmt(total) + ".", NamedTextColor.RED));
            return;
        }

        Block block = shop.getChestLocation().getBlock();
        if (!(block.getState() instanceof org.bukkit.block.Chest cs)) {
            player.sendMessage(Component.text("Shop chest not found.", NamedTextColor.RED));
            return;
        }
        Inventory chestInv = cs.getInventory();
        ItemStack sellItem = shop.getSellItem();

        int stock = countStock(chestInv, sellItem);
        if (stock < qty) {
            player.sendMessage(Component.text("Not enough stock. Available: " + stock + ".", NamedTextColor.RED));
            return;
        }
        if (countInventorySpace(player, sellItem) < qty) {
            player.sendMessage(Component.text("Not enough inventory space.", NamedTextColor.RED));
            return;
        }

        // Atomic: withdraw → remove from chest → give to buyer → pay owner
        if (!plugin.getEconomy().withdraw(player.getUniqueId(), total)) {
            player.sendMessage(Component.text("Payment failed.", NamedTextColor.RED));
            return;
        }

        int removed = 0;
        while (removed < qty) {
            if (!removeOneFromChest(chestInv, sellItem)) {
                double refund = Math.round((qty - removed) * pricePerItem * 100.0) / 100.0;
                plugin.getEconomy().deposit(player.getUniqueId(), refund);
                qty = removed;
                break;
            }
            removed++;
        }
        if (qty < 1) {
            plugin.getEconomy().deposit(player.getUniqueId(), total);
            player.sendMessage(Component.text("Could not retrieve items from chest. Refunded.", NamedTextColor.RED));
            return;
        }

        double actualTotal = Math.round(qty * pricePerItem * 100.0) / 100.0;
        ItemStack give = sellItem.clone();
        give.setAmount(qty);
        player.getInventory().addItem(give);
        plugin.getEconomy().deposit(shop.getOwnerUuid(), actualTotal);
        plugin.getHolograms().createOrUpdate(shop);

        bs.purchased = true;
        player.closeInventory();
        player.sendMessage(Component.text("Bought ", NamedTextColor.GREEN)
            .append(Component.text(qty + "× " + friendlyName(sellItem), NamedTextColor.YELLOW))
            .append(Component.text(" for $" + fmt(actualTotal) + ".", NamedTextColor.GREEN)));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);

        // Notify owner if online
        Player owner = Bukkit.getPlayer(shop.getOwnerUuid());
        if (owner != null && !owner.equals(player)) {
            owner.sendMessage(Component.text(player.getName() + " bought ", NamedTextColor.GRAY)
                .append(Component.text(qty + "× " + friendlyName(sellItem), NamedTextColor.YELLOW))
                .append(Component.text(" from your shop ", NamedTextColor.GRAY))
                .append(Component.text("+$" + fmt(actualTotal), NamedTextColor.GREEN)));
            owner.playSound(owner.getLocation(), Sound.ENTITY_VILLAGER_TRADE, 0.5f, 1.5f);
        }
    }

    // ── Chest / inventory helpers ─────────────────────────────────────────────

    private boolean isSetupChest(Inventory inv, Shop shop) {
        InventoryHolder holder = inv.getHolder();
        if (holder instanceof org.bukkit.block.Chest single) {
            return ChestUtil.primaryLocation(single.getBlock()).equals(shop.getChestLocation());
        }
        if (holder instanceof org.bukkit.block.DoubleChest dc) {
            InventoryHolder left  = dc.getLeftSide();
            InventoryHolder right = dc.getRightSide();
            if (left  instanceof org.bukkit.block.Chest lc &&
                ChestUtil.primaryLocation(lc.getBlock()).equals(shop.getChestLocation())) return true;
            if (right instanceof org.bukkit.block.Chest rc &&
                ChestUtil.primaryLocation(rc.getBlock()).equals(shop.getChestLocation())) return true;
        }
        return false;
    }

    private int getChestStock(Shop shop) {
        if (shop.getSellItem() == null) return 0;
        Block b = shop.getChestLocation().getBlock();
        if (!(b.getState() instanceof org.bukkit.block.Chest cs)) return 0;
        return countStock(cs.getInventory(), shop.getSellItem());
    }

    private int countStock(Inventory inv, ItemStack template) {
        int n = 0;
        for (ItemStack s : inv.getContents()) {
            if (s != null && s.isSimilar(template)) n += s.getAmount();
        }
        return n;
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
        double pricePerItem = shop.getPrice() / 64.0;
        int byBalance  = (int) Math.floor(plugin.getEconomy().getBalance(player.getUniqueId()) / pricePerItem);
        int byStock    = getChestStock(shop);
        int byInvSpace = countInventorySpace(player, shop.getSellItem());
        return Math.max(0, Math.min(byBalance, Math.min(byStock, byInvSpace)));
    }

    private void applyDelta(PriceSession ps, double delta) {
        ps.pending = Math.max(0.0, Math.round((ps.pending + delta) * 100.0) / 100.0);
        ps.inventory.setItem(PE_DISP, makePriceDisplay(ps.pending));
    }

    private void scheduleOpen(Player player, Runnable action) {
        Bukkit.getScheduler().runTask(plugin, (Runnable) () -> {
            if (player.isOnline()) action.run();
        });
    }

    private Inventory sessionInventory(Session s) {
        return switch (s) {
            case PriceSession ps -> ps.inventory;
            case BuyerSession bs -> bs.inventory;
        };
    }

    private String friendlyName(ItemStack item) {
        if (item == null) return "???";
        String[] words = item.getType().name().split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1).toLowerCase());
        }
        return sb.toString();
    }

    // ── Item factories ────────────────────────────────────────────────────────

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

    // Price GUI ----------------------------------------------------------------
    private ItemStack makePriceDisplay(double price) {
        ItemStack it = new ItemStack(price > 0 ? Material.GOLD_BLOCK : Material.IRON_BLOCK);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text("$" + fmt(price) + " / stack", NamedTextColor.GOLD)
            .decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
        m.lore(List.of(plain("Use the buttons to adjust")));
        it.setItemMeta(m);
        return it;
    }

    private ItemStack makePricePane(double delta) {
        boolean pos = delta > 0;
        ItemStack it = new ItemStack(pos ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE);
        ItemMeta m = it.getItemMeta();
        String label = Math.abs(delta) < 1
            ? String.format("%+.2f", delta)
            : String.format("%+.0f", delta);
        m.displayName(Component.text(label, pos ? NamedTextColor.GREEN : NamedTextColor.RED)
            .decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
        it.setItemMeta(m);
        return it;
    }

    private ItemStack makeItemPreview(Shop shop) {
        ItemStack it = shop.getSellItem().clone();
        it.setAmount(1);
        ItemMeta m = it.hasItemMeta() ? it.getItemMeta()
            : Bukkit.getItemFactory().getItemMeta(it.getType());
        m.displayName(colored(friendlyName(shop.getSellItem()), NamedTextColor.WHITE));
        m.lore(List.of(plain("Selling this item")));
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

    private ItemStack makeConfirmButton(String label, boolean active) {
        ItemStack it = new ItemStack(active ? Material.EMERALD_BLOCK : Material.BARRIER);
        ItemMeta m = it.getItemMeta();
        m.displayName(colored(label, active ? NamedTextColor.GREEN : NamedTextColor.RED)
            .decoration(TextDecoration.BOLD, true));
        it.setItemMeta(m);
        return it;
    }

    // Buyer GUI ----------------------------------------------------------------
    private ItemStack makeAdjustButton(int delta, Material mat, String label) {
        boolean pos = delta > 0;
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text(label, pos ? NamedTextColor.GREEN : NamedTextColor.RED)
            .decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
        it.setItemMeta(m);
        return it;
    }

    private ItemStack makeBuyerItemDisplay(BuyerSession bs) {
        Shop shop = bs.shop;
        if (shop.getSellItem() == null) return makeFiller();
        ItemStack it = shop.getSellItem().clone();
        it.setAmount(Math.min(bs.amount, it.getMaxStackSize()));
        ItemMeta m = it.hasItemMeta() ? it.getItemMeta()
            : Bukkit.getItemFactory().getItemMeta(it.getType());
        double pricePerItem = shop.getPrice() / 64.0;
        double total = Math.round(bs.amount * pricePerItem * 100.0) / 100.0;
        m.displayName(colored(bs.amount + "× " + friendlyName(shop.getSellItem()), NamedTextColor.WHITE)
            .decoration(TextDecoration.BOLD, true));
        m.lore(List.of(
            plain("Price per item: ").append(colored("$" + fmt(pricePerItem), NamedTextColor.GREEN)),
            plain("Total:          ").append(colored("$" + fmt(total),        NamedTextColor.GOLD)),
            Component.empty(),
            plain("Stock: ").append(colored(String.valueOf(getChestStock(shop)), NamedTextColor.YELLOW))
        ));
        it.setItemMeta(m);
        return it;
    }

    private ItemStack makePriceTotalDisplay(BuyerSession bs) {
        double pricePerItem = bs.shop.getPrice() / 64.0;
        double total = Math.round(bs.amount * pricePerItem * 100.0) / 100.0;
        ItemStack it = new ItemStack(Material.GOLD_INGOT);
        ItemMeta m = it.getItemMeta();
        m.displayName(colored("Total: $" + fmt(total), NamedTextColor.GOLD)
            .decoration(TextDecoration.BOLD, true));
        m.lore(List.of(
            plain(bs.amount + " items × $" + fmt(pricePerItem) + " each")
        ));
        it.setItemMeta(m);
        return it;
    }

    // ── Component helpers ─────────────────────────────────────────────────────
    private Component plain(String t) {
        return Component.text(t, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
    }
    private Component colored(String t, NamedTextColor c) {
        return Component.text(t, c).decoration(TextDecoration.ITALIC, false);
    }
    private String fmt(double v) { return String.format("%.2f", v); }
}
