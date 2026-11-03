package com.sighs.universalarmory.forge;

import com.sighs.universalarmory.Universalarmory;
import com.sighs.universalarmory.block.ArmoryBlock;
import com.sighs.universalarmory.block.entity.ArmoryBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

final class ForgeArmoryRegistries {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Universalarmory.MODID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Universalarmory.MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Universalarmory.MODID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Universalarmory.MODID);
    static final RegistryObject<Block> ARMORY = BLOCKS.register("armory",
            () -> new ArmoryBlock(BlockBehaviour.Properties.copy(Blocks.CRAFTING_TABLE)));
    private static final RegistryObject<Item> ARMORY_ITEM = ITEMS.register("armory",
            () -> new BlockItem(ARMORY.get(), new Item.Properties()));
    static final RegistryObject<BlockEntityType<ArmoryBlockEntity>> ARMORY_BLOCK_ENTITY = BLOCK_ENTITIES.register("armory",
            () -> BlockEntityType.Builder.of(ArmoryBlockEntity::new, ARMORY.get()).build(null));
    private static final RegistryObject<Block> EXAMPLE_BLOCK = BLOCKS.register("example_block",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.STONE)));
    private static final RegistryObject<Item> EXAMPLE_BLOCK_ITEM = ITEMS.register("example_block",
            () -> new BlockItem(EXAMPLE_BLOCK.get(), new Item.Properties()));
    private static final RegistryObject<Item> EXAMPLE_ITEM = ITEMS.register("example_item",
            () -> new Item(new Item.Properties().food(new FoodProperties.Builder().alwaysEat().nutrition(1).saturationMod(2f).build())));
    private static final RegistryObject<CreativeModeTab> TAB = TABS.register("example_tab", () -> CreativeModeTab.builder()
            .withTabsBefore(CreativeModeTabs.COMBAT).icon(() -> ARMORY_ITEM.get().getDefaultInstance())
            .title(Component.translatable("itemGroup.universalarmory"))
            .displayItems((parameters, output) -> {
                output.accept(ARMORY_ITEM.get());
                output.accept(EXAMPLE_ITEM.get());
            }).build());

    private ForgeArmoryRegistries() {
    }

    static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        TABS.register(bus);
        bus.addListener(ForgeArmoryRegistries::creativeContents);
    }

    private static void creativeContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(ARMORY_ITEM);
        }
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(EXAMPLE_BLOCK_ITEM);
        }
    }
}
