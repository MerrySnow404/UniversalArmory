package com.sighs.universalarmory.armory;

import com.sighs.universalarmory.block.entity.ArmoryBlockEntity;
import com.sighs.universalarmory.gui.util.AttachmentUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ArmoryInstance {
    private final Level level;
    private final BlockPos blockPos;
    private final ArmoryBlockEntity boundEntity;

    public ArmoryInstance(@NotNull ArmoryBlockEntity blockEntity) {
        this.level = blockEntity.getLevel();
        this.blockPos = blockEntity.getBlockPos().immutable();
        this.boundEntity = blockEntity;
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
        if (level == null || !level.hasChunkAt(blockPos) || boundEntity.isRemoved()) {
            return null;
        }
        return level.getBlockEntity(blockPos) == boundEntity ? boundEntity : null;
    }

    @NotNull
    public List<ItemStack> getAllItems() {
        ArmoryBlockEntity entity = getBlockEntity();
        if (entity == null) {
            return List.of();
        }
        List<ItemStack> result = new ArrayList<>();
        if (level.isClientSide) {
            if (!blockPos.equals(ArmoryClientState.position())) {
                return List.of();
            }
            for (Tag value : ArmoryClientState.snapshot().getList("Entries", Tag.TAG_COMPOUND)) {
                result.add(ItemStack.of(((CompoundTag) value).getCompound("Item")));
            }
        } else {
            for (ArmoryStorage.Entry entry : entity.storage().entries()) {
                result.add(entry.prototype());
            }
        }
        return List.copyOf(result);
    }

    @NotNull
    public List<ItemStack> getAllGuns() {
        return getAllItems().stream().filter(AttachmentUtils::isGun).toList();
    }
}
