package com.sighs.universalarmory.forge;

import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.registry.ApricityUIRegistry;
import com.sighs.universalarmory.Universalarmory;
import com.sighs.universalarmory.apricity.element.GunShowElement;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = Universalarmory.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ForgeArmoryClient {
    private ForgeArmoryClient() {
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ApricityUIRegistry.scanPackage("com.sighs.universalarmory.apricity.element");
            Element.register("gunshow", (document, tagName) -> new GunShowElement(document));
        });
    }
}
