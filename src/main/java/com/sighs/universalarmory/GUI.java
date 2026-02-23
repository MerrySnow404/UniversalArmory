package com.sighs.universalarmory;

import com.sighs.apricityui.ApricityUI;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Universalarmory.MODID, value = Dist.CLIENT)
public class GUI {
    private static final String PATH = "ua_ms/index.html";

    @SubscribeEvent
    public static void open(LivingEvent.LivingJumpEvent event) {
        if (event.getEntity() instanceof Player player && player.isLocalPlayer()) {
            ApricityUI.openScreen(PATH);
        }
    }
}
