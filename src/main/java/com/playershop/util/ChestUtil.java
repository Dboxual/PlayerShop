package com.playershop.util;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Chest;

public final class ChestUtil {

    private ChestUtil() {}

    public static Location primaryLocation(Block block) {
        BlockData data = block.getBlockData();
        if (!(data instanceof Chest chestData)) return block.getLocation();
        if (chestData.getType() == Chest.Type.SINGLE) return block.getLocation();

        BlockFace facing = chestData.getFacing();
        BlockFace toOther = chestData.getType() == Chest.Type.LEFT
            ? rotateCW(facing) : rotateCCW(facing);
        Block other = block.getRelative(toOther);
        return lowerOf(block.getLocation(), other.getLocation());
    }

    private static BlockFace rotateCW(BlockFace face) {
        return switch (face) {
            case NORTH -> BlockFace.EAST;
            case EAST  -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.WEST;
            case WEST  -> BlockFace.NORTH;
            default    -> face;
        };
    }

    private static BlockFace rotateCCW(BlockFace face) {
        return switch (face) {
            case NORTH -> BlockFace.WEST;
            case WEST  -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.EAST;
            case EAST  -> BlockFace.NORTH;
            default    -> face;
        };
    }

    public static Location hologramLocation(Block block) {
        BlockData data = block.getBlockData();
        if (!(data instanceof Chest chestData) || chestData.getType() == Chest.Type.SINGLE) {
            return block.getLocation().add(0.5, 2.5, 0.5);
        }
        BlockFace facing = chestData.getFacing();
        BlockFace toOther = chestData.getType() == Chest.Type.LEFT
            ? rotateCW(facing) : rotateCCW(facing);
        Block other = block.getRelative(toOther);
        double cx = (block.getX() + other.getX()) / 2.0 + 0.5;
        double cz = (block.getZ() + other.getZ()) / 2.0 + 0.5;
        return new Location(block.getWorld(), cx, block.getY() + 2.5, cz);
    }

    private static Location lowerOf(Location a, Location b) {
        if (a.getBlockX() != b.getBlockX()) return a.getBlockX() < b.getBlockX() ? a : b;
        return a.getBlockZ() < b.getBlockZ() ? a : b;
    }
}
