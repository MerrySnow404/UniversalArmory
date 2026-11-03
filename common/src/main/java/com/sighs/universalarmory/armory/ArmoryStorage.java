package com.sighs.universalarmory.armory;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ArmoryStorage {
    private final Map<String, StoredItem> entries = new LinkedHashMap<>();
    private final Runnable changed;

    public ArmoryStorage(Runnable changed) {
        this.changed = changed;
    }

    public String deposit(ItemStack stack) {
        if (stack.isEmpty()) {
            return "";
        }
        return deposit(stack, BigInteger.valueOf(stack.getCount()));
    }

    public String deposit(ItemStack prototype, BigInteger amount) {
        if (prototype.isEmpty() || amount.signum() <= 0) {
            throw new IllegalArgumentException("A stored item must have a positive quantity");
        }
        for (StoredItem entry : entries.values()) {
            if (ItemStack.isSameItemSameTags(entry.prototype, prototype)) {
                entry.quantity = entry.quantity.add(amount);
                changed.run();
                return entry.id;
            }
        }
        String id = UUID.randomUUID().toString();
        ItemStack normalized = prototype.copy();
        normalized.setCount(1);
        entries.put(id, new StoredItem(id, normalized, amount));
        changed.run();
        return id;
    }

    public ItemStack withdraw(String id, int requested) {
        StoredItem entry = entries.get(id);
        if (entry == null || requested <= 0) {
            return ItemStack.EMPTY;
        }
        int count = entry.quantity.min(BigInteger.valueOf(Math.min(requested, entry.prototype.getMaxStackSize()))).intValueExact();
        ItemStack result = entry.prototype.copy();
        result.setCount(count);
        entry.quantity = entry.quantity.subtract(BigInteger.valueOf(count));
        if (entry.quantity.signum() == 0) {
            entries.remove(id);
        }
        changed.run();
        return result;
    }

    public List<Entry> entries() {
        return page(0, entries.size());
    }

    public int size() {
        return entries.size();
    }

    public List<Entry> page(int offset, int limit) {
        if (offset < 0 || limit < 0) {
            throw new IllegalArgumentException("Invalid inventory range");
        }
        List<Entry> result = new ArrayList<>();
        int index = 0;
        for (StoredItem entry : entries.values()) {
            if (result.size() >= limit) {
                break;
            }
            if (index++ >= offset) {
                result.add(new Entry(entry.id, entry.prototype.copy(), entry.quantity));
            }
        }
        return List.copyOf(result);
    }

    public ItemStack prototype(String id) {
        StoredItem entry = entries.get(id);
        return entry == null ? ItemStack.EMPTY : entry.prototype.copy();
    }

    public boolean consume(String id, BigInteger amount) {
        StoredItem entry = entries.get(id);
        if (entry == null || amount.signum() <= 0 || entry.quantity.compareTo(amount) < 0) {
            return false;
        }
        entry.quantity = entry.quantity.subtract(amount);
        if (entry.quantity.signum() == 0) {
            entries.remove(id);
        }
        changed.run();
        return true;
    }

    public void replaceWith(ArmoryStorage replacement) {
        load(replacement.save());
        changed.run();
    }

    public BigInteger quantity(String id) {
        StoredItem entry = entries.get(id);
        return entry == null ? BigInteger.ZERO : entry.quantity;
    }

    public BigInteger totalQuantity() {
        BigInteger result = BigInteger.ZERO;
        for (StoredItem entry : entries.values()) {
            result = result.add(entry.quantity);
        }
        return result;
    }

    public ListTag save() {
        ListTag result = new ListTag();
        for (StoredItem entry : entries.values()) {
            CompoundTag tag = new CompoundTag();
            tag.putString("Id", entry.id);
            tag.put("Item", entry.prototype.save(new CompoundTag()));
            tag.putString("Quantity", entry.quantity.toString());
            result.add(tag);
        }
        return result;
    }

    public void load(ListTag serialized) {
        Map<String, StoredItem> loaded = new LinkedHashMap<>();
        for (Tag value : serialized) {
            if (!(value instanceof CompoundTag tag)) {
                throw new IllegalArgumentException("Invalid armory inventory entry");
            }
            String id = tag.getString("Id");
            ItemStack stack = ItemStack.of(tag.getCompound("Item"));
            BigInteger quantity = new BigInteger(tag.getString("Quantity"));
            if (id.isBlank() || stack.isEmpty() || quantity.signum() <= 0 || loaded.containsKey(id)) {
                throw new IllegalArgumentException("Invalid armory inventory entry: " + id);
            }
            stack.setCount(1);
            loaded.put(id, new StoredItem(id, stack, quantity));
        }
        entries.clear();
        entries.putAll(loaded);
    }

    public record Entry(String id, ItemStack prototype, BigInteger quantity) {
    }

    private static final class StoredItem {
        private final String id;
        private final ItemStack prototype;
        private BigInteger quantity;

        private StoredItem(String id, ItemStack prototype, BigInteger quantity) {
            this.id = id;
            this.prototype = prototype;
            this.quantity = quantity;
        }
    }
}
