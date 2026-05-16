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
        if (args.length == 0) { sendHelp(sender); return true; }

        switch (args[0].toLowerCase()) {
            case "setprice" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(Component.text(
                        "Usage: /playershop setprice <amount>", NamedTextColor.YELLOW));
                    return true;
                }
                double price;
                try {
                    price = Double.parseDouble(args[1]);
                    if (price < 0) throw new NumberFormatException();
                } catch (NumberFormatException e) {
                    player.sendMessage(Component.text("Invalid price.", NamedTextColor.RED));
                    return true;
                }
                handleSetPrice(player, price);
            }
            case "remove" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
                    return true;
                }
                handleRemove(player);
            }
            case "reload" -> {
                if (!sender.hasPermission("playershop.admin")) {
                    sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
                    return true;
                }
                plugin.reload();
                sender.sendMessage(Component.text("PlayerShop reloaded.", NamedTextColor.GREEN));
            }
            default -> sendHelp(sender);
        }
        return true;
    }

    private void handleSetPrice(Player player, double price) {
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
        if (!shop.isOwner(player.getUniqueId()) && !player.hasPermission("playershop.admin")) {
            player.sendMessage(Component.text("That's not your shop.", NamedTextColor.RED));
            return;
        }
        shop.setPrice(price);
        plugin.getStorage().saveShop(shop);
        player.sendMessage(Component.text(
            "Price set to $" + String.format("%.2f", price) + ".", NamedTextColor.GREEN));
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
        if (!shop.isOwner(player.getUniqueId()) && !player.hasPermission("playershop.admin")) {
            player.sendMessage(Component.text("That's not your shop.", NamedTextColor.RED));
            return;
        }
        plugin.getShopManager().removeShop(shop.getId());
        plugin.getStorage().deleteShop(shop.getId());
        player.sendMessage(Component.text("Shop removed.", NamedTextColor.GREEN));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("─── PlayerShop ───", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/playershop setprice <amount>", NamedTextColor.YELLOW)
            .append(Component.text(" — look at chest, set price", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/playershop remove", NamedTextColor.YELLOW)
            .append(Component.text(" — look at chest, remove shop", NamedTextColor.GRAY)));
        if (sender.hasPermission("playershop.admin")) {
            sender.sendMessage(Component.text("/playershop reload", NamedTextColor.YELLOW)
                .append(Component.text(" — reload config", NamedTextColor.GRAY)));
        }
    }
}
