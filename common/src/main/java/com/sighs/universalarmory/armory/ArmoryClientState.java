package com.sighs.universalarmory.armory;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

public final class ArmoryClientState {
    private static CompoundTag snapshot = new CompoundTag();
    private static BlockPos position;
    private static long generation;
    private static long requestId;
    private static CompoundTag options = new CompoundTag();

    private ArmoryClientState() {
    }

    public static void begin(BlockPos pos) {
        position = pos.immutable();
        snapshot = new CompoundTag();
        options = new CompoundTag();
        requestId++;
        generation++;
    }

    public static long nextRequest() {
        return ++requestId;
    }

    public static CompoundTag options() {
        return options.copy();
    }

    public static void configure(CompoundTag query) {
        options = query.copy();
    }

    public static void accept(BlockPos pos, long responseId, CompoundTag data) {
        if (pos.equals(position) && responseId == requestId) {
            snapshot = data.copy();
            snapshot.putLong("ResponseId", responseId);
            generation++;
        }
    }

    public static CompoundTag snapshot() {
        return snapshot.copy();
    }

    public static BlockPos position() {
        return position;
    }

    public static long generation() {
        return generation;
    }

    public static void close() {
        position = null;
        snapshot = new CompoundTag();
        generation++;
    }
}
