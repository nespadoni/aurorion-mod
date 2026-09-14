package com.aurorion.limbo.exile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import org.jetbrains.annotations.Nullable;

/** Identidade da moldura colocada: independente de reload da config ou do destino do exilio. */
public record DoorFrame(BlockPos base, Direction facing, String block, String dimension) {
    public static final int WIDTH = 1;
    public static final int HEIGHT = 3;

    public BlockPos position(int width, int height) {
        return base.relative(facing.getClockWise(), width).above(height);
    }

    public static boolean edge(int width, int height) {
        return Math.abs(width) == WIDTH || height == 0 || height == HEIGHT;
    }

    public void write(CompoundTag tag) {
        tag.put("DoorPos", NbtUtils.writeBlockPos(base));
        tag.putString("DoorFacing", facing.getName());
        tag.putString("DoorBlock", block);
        tag.putString("DoorDimension", dimension);
    }

    @Nullable
    public static DoorFrame read(CompoundTag tag) {
        BlockPos base = NbtUtils.readBlockPos(tag, "DoorPos").orElse(null);
        Direction facing = Direction.byName(tag.getString("DoorFacing"));
        if (base == null || facing == null || facing.getAxis().isVertical()
                || !tag.contains("DoorBlock") || !tag.contains("DoorDimension")) return null;
        return new DoorFrame(base, facing, tag.getString("DoorBlock"), tag.getString("DoorDimension"));
    }
}
