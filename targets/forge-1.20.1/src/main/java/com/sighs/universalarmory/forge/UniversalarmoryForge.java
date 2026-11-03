package com.sighs.universalarmory.forge;

import com.sighs.universalarmory.Universalarmory;
import com.sighs.universalarmory.armory.ArmoryNetwork;
import com.sighs.universalarmory.block.entity.ArmoryBlockEntity;
import com.sighs.universalarmory.spi.ArmoryPlatform;
import com.sighs.universalarmory.spi.ArmoryServices;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import java.util.List;

@Mod(Universalarmory.MODID)
public final class UniversalarmoryForge implements ArmoryPlatform {
    public UniversalarmoryForge() {
        ArmoryServices.install(this);
        ArmoryNetwork.install(new com.sighs.universalarmory.spi.ArmoryNetworkBridge() {
            @Override
            public void open(net.minecraft.server.level.ServerPlayer player, net.minecraft.core.BlockPos pos) {
                ForgeArmoryNetwork.open(player, pos);
            }

            @Override
            public void request(String action, int slot, String entryId, int amount, int page) {
                ForgeArmoryNetwork.request(action, slot, entryId, amount, page);
            }

            @Override
            public void operate(String action, String entryId, net.minecraft.nbt.CompoundTag operation, int page) {
                ForgeArmoryNetwork.operate(action, entryId, operation, page);
            }
        });
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ForgeArmoryRegistries.register(bus);
        bus.addListener(this::commonSetup);
        bus.addListener(ForgeArmoryConfig::onLoad);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ForgeArmoryConfig.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        ForgeArmoryNetwork.register();
    }

    @Override
    public BlockEntityType<ArmoryBlockEntity> armoryBlockEntityType() {
        return ForgeArmoryRegistries.ARMORY_BLOCK_ENTITY.get();
    }

    @Override
    public void saveFavorites(List<String> entries) {
        ForgeArmoryConfig.saveFavorites(entries);
    }

    @Override
    public void saveRecentViews(List<String> entries) {
        ForgeArmoryConfig.saveRecent(entries);
    }
}
