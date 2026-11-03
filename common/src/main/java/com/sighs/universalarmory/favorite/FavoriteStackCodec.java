package com.sighs.universalarmory.favorite;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sighs.universalarmory.gui.util.AttachmentUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public final class FavoriteStackCodec {
    private FavoriteStackCodec() {
    }

    public static boolean supports(@Nullable ItemStack stack) {
        return AttachmentUtils.isGun(stack) || AttachmentUtils.isAttachment(stack)
                || stack != null && !stack.isEmpty() && com.tacz.guns.api.item.IAmmo.getIAmmoOrNull(stack) != null;
    }

    @NotNull
    public static Optional<String> encode(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty() || !supports(stack)) {
            return Optional.empty();
        }
        ItemStack normalized = stack.copy();
        normalized.setCount(1);
        CompoundTag serialized = normalized.save(new CompoundTag());
        return Optional.of(serialized.toString());
    }

    @NotNull
    public static Optional<ItemStack> decode(@Nullable String serializedStack) {
        if (serializedStack == null || serializedStack.isBlank()) {
            return Optional.empty();
        }
        try {
            CompoundTag tag = TagParser.parseTag(serializedStack.trim());
            ItemStack stack = ItemStack.of(tag);
            if (stack.isEmpty() || !supports(stack)) {
                return Optional.empty();
            }
            stack.setCount(1);
            return Optional.of(stack);
        } catch (CommandSyntaxException ignored) {
            return Optional.empty();
        }
    }
}
