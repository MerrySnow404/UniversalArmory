package com.sighs.universalarmory.template;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public interface GunTemplateShareService {
    boolean supports(@NotNull ItemStack stack);

    @NotNull
    GunTemplate exportTemplate(@NotNull ItemStack gunStack, @NotNull String templateName);

    @NotNull
    String exportCode(@NotNull ItemStack gunStack, @NotNull String templateName);

    @NotNull
    String exportCode(@NotNull GunTemplate template);

    @NotNull
    GunTemplate importCode(@NotNull String code);

    @NotNull
    ItemStack createPreviewStack(@NotNull GunTemplate template);
}
