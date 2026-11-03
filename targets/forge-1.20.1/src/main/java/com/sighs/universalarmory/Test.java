package com.sighs.universalarmory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Universalarmory.MODID, value = Dist.CLIENT)
public final class Test {
    private static boolean opened;

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (!opened && Boolean.getBoolean("universalarmory.preview")
                && Minecraft.getInstance().screen instanceof TitleScreen) {
            opened = true;
            GUI.open();
        }
    }
}
