package dev.strangequark.stashlight.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

import java.util.Set;

public class Util {
    public static String getDimensionName(Level level) {
        return level.dimension().identifier().getPath();
    }

    public static boolean isValidSearchableContainer(BlockState state) {
        Block block = state.getBlock();

        if (!(block instanceof EntityBlock)) {
            return false;
        }

        return !(block instanceof EnderChestBlock || block instanceof EnchantingTableBlock || block instanceof BeaconBlock);
    }

    public static BlockPos getCanonicalPos(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ChestBlock chest)) {
            return pos;
        }

        // Ask the vanilla ChestBlock to resolve the inventory. This is our Source of Truth.
        var inv = ChestBlock.getContainer(chest, state, level, pos, true);

        if (inv instanceof CompoundContainer di) {
            try {
                // We use reflection to find the 'first' half of the DoubleInventory.
                // This aligns our database key with Minecraft's internal 'Master' half.
                var f = CompoundContainer.class.getDeclaredField("container1");
                f.setAccessible(true);
                var first = f.get(di);
                if (first instanceof BlockEntity be) {
                    return be.getBlockPos();
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return pos;
    }

    public static Set<BlockPos> resolveContainerPositions(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);

        if (!(state.getBlock() instanceof EntityBlock)) {
            return Set.of(pos);
        }

        if (state.getBlock() instanceof ChestBlock) {
            ChestType type = state.getValue(ChestBlock.TYPE);

            if (type == ChestType.SINGLE) {
                return Set.of(pos);
            }

            Direction facing = state.getValue(ChestBlock.FACING);
            Direction offset = type == ChestType.LEFT ? facing.getClockWise() : facing.getCounterClockWise();
            BlockPos other = pos.relative(offset);

            if (level.getBlockState(other).getBlock() instanceof ChestBlock) {
                return Set.of(pos, other);
            }
        }

        return Set.of(pos);
    }
}
