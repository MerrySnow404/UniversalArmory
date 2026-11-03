package com.sighs.universalarmory.armory;

import com.sighs.universalarmory.block.entity.ArmoryBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public final class ArmoryRequestHandler {
    private ArmoryRequestHandler() {
    }

    public static CompoundTag handle(ServerPlayer player, BlockPos pos, String action, int slot, String entryId,
                                     int amount, int page, CompoundTag options) {
        if (action.equals("close")) {
            ArmoryManager.unbind(player);
            return null;
        }
        try {
            ArmoryBlockEntity armory = ArmoryTransfers.requireAccessible(player, pos);
            ArmoryQueryCodec.decode(options);
            ArmoryQueryCodec.sort(options);
            ArmoryEquipmentService.Result operationResult = null;
            String notice = null;
            switch (action) {
                case "refresh" -> { }
                case "deposit" -> ArmoryTransfers.deposit(player, armory, slot, amount);
                case "withdraw" -> ArmoryTransfers.withdraw(player, armory, entryId, amount);
                case "supply-ammo" -> {
                    CompoundTag operation = options.getCompound("Operation");
                    ArmoryTransfers.supplyAmmo(player, armory, operation.getString("Ammo"), operation.getInt("Amount"));
                    notice = "已将对应类型的库存弹药放入背包";
                }
                case "craft", "install", "uninstall", "apply-code", "reload" -> operationResult =
                        ArmoryEquipmentService.perform(player, armory, action, entryId, options.getCompound("Operation"));
                default -> throw new IllegalArgumentException("Unsupported inventory action");
            }
            CompoundTag query = options.copy();
            if (operationResult != null) {
                query.putString("FocusKey", operationResult.key());
            }
            CompoundTag data = snapshot(armory, page, query);
            if (notice != null) {
                data.putString("Notice", notice);
            }
            if (operationResult != null) {
                CompoundTag result = new CompoundTag();
                result.putString("Action", action);
                result.putString("Id", operationResult.inventoryId());
                result.putString("Key", operationResult.key());
                result.putString("Quantity", operationResult.quantity().toString());
                result.put("Item", operationResult.stack().save(new CompoundTag()));
                data.put("OperationResult", result);
            }
            return data;
        } catch (IllegalArgumentException exception) {
            CompoundTag error = new CompoundTag();
            error.putString("Error", exception.getMessage());
            return error;
        }
    }

    private static CompoundTag snapshot(ArmoryBlockEntity armory, int requestedPage, CompoundTag options) {
        ArmoryStorage storage = armory.storage();
        List<ArmoryEntry> inventory = ArmoryEntries.inventory(storage.entries());
        String source = options.getString("Source");
        List<ArmoryEntry> entries = inventory;
        if (source.equals("CATALOGUE")) {
            entries = ArmoryEntries.withCollection(ArmoryEntries.catalogue(), inventory);
        } else if (source.equals("FAVORITE") || source.equals("RECENT")) {
            entries = ArmoryEntries.withCollection(previews(options.getList("Previews", Tag.TAG_COMPOUND),
                    ArmoryEntry.Origin.valueOf(source)), inventory);
        }
        ArmoryQuery.Page page = ArmoryQuery.select(entries, ArmoryQueryCodec.decode(options),
                ArmoryQueryCodec.values(options, "FavoriteKeys"), ArmoryQueryCodec.sort(options),
                options.getBoolean("Descending"), requestedPage, ArmoryQueryCodec.pageSize(options));
        CompoundTag result = new CompoundTag();
        result.putString("Source", source);
        result.putInt("Page", page.page());
        result.putInt("PageCount", page.pageCount());
        result.putInt("MatchCount", page.total());
        result.putInt("EntryCount", inventory.size());
        result.putInt("VariantCount", storage.size());
        result.putString("TotalQuantity", storage.totalQuantity().toString());
        ListTag serialized = new ListTag();
        page.entries().forEach(entry -> serialized.add(serialize(entry)));
        result.put("Entries", serialized);
        result.putInt("RequestedPage", requestedPage);
        if (source.equals("HOME")) {
            long seed = options.getLong("RandomSeed");
            result.put("HomeFavorites", homeEntries(ArmoryEntries.withCollection(
                    previews(options.getList("FavoritePreviews", Tag.TAG_COMPOUND), ArmoryEntry.Origin.FAVORITE), inventory), 2, seed));
            result.put("HomeRecent", homeEntries(ArmoryEntries.withCollection(
                    previews(options.getList("RecentPreviews", Tag.TAG_COMPOUND), ArmoryEntry.Origin.RECENT), inventory), 6, null));
            result.put("HomeCatalogue", homeEntries(ArmoryEntries.withCollection(ArmoryEntries.catalogue(), inventory).stream()
                    .filter(entry -> entry.kind() == ArmoryItemIdentity.Kind.GUN).toList(), 2, seed + 1));
        }
        String focusKey = options.getString("FocusKey");
        if (!focusKey.isEmpty()) {
            CompoundTag focused = new CompoundTag();
            focused.putString("Key", focusKey);
            BigInteger quantity = BigInteger.ZERO;
            for (ArmoryEntry entry : inventory) {
                if (entry.key().equals(focusKey) || ArmoryNetwork.resourceKey(entry).equals(focusKey)) {
                    quantity = quantity.add(entry.quantity());
                    if (!focused.contains("Id")) {
                        focused.putString("Id", entry.inventoryIds().get(0));
                        focused.put("Item", entry.stack().save(new CompoundTag()));
                    }
                }
            }
            focused.putString("Quantity", quantity.toString());
            result.put("Focused", focused);
        }
        return result;
    }

    private static List<ArmoryEntry> previews(ListTag tags, ArmoryEntry.Origin origin) {
        List<ItemStack> stacks = new ArrayList<>();
        for (Tag value : tags) {
            ItemStack stack = ItemStack.of((CompoundTag) value);
            if (!stack.isEmpty()) {
                stacks.add(stack);
            }
        }
        return ArmoryEntries.remembered(stacks, origin);
    }

    private static ListTag homeEntries(List<ArmoryEntry> source, int limit, Long seed) {
        List<ArmoryEntry> entries = new ArrayList<>(source);
        if (seed != null) {
            java.util.Collections.shuffle(entries, new java.util.Random(seed));
        }
        ListTag result = new ListTag();
        entries.stream().limit(limit).forEach(entry -> result.add(serialize(entry)));
        return result;
    }

    private static CompoundTag serialize(ArmoryEntry entry) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Id", entry.inventoryIds().isEmpty() ? "" : entry.inventoryIds().get(0));
        tag.putString("Key", entry.key());
        tag.putString("Origin", entry.origin().name());
        tag.putInt("RecentOrder", entry.recentOrder());
        tag.put("Item", entry.stack().save(new CompoundTag()));
        tag.putString("Quantity", entry.quantity().toString());
        return tag;
    }
}
