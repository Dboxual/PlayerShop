package com.playershop.commands;

import com.playershop.PlayerShopPlugin;
import com.playershop.data.Shop;
import com.playershop.util.ChestUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Optional;

public class ShopAdminCommand implements CommandExecutor {

    private final PlayerShopPlugin plugin;

    public ShopAdminCommand(PlayerShopPlugin plugin) {
        this.plugin = plugin;
    }

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
            default -> sendHelp(sender);
        }
        return true;
    }

    private void handleRemove(Player player) {
        Block target = player.getTargetBlockExact(5);
        if (target == null || !(target.getBlockData() instanceof Chest)) {
            player.sendMessage(Component.text("Look at a shop chest.", NamedTextColor.RED));
            return;
        }
        Optional<Shop> shopOpt = plugin.getShopManager().getShopAt(ChestUtil.primaryLocation(target));
        if (shopOpt.isEmpty()) {
            player.sendMessage(Component.text("No shop on that chest.", NamedTextColor.RED));
            return;
        }
        Shop shop = shopOpt.get();
        plugin.getHolograms().delete(shop.getId());
        plugin.getShopManager().removeShop(shop.getId());
        plugin.getStorage().deleteShop(shop.getId());
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
        Optional<Shop> shopOpt = plugin.getShopManager().getShopAt(ChestUtil.primaryLocation(target));
        if (shopOpt.isEmpty()) {
            player.sendMessage(Component.text("No shop on that chest.", NamedTextColor.RED));
            return;
        }
        Shop shop = shopOpt.get();
        player.sendMessage(Component.text("─── Shop Info ───", NamedTextColor.GOLD));
        player.sendMessage(Component.text("Owner: ", NamedTextColor.GRAY)
            .append(Component.text(shop.getOwnerName(), NamedTextColor.YELLOW)));
        player.sendMessage(Component.text("Item:  ", NamedTextColor.GRAY)
            .append(shop.getSellItem() != null
                ? Component.text(shop.getSellItem().getType().name(), NamedTextColor.WHITE)
                : Component.text("not set", NamedTextColor.RED)));
        player.sendMessage(Component.text("Price: ", NamedTextColor.GRAY)
            .append(Component.text("$" + String.format("%.2f", shop.getPrice()), NamedTextColor.GREEN)));
        player.sendMessage(Component.text("ID:    ", NamedTextColor.GRAY)
            .append(Component.text(shop.getId().toString(), NamedTextColor.DARK_GRAY)));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("─── PlayerShop Admin ───", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/playershop reload", NamedTextColor.YELLOW)
            .append(Component.text(" — reload config & data", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/playershop remove", NamedTextColor.YELLOW)
            .append(Component.text(" — look at chest, force-delete shop", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/playershop info", NamedTextColor.YELLOW)
            .append(Component.text(" — look at chest, view shop details", NamedTextColor.GRAY)));
    }
}
