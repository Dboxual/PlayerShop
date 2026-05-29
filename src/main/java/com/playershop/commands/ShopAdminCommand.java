package com.playershop.commands;

import com.playershop.PlayerShopPlugin;
import com.playershop.data.Listing;
import com.playershop.data.PlayerShop;
import com.playershop.util.ChestUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public class ShopAdminCommand implements CommandExecutor {

    private final PlayerShopPlugin plugin;

    public ShopAdminCommand(PlayerShopPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("playershop.admin")) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) { sendHelp(sender); return true; }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                plugin.reload();
                sender.sendMessage(Component.text("PlayerShop reloaded.", NamedTextColor.GREEN));
            }
            case "remove" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
                    return true;
                }
                handleRemove(player);
            }
            case "info" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
                    return true;
                }
                handleInfo(player);
            }
            case "inspect" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
                    return true;
                }
                debugItem(player, player.getInventory().getItemInMainHand(), "HAND");
            }
            case "inspectlisting" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
                    return true;
                }
                handleDebugListing(player);
            }
            case "debug" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(Component.text(
                        "Usage: /playershop debug <hand|listing>", NamedTextColor.RED));
                    return true;
                }
                switch (args[1].toLowerCase()) {
                    case "hand"    -> debugItem(player,
                        player.getInventory().getItemInMainHand(), "HAND");
                    case "listing" -> handleDebugListing(player);
                    default        -> player.sendMessage(Component.text(
                        "Unknown target. Use: hand  or  listing", NamedTextColor.RED));
                }
            }
            default -> sendHelp(sender);
        }
        return true;
    }

    // ── Debug ─────────────────────────────────────────────────────────────────

    private void debugItem(Player player, ItemStack item, String label) {
        if (item == null || item.getType() == Material.AIR) {
            player.sendMessage(Component.text(
                "[Debug:" + label + "] No item.", NamedTextColor.RED));
            return;
        }

        player.sendMessage(Component.text(
            "─── Debug: " + label + " ───", NamedTextColor.AQUA));
        send(player, "Material : " + item.getType().name());
        send(player, "Amount   : " + item.getAmount());

        ItemMeta meta = item.getItemMeta();
        if (meta == null) { send(player, "ItemMeta : null"); return; }

        // Display name
        if (meta.hasDisplayName()) {
            send(player, "Name     : " + meta.displayName().toString());
        } else {
            send(player, "Name     : <none>");
        }

        // Lore
        List<Component> lore = meta.lore();
        if (lore != null && !lore.isEmpty()) {
            send(player, "Lore[" + lore.size() + "]:");
            for (int i = 0; i < lore.size(); i++)
                send(player, "  [" + i + "] " + lore.get(i).toString());
        } else {
            send(player, "Lore     : <none>");
        }

        // Custom model data
        send(player, "ModelData: " + (meta.hasCustomModelData()
            ? meta.getCustomModelData() : "<none>"));

        // Enchants
        if (!meta.getEnchants().isEmpty()) {
            for (var entry : meta.getEnchants().entrySet())
                send(player, "Enchant  : " + entry.getKey().getKey()
                    + " lvl " + entry.getValue());
        }

        // Item flags
        if (!meta.getItemFlags().isEmpty())
            send(player, "Flags    : " + meta.getItemFlags());

        // PDC — all keys
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Set<NamespacedKey> keys = pdc.getKeys();
        if (keys.isEmpty()) {
            player.sendMessage(Component.text(
                "PDC      : <EMPTY — no custom keys>", NamedTextColor.RED));
        } else {
            send(player, "PDC [" + keys.size() + " key(s)]:");
            for (NamespacedKey key : keys)
                send(player, "  " + key + " = " + readPdc(pdc, key));
        }

        // Specific check: totemaltars:totem_type
        NamespacedKey totemKey = new NamespacedKey("totemaltars", "totem_type");
        if (pdc.has(totemKey, PersistentDataType.STRING)) {
            player.sendMessage(Component.text(
                ">> totemaltars:totem_type = "
                    + pdc.get(totemKey, PersistentDataType.STRING),
                NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text(
                ">> totemaltars:totem_type = MISSING", NamedTextColor.RED));
        }

        // Mirror to console so it can be copied from server logs
        plugin.getLogger().info("[PlayerShop debug:" + label + "] "
            + item.getType() + " | PDC keys: " + keys
            + " | totemaltars:totem_type="
            + (pdc.has(totemKey, PersistentDataType.STRING)
                ? pdc.get(totemKey, PersistentDataType.STRING) : "MISSING"));
    }

    private void handleDebugListing(Player player) {
        Block target = player.getTargetBlockExact(5);
        if (target == null || !(target.getBlockData() instanceof Chest)) {
            player.sendMessage(Component.text("Look at a shop chest.", NamedTextColor.RED));
            return;
        }
        Optional<PlayerShop> shopOpt = plugin.getShopManager()
            .getPlayerShopByChest(ChestUtil.primaryLocation(target));
        if (shopOpt.isEmpty()) {
            player.sendMessage(Component.text("No shop on that chest.", NamedTextColor.RED));
            return;
        }
        PlayerShop shop = shopOpt.get();
        List<Listing> listings = shop.getListings();
        if (listings.isEmpty()) {
            player.sendMessage(Component.text(
                "No listings in this shop.", NamedTextColor.YELLOW));
            return;
        }
        for (int i = 0; i < listings.size(); i++) {
            player.sendMessage(Component.text(
                "─── Listing [" + i + "] stored template ───", NamedTextColor.GOLD));
            debugItem(player, listings.get(i).getTemplate(), "LISTING-" + i);
        }
    }

    /** Try common PDC types and return a readable "TYPE:value" string. */
    private String readPdc(PersistentDataContainer pdc, NamespacedKey key) {
        if (pdc.has(key, PersistentDataType.STRING))
            return "STRING:" + pdc.get(key, PersistentDataType.STRING);
        if (pdc.has(key, PersistentDataType.INTEGER))
            return "INT:" + pdc.get(key, PersistentDataType.INTEGER);
        if (pdc.has(key, PersistentDataType.DOUBLE))
            return "DOUBLE:" + pdc.get(key, PersistentDataType.DOUBLE);
        if (pdc.has(key, PersistentDataType.FLOAT))
            return "FLOAT:" + pdc.get(key, PersistentDataType.FLOAT);
        if (pdc.has(key, PersistentDataType.LONG))
            return "LONG:" + pdc.get(key, PersistentDataType.LONG);
        if (pdc.has(key, PersistentDataType.SHORT))
            return "SHORT:" + pdc.get(key, PersistentDataType.SHORT);
        if (pdc.has(key, PersistentDataType.BYTE))
            return "BYTE:" + pdc.get(key, PersistentDataType.BYTE);
        if (pdc.has(key, PersistentDataType.BOOLEAN))
            return "BOOLEAN:" + pdc.get(key, PersistentDataType.BOOLEAN);
        return "<complex/array type>";
    }

    // ── Existing handlers ─────────────────────────────────────────────────────

    private void handleRemove(Player player) {
        Block target = player.getTargetBlockExact(5);
        if (target == null || !(target.getBlockData() instanceof Chest)) {
            player.sendMessage(Component.text("Look at a shop chest.", NamedTextColor.RED));
            return;
        }
        Optional<PlayerShop> shopOpt = plugin.getShopManager()
            .getPlayerShopByChest(ChestUtil.primaryLocation(target));
        if (shopOpt.isEmpty()) {
            player.sendMessage(Component.text("No shop on that chest.", NamedTextColor.RED));
            return;
        }
        PlayerShop shop = shopOpt.get();
        plugin.getHolograms().delete(shop.getOwnerUuid());
        plugin.getShopManager().removeShop(shop.getOwnerUuid());
        plugin.getStorage().deleteShop(shop.getOwnerUuid());
        player.sendMessage(Component.text("Removed shop owned by ", NamedTextColor.GREEN)
            .append(Component.text(shop.getOwnerName(), NamedTextColor.YELLOW))
            .append(Component.text(".", NamedTextColor.GREEN)));
    }

    private void handleInfo(Player player) {
        Block target = player.getTargetBlockExact(5);
        if (target == null || !(target.getBlockData() instanceof Chest)) {
            player.sendMessage(Component.text("Look at a shop chest.", NamedTextColor.RED));
            return;
        }
        Optional<PlayerShop> shopOpt = plugin.getShopManager()
            .getPlayerShopByChest(ChestUtil.primaryLocation(target));
        if (shopOpt.isEmpty()) {
            player.sendMessage(Component.text("No shop on that chest.", NamedTextColor.RED));
            return;
        }
        PlayerShop shop = shopOpt.get();
        player.sendMessage(Component.text("─── Shop Info ───", NamedTextColor.GOLD));
        player.sendMessage(Component.text("Owner:    ", NamedTextColor.GRAY)
            .append(Component.text(shop.getOwnerName(), NamedTextColor.YELLOW)));
        player.sendMessage(Component.text("Chests:   ", NamedTextColor.GRAY)
            .append(Component.text(String.valueOf(shop.getChests().size()), NamedTextColor.WHITE)));
        player.sendMessage(Component.text("Listings: ", NamedTextColor.GRAY)
            .append(Component.text(String.valueOf(shop.getListings().size()), NamedTextColor.WHITE)));
        player.sendMessage(Component.text("Owner ID: ", NamedTextColor.GRAY)
            .append(Component.text(shop.getOwnerUuid().toString(), NamedTextColor.DARK_GRAY)));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("─── PlayerShop Admin ───", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/playershop reload", NamedTextColor.YELLOW)
            .append(Component.text(" — reload config & data", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/playershop remove", NamedTextColor.YELLOW)
            .append(Component.text(" — look at chest, force-delete entire player shop", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/playershop info", NamedTextColor.YELLOW)
            .append(Component.text(" — look at chest, view shop details", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/playershop inspect", NamedTextColor.YELLOW)
            .append(Component.text(" — print full item data for held item (PDC, lore, etc.)", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/playershop inspectlisting", NamedTextColor.YELLOW)
            .append(Component.text(" — look at chest, inspect stored listing templates", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/playershop debug hand", NamedTextColor.YELLOW)
            .append(Component.text(" — alias for inspect", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/playershop debug listing", NamedTextColor.YELLOW)
            .append(Component.text(" — alias for inspectlisting", NamedTextColor.GRAY)));
    }

    private void send(Player player, String text) {
        player.sendMessage(Component.text(text, NamedTextColor.GRAY));
    }
}
