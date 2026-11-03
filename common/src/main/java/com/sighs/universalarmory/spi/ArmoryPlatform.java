package com.sighs.universalarmory.spi;

import com.sighs.universalarmory.block.entity.ArmoryBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.List;

public interface ArmoryPlatform {
    BlockEntityType<ArmoryBlockEntity> armoryBlockEntityType();

    void saveFavorites(List<String> entries);

    void saveRecentViews(List<String> entries);
}
