package com.sighs.universalarmory.armory;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.math.BigInteger;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

public final class ArmoryQueryCodec {
    private ArmoryQueryCodec() {
    }

    public static CompoundTag encode(ArmoryQuery.Filter filter, ArmoryQuery.Sort sort, boolean descending, int pageSize) {
        CompoundTag result = new CompoundTag();
        result.putString("Search", filter.search());
        result.put("Kinds", strings(filter.kinds().stream().map(Enum::name).collect(Collectors.toSet())));
        result.put("Types", strings(filter.types()));
        result.put("Namespaces", strings(filter.namespaces()));
        result.put("AmmoIds", strings(filter.ammoIds()));
        result.putString("Ownership", filter.ownership().name());
        result.putString("Attachments", filter.attachments().name());
        result.putString("Locked", filter.locked().name());
        result.putBoolean("FavoriteOnly", filter.favoriteOnly());
        result.putString("MinimumQuantity", filter.minimumQuantity().toString());
        if (filter.maximumQuantity() != null) {
            result.putString("MaximumQuantity", filter.maximumQuantity().toString());
        }
        putRange(result, "Damage", filter.damage());
        putRange(result, "Rpm", filter.rpm());
        putRange(result, "Weight", filter.weight());
        result.putString("Sort", sort.name());
        result.putBoolean("Descending", descending);
        result.putInt("PageSize", Math.max(1, Math.min(96, pageSize)));
        return result;
    }

    public static ArmoryQuery.Filter decode(CompoundTag tag) {
        if (!tag.contains("Sort")) {
            return ArmoryQuery.Filter.all();
        }
        Set<ArmoryItemIdentity.Kind> kinds = values(tag, "Kinds").stream()
                .map(ArmoryItemIdentity.Kind::valueOf).collect(Collectors.toSet());
        return new ArmoryQuery.Filter(tag.getString("Search"), kinds, values(tag, "Types"), values(tag, "Namespaces"),
                values(tag, "AmmoIds"), ArmoryQuery.Ownership.valueOf(tag.getString("Ownership")),
                ArmoryQuery.Attachments.valueOf(tag.getString("Attachments")), ArmoryQuery.Toggle.valueOf(tag.getString("Locked")),
                tag.getBoolean("FavoriteOnly"), new BigInteger(tag.getString("MinimumQuantity")),
                tag.contains("MaximumQuantity") ? new BigInteger(tag.getString("MaximumQuantity")) : null,
                range(tag, "Damage"), range(tag, "Rpm"), range(tag, "Weight"));
    }

    public static ArmoryQuery.Sort sort(CompoundTag tag) {
        return tag.contains("Sort") ? ArmoryQuery.Sort.valueOf(tag.getString("Sort")) : ArmoryQuery.Sort.NAME;
    }

    public static int pageSize(CompoundTag tag) {
        return tag.contains("PageSize") ? Math.max(1, Math.min(96, tag.getInt("PageSize"))) : 24;
    }

    public static Set<String> values(CompoundTag tag, String name) {
        Set<String> result = new LinkedHashSet<>();
        for (Tag value : tag.getList(name, Tag.TAG_STRING)) {
            result.add(value.getAsString());
        }
        return Set.copyOf(result);
    }

    private static ListTag strings(Set<String> values) {
        ListTag result = new ListTag();
        values.stream().sorted().forEach(value -> result.add(StringTag.valueOf(value)));
        return result;
    }

    private static void putRange(CompoundTag tag, String name, ArmoryQuery.Range range) {
        tag.putDouble(name + "Minimum", range.minimum());
        tag.putDouble(name + "Maximum", range.maximum());
    }

    private static ArmoryQuery.Range range(CompoundTag tag, String name) {
        return new ArmoryQuery.Range(tag.getDouble(name + "Minimum"), tag.getDouble(name + "Maximum"));
    }
}
