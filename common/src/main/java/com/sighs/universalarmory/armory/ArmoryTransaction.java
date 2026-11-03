package com.sighs.universalarmory.armory;

import com.sighs.universalarmory.block.entity.ArmoryBlockEntity;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public final class ArmoryTransaction {
    private final ServerPlayer player;
    private final ArmoryBlockEntity armory;
    private final ListTag originalStorage;
    private final List<ItemStack> originalInventory = new ArrayList<>();
    private final List<ItemStack> inventory = new ArrayList<>();
    private final ArmoryStorage draft = new ArmoryStorage(() -> { });
    private boolean committed;

    public ArmoryTransaction(ServerPlayer player, ArmoryBlockEntity armory) {
        this.player = player;
        this.armory = armory;
        originalStorage = armory.storage().save();
        draft.load(originalStorage);
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            originalInventory.add(stack.copy());
            inventory.add(stack.copy());
        }
    }

    public ArmoryStorage storage() {
        return draft;
    }

    public ItemStack find(Predicate<ItemStack> matches) {
        for (ArmoryStorage.Entry entry : draft.entries()) {
            if (matches.test(entry.prototype())) {
                return entry.prototype();
            }
        }
        for (ItemStack stack : inventory) {
            if (!stack.isEmpty() && matches.test(stack)) {
                ItemStack result = stack.copy();
                result.setCount(1);
                return result;
            }
        }
        return ItemStack.EMPTY;
    }

    public void consume(List<Requirement> requirements) {
        List<Supply> supplies = new ArrayList<>();
        for (ArmoryStorage.Entry entry : draft.entries()) {
            supplies.add(new Supply(entry.prototype(), entry.quantity(), entry.id(), -1));
        }
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.get(slot);
            if (!stack.isEmpty()) {
                supplies.add(new Supply(stack, BigInteger.valueOf(stack.getCount()), "", slot));
            }
        }
        long[] required = requirements.stream().mapToLong(Requirement::count).toArray();
        boolean[][] matches = new boolean[supplies.size()][requirements.size()];
        for (int source = 0; source < supplies.size(); source++) {
            for (int demand = 0; demand < requirements.size(); demand++) {
                matches[source][demand] = requirements.get(demand).matches.test(supplies.get(source).stack);
            }
        }
        long[] consumed = MaterialAllocator.allocate(supplies.stream().map(Supply::quantity).toList(), required, matches);
        for (int source = 0; source < supplies.size(); source++) {
            long count = consumed[source];
            if (count == 0) {
                continue;
            }
            Supply supply = supplies.get(source);
            ItemStack remainder = supply.stack.getCraftingRemainingItem();
            if (supply.slot < 0) {
                if (!draft.consume(supply.id, BigInteger.valueOf(count))) {
                    throw new IllegalStateException("Material allocation no longer matches draft inventory");
                }
            } else {
                inventory.get(supply.slot).shrink(Math.toIntExact(count));
            }
            if (!remainder.isEmpty()) {
                draft.deposit(remainder, BigInteger.valueOf(count).multiply(BigInteger.valueOf(remainder.getCount())));
            }
        }
    }

    public void commit() {
        if (committed || !armory.storage().save().equals(originalStorage)) {
            throw new IllegalArgumentException("军械库已变化，请刷新后重试");
        }
        for (int slot = 0; slot < originalInventory.size(); slot++) {
            ItemStack original = originalInventory.get(slot);
            ItemStack current = player.getInventory().getItem(slot);
            if (original.getCount() != current.getCount() || !ItemStack.isSameItemSameTags(original, current)) {
                throw new IllegalArgumentException("背包已变化，请刷新后重试");
            }
        }
        armory.storage().replaceWith(draft);
        for (int slot = 0; slot < inventory.size(); slot++) {
            player.getInventory().setItem(slot, inventory.get(slot));
        }
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        armory.setChanged();
        player.serverLevel().updateNeighbourForOutputSignal(armory.getBlockPos(), armory.getBlockState().getBlock());
        committed = true;
    }

    public record Requirement(Predicate<ItemStack> matches, long count) {
        public Requirement {
            if (matches == null || count < 0) {
                throw new IllegalArgumentException("Invalid material requirement");
            }
        }
    }

    private record Supply(ItemStack stack, BigInteger quantity, String id, int slot) { }
}
