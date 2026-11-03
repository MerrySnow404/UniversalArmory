package com.sighs.universalarmory.armory;

import com.sighs.universalarmory.template.GunTemplate;
import com.sighs.universalarmory.template.GunTemplateCodecs;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.api.item.builder.AttachmentItemBuilder;
import net.minecraft.world.item.ItemStack;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ArmoryEntries {
    private ArmoryEntries() {
    }

    public static List<ArmoryEntry> inventory(Collection<ArmoryStorage.Entry> stored) {
        Map<String, ArmoryEntry> grouped = new LinkedHashMap<>();
        for (ArmoryStorage.Entry item : stored) {
            ItemStack stack = item.prototype();
            String key = ArmoryItemIdentity.key(stack);
            ArmoryEntry previous = grouped.get(key);
            List<String> ids = new ArrayList<>();
            BigInteger quantity = item.quantity();
            if (previous != null) {
                ids.addAll(previous.inventoryIds());
                quantity = quantity.add(previous.quantity());
            }
            ids.add(item.id());
            grouped.put(key, new ArmoryEntry(key, previous == null ? stack : previous.stack(), quantity,
                    ids, ArmoryEntry.Origin.INVENTORY, -1));
        }
        return List.copyOf(grouped.values());
    }

    public static List<ArmoryEntry> catalogue() {
        List<ItemStack> stacks = new ArrayList<>();
        TimelessAPI.getAllCommonGunIndex().forEach(entry -> stacks.add(GunTemplateCodecs.share().createPreviewStack(
                new GunTemplate("catalogue", entry.getKey(), entry.getValue().getPojo().getDisplay(), false, Map.of()))));
        TimelessAPI.getAllCommonAmmoIndex().forEach(entry -> stacks.add(
                AmmoItemBuilder.create().setId(entry.getKey()).setCount(1).build()));
        TimelessAPI.getAllCommonAttachmentIndex().forEach(entry -> stacks.add(
                AttachmentItemBuilder.create().setId(entry.getKey()).setCount(1).build()));
        return remembered(stacks, ArmoryEntry.Origin.CATALOGUE);
    }

    public static List<ArmoryEntry> remembered(Collection<ItemStack> stacks, ArmoryEntry.Origin origin) {
        Map<String, ArmoryEntry> unique = new LinkedHashMap<>();
        int order = 0;
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) {
                continue;
            }
            String key = ArmoryItemIdentity.key(stack);
            unique.putIfAbsent(key, new ArmoryEntry(key, stack, BigInteger.ZERO, List.of(), origin, order++));
        }
        return List.copyOf(unique.values());
    }

    public static List<ArmoryEntry> withCollection(List<ArmoryEntry> source, List<ArmoryEntry> inventory) {
        Map<String, BigInteger> quantities = new LinkedHashMap<>();
        Map<String, List<String>> ids = new LinkedHashMap<>();
        boolean catalogue = !source.isEmpty() && source.get(0).origin() == ArmoryEntry.Origin.CATALOGUE;
        for (ArmoryEntry entry : inventory) {
            String key = catalogue ? collectionKey(entry) : entry.key();
            quantities.merge(key, entry.quantity(), BigInteger::add);
            ids.computeIfAbsent(key, ignored -> new ArrayList<>()).addAll(entry.inventoryIds());
        }
        List<ArmoryEntry> result = new ArrayList<>();
        for (ArmoryEntry entry : source) {
            String key = catalogue ? collectionKey(entry) : entry.key();
            result.add(new ArmoryEntry(entry.key(), entry.stack(), quantities.getOrDefault(key, BigInteger.ZERO),
                    ids.getOrDefault(key, List.of()), entry.origin(), entry.recentOrder()));
        }
        return List.copyOf(result);
    }

    private static String collectionKey(ArmoryEntry entry) {
        return ArmoryItemIdentity.resourceKey(entry.stack());
    }
}
