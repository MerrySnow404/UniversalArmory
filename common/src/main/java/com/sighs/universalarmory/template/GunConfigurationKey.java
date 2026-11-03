package com.sighs.universalarmory.template;

import net.minecraft.world.item.ItemStack;

public final class GunConfigurationKey {
    private GunConfigurationKey() {
    }

    public static String of(ItemStack stack) {
        return GunTemplateCodecs.share().exportCode(stack, "configuration");
    }
}
