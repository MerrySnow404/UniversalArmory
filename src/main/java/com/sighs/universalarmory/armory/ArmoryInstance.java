package com.sighs.universalarmory.armory;

import com.sighs.universalarmory.block.entity.ArmoryBlockEntity;
import com.sighs.universalarmory.gui.util.AttachmentUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ArmoryInstance {

    private final Level level;
    private final BlockPos blockPos;

    public ArmoryInstance(@NotNull ArmoryBlockEntity blockEntity) {
        this.level = blockEntity.getLevel();
        this.blockPos = blockEntity.getBlockPos().immutable();
    }

    public boolean isValid() {
        return getBlockEntity() != null;
    }

    @NotNull
    public BlockPos getBlockPos() {
        return blockPos;
    }

    @Nullable
    public ArmoryBlockEntity getBlockEntity() {
        if (level == null) {
            return null;
        }
        BlockEntity blockEntity = level.getBlockEntity(blockPos);
        if (blockEntity instanceof ArmoryBlockEntity armoryBlockEntity && !armoryBlockEntity.isRemoved()) {
            return armoryBlockEntity;
        }
        return null;
    }

    @NotNull
    public List<ItemStack> getAllItems() {
        ArmoryBlockEntity blockEntity = getBlockEntity();
        if (blockEntity == null) {
            return Collections.emptyList();
        }
        List<ItemStack> items = new ArrayList<>(blockEntity.getContainerSize());
        for (int slot = 0; slot < blockEntity.getContainerSize(); slot++) {
            items.add(blockEntity.getItem(slot).copy());
        }
        return items;
    }

    @NotNull
    public ItemStack getItem(int slot) {
        ArmoryBlockEntity blockEntity = getBlockEntity();
        if (blockEntity == null) {
            return ItemStack.EMPTY;
        }
        if (slot < 0 || slot >= blockEntity.getContainerSize()) {
            return ItemStack.EMPTY;
        }
        return blockEntity.getItem(slot).copy();
    }

    public void setItem(int slot, @NotNull ItemStack stack) {
        ArmoryBlockEntity blockEntity = getBlockEntity();
        if (blockEntity == null) {
            return;
        }
        if (slot < 0 || slot >= blockEntity.getContainerSize()) {
            return;
        }
        blockEntity.setItem(slot, stack.copy());
        blockEntity.markUpdated();
    }

    @NotNull
    public ItemStack removeItem(int slot) {
        ArmoryBlockEntity blockEntity = getBlockEntity();
        if (blockEntity == null) {
            return ItemStack.EMPTY;
        }
        if (slot < 0 || slot >= blockEntity.getContainerSize()) {
            return ItemStack.EMPTY;
        }
        ItemStack removed = blockEntity.removeItemNoUpdate(slot);
        blockEntity.markUpdated();
        return removed;
    }

    public void clear() {
        ArmoryBlockEntity blockEntity = getBlockEntity();
        if (blockEntity == null) {
            return;
        }
        blockEntity.clearContent();
        blockEntity.markUpdated();
    }

    @NotNull
    public ItemStack addItem(@NotNull ItemStack stack) {
        ArmoryBlockEntity blockEntity = getBlockEntity();
        if (blockEntity == null || stack.isEmpty()) {
            return stack.copy();
        }

        ItemStack remaining = stack.copy();
        for (int slot = 0; slot < blockEntity.getContainerSize(); slot++) {
            ItemStack existing = blockEntity.getItem(slot);
            if (existing.isEmpty()) {
                blockEntity.setItem(slot, remaining);
                blockEntity.markUpdated();
                return ItemStack.EMPTY;
            }
            if (!ItemStack.isSameItemSameTags(existing, remaining)) {
                continue;
            }
            int maxInsert = Math.min(blockEntity.getMaxStackSize(), existing.getMaxStackSize()) - existing.getCount();
            if (maxInsert <= 0) {
                continue;
            }
            int inserted = Math.min(maxInsert, remaining.getCount());
            existing.grow(inserted);
            remaining.shrink(inserted);
            if (remaining.isEmpty()) {
                blockEntity.markUpdated();
                return ItemStack.EMPTY;
            }
        }

        blockEntity.markUpdated();
        return remaining;
    }

    @NotNull
    public List<ItemStack> getAllGuns() {
        ArmoryBlockEntity blockEntity = getBlockEntity();
        if (blockEntity == null) {
            return Collections.emptyList();
        }
        List<ItemStack> guns = new ArrayList<>();
        for (int slot = 0; slot < blockEntity.getContainerSize(); slot++) {
            ItemStack stack = blockEntity.getItem(slot);
            if (AttachmentUtils.isGun(stack)) {
                guns.add(stack.copy());
            }
        }
        return guns;
    }
}
