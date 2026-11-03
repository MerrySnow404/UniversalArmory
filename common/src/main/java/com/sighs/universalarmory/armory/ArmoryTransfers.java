package com.sighs.universalarmory.armory;

import com.sighs.universalarmory.block.entity.ArmoryBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class ArmoryTransfers {
    private ArmoryTransfers() {
    }

    public static ArmoryBlockEntity requireAccessible(ServerPlayer player, BlockPos pos) {
        if (!player.isAlive() || player.isSpectator() || !player.serverLevel().hasChunkAt(pos)
                || player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64) {
            throw new IllegalArgumentException("Armory is out of reach");
        }
        ArmoryInstance bound = ArmoryManager.getBoundOrNull(player);
        if (bound == null || !bound.getBlockPos().equals(pos)
                || !(player.serverLevel().getBlockEntity(pos) instanceof ArmoryBlockEntity blockEntity)
                || bound.getBlockEntity() != blockEntity) {
            throw new IllegalArgumentException("No matching armory session");
        }
        return blockEntity;
    }

    public static void deposit(ServerPlayer player, ArmoryBlockEntity armory, int slot, int requested) {
        if (slot < 0 || slot >= player.getInventory().getContainerSize() || requested <= 0) {
            throw new IllegalArgumentException("Invalid deposit request");
        }
        ItemStack source = player.getInventory().getItem(slot);
        if (source.isEmpty()) {
            return;
        }
        int count = Math.min(source.getCount(), requested);
        ItemStack inserted = source.copy();
        inserted.setCount(count);
        armory.storage().deposit(inserted);
        source.shrink(count);
        armory.setChanged();
        player.serverLevel().updateNeighbourForOutputSignal(armory.getBlockPos(), armory.getBlockState().getBlock());
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }

    public static void supplyAmmo(ServerPlayer player, ArmoryBlockEntity armory, String ammoId, int requested) {
        net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(ammoId);
        if (id == null || requested < 1 || requested > 4096) {
            throw new IllegalArgumentException("无效的弹药补给请求");
        }
        int remaining = requested;
        for (ArmoryStorage.Entry entry : armory.storage().entries()) {
            com.tacz.guns.api.item.IAmmo ammo = com.tacz.guns.api.item.IAmmo.getIAmmoOrNull(entry.prototype());
            if (ammo == null || !id.equals(ammo.getAmmoId(entry.prototype()))) {
                continue;
            }
            while (remaining > 0 && armory.storage().quantity(entry.id()).signum() > 0) {
                ItemStack taken = armory.storage().withdraw(entry.id(), remaining);
                int count = taken.getCount();
                ArmoryInventoryInsertion.insert(player.getInventory().items, taken, player.getInventory().getMaxStackSize());
                int delivered = count - taken.getCount();
                if (!taken.isEmpty()) {
                    armory.storage().deposit(taken);
                }
                remaining -= delivered;
                if (delivered < count) {
                    break;
                }
            }
            if (remaining == 0) {
                break;
            }
        }
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        armory.setChanged();
        player.serverLevel().updateNeighbourForOutputSignal(armory.getBlockPos(), armory.getBlockState().getBlock());
        if (remaining == requested) {
            throw new IllegalArgumentException("军械库没有该类型弹药，或背包没有可用空间");
        }
    }

    public static void withdraw(ServerPlayer player, ArmoryBlockEntity armory, String entryId, int requested) {
        if (requested <= 0) {
            throw new IllegalArgumentException("Invalid withdrawal request");
        }
        ItemStack taken = armory.storage().withdraw(entryId, requested);
        if (taken.isEmpty()) {
            return;
        }
        ArmoryInventoryInsertion.insert(player.getInventory().items, taken, player.getInventory().getMaxStackSize());
        if (!taken.isEmpty()) {
            armory.storage().deposit(taken);
        }
        armory.setChanged();
        player.serverLevel().updateNeighbourForOutputSignal(armory.getBlockPos(), armory.getBlockState().getBlock());
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }
}
