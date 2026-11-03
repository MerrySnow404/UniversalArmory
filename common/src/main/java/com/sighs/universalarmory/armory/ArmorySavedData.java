package com.sighs.universalarmory.armory;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class ArmorySavedData extends SavedData {
    private final Map<UUID, ArmoryStorage> inventories = new LinkedHashMap<>();

    public static ArmorySavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                ArmorySavedData::load, ArmorySavedData::new, "universalarmory_inventories");
    }

    public ArmoryStorage inventory(UUID id, ArmoryStorage migrated) {
        ArmoryStorage existing = inventories.get(id);
        if (existing != null) {
            return existing;
        }
        ArmoryStorage created = new ArmoryStorage(this::setDirty);
        created.load(migrated.save());
        inventories.put(id, created);
        setDirty();
        return created;
    }

    private static ArmorySavedData load(CompoundTag tag) {
        ArmorySavedData result = new ArmorySavedData();
        CompoundTag inventories = tag.getCompound("Inventories");
        for (String key : inventories.getAllKeys()) {
            UUID id = UUID.fromString(key);
            ArmoryStorage storage = new ArmoryStorage(result::setDirty);
            storage.load(inventories.getList(key, Tag.TAG_COMPOUND));
            result.inventories.put(id, storage);
        }
        return result;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag serialized = new CompoundTag();
        inventories.forEach((id, storage) -> serialized.put(id.toString(), storage.save()));
        tag.put("Inventories", serialized);
        return tag;
    }
}
