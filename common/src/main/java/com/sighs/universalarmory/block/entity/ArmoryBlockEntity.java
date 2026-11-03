package com.sighs.universalarmory.block.entity;

import com.sighs.universalarmory.Universalarmory;
import com.sighs.universalarmory.armory.ArmorySavedData;
import com.sighs.universalarmory.armory.ArmoryStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

public class ArmoryBlockEntity extends BlockEntity {
    private UUID inventoryId = UUID.randomUUID();
    private ArmoryStorage migrated = new ArmoryStorage(this::setChanged);
    private ArmoryStorage storage;

    public ArmoryBlockEntity(BlockPos pos, BlockState blockState) {
        super(com.sighs.universalarmory.spi.ArmoryServices.platform().armoryBlockEntityType(), pos, blockState);
    }

    public ArmoryStorage storage() {
        if (!(level instanceof ServerLevel serverLevel)) {
            throw new IllegalStateException("Armory inventory is server-owned");
        }
        if (storage == null) {
            storage = ArmorySavedData.get(serverLevel).inventory(inventoryId, migrated);
            migrated = new ArmoryStorage(this::setChanged);
            setChanged();
        }
        return storage;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel) {
            storage();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putUUID("ArmoryId", inventoryId);
        if (level instanceof ServerLevel) {
            storage();
        } else if (storage == null && migrated.size() > 0) {
            tag.put("ArmoryInventory", migrated.save());
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        inventoryId = tag.hasUUID("ArmoryId") ? tag.getUUID("ArmoryId") : UUID.randomUUID();
        storage = null;
        migrated = new ArmoryStorage(this::setChanged);
        migrated.load(tag.getList("ArmoryInventory", Tag.TAG_COMPOUND));
        for (Tag value : tag.getList("Items", Tag.TAG_COMPOUND)) {
            ItemStack stack = ItemStack.of((CompoundTag) value);
            if (!stack.isEmpty()) {
                migrated.deposit(stack);
            }
        }
    }
}
