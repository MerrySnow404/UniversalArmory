package com.sighs.universalarmory.spi;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

public interface ArmoryNetworkBridge {
    void open(ServerPlayer player, BlockPos pos);

    void request(String action, int slot, String entryId, int amount, int page);

    void operate(String action, String entryId, CompoundTag operation, int page);
}
