package com.sighs.universalarmory.armory;

import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class ArmoryInventoryInsertion {
    private ArmoryInventoryInsertion() {
    }

    public static void insert(List<ItemStack> slots, ItemStack remaining, int inventoryLimit) {
        if (remaining.isEmpty() || inventoryLimit <= 0) {
            return;
        }
        for (ItemStack existing : slots) {
            if (remaining.isEmpty()) {
                return;
            }
            if (!existing.isEmpty() && ItemStack.isSameItemSameTags(existing, remaining)) {
                int capacity = Math.min(inventoryLimit, existing.getMaxStackSize()) - existing.getCount();
                int inserted = Math.min(remaining.getCount(), Math.max(0, capacity));
                existing.grow(inserted);
                remaining.shrink(inserted);
            }
        }
        for (int slot = 0; slot < slots.size() && !remaining.isEmpty(); slot++) {
            if (slots.get(slot).isEmpty()) {
                int inserted = Math.min(remaining.getCount(), Math.min(inventoryLimit, remaining.getMaxStackSize()));
                ItemStack placed = remaining.copy();
                placed.setCount(inserted);
                slots.set(slot, placed);
                remaining.shrink(inserted);
            }
        }
    }
}
