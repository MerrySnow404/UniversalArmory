package com.sighs.universalarmory.armory;

import com.sighs.universalarmory.spi.ArmoryNetworkBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;

public final class ArmoryNetwork {
    private static ArmoryNetworkBridge bridge;

    private ArmoryNetwork() {
    }

    public static void install(ArmoryNetworkBridge implementation) {
        if (bridge != null) {
            throw new IllegalStateException("Armory network already installed");
        }
        bridge = Objects.requireNonNull(implementation);
    }

    public static void open(ServerPlayer player, BlockPos pos) {
        network().open(player, pos);
    }

    public static void request(String action, int slot, String entryId, int amount, int page) {
        network().request(action, slot, entryId, amount, page);
    }

    public static void operate(String action, String entryId, CompoundTag operation, int page) {
        network().operate(action, entryId, operation, page);
    }

    public static String resourceKey(ArmoryEntry entry) {
        return ArmoryItemIdentity.resourceKey(entry.stack());
    }

    private static ArmoryNetworkBridge network() {
        if (bridge == null) {
            throw new IllegalStateException("Armory target has not installed a network transport");
        }
        return bridge;
    }
}
