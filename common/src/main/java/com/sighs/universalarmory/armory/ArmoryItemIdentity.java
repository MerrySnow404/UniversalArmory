package com.sighs.universalarmory.armory;

import com.sighs.universalarmory.template.GunConfigurationKey;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

public final class ArmoryItemIdentity {
    private ArmoryItemIdentity() {
    }

    public static String key(ItemStack stack) {
        if (IGun.getIGunOrNull(stack) != null) {
            return GunConfigurationKey.of(stack);
        }
        ItemStack normalized = stack.copy();
        normalized.setCount(1);
        return normalized.save(new CompoundTag()).toString();
    }

    public static ResourceLocation resourceId(ItemStack stack) {
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun != null) {
            return gun.getGunId(stack);
        }
        IAmmo ammo = IAmmo.getIAmmoOrNull(stack);
        if (ammo != null) {
            return ammo.getAmmoId(stack);
        }
        IAttachment attachment = IAttachment.getIAttachmentOrNull(stack);
        return attachment != null ? attachment.getAttachmentId(stack) : ForgeRegistries.ITEMS.getKey(stack.getItem());
    }

    public static String resourceKey(ItemStack stack) {
        return "resource:" + kind(stack).name() + ":" + resourceId(stack);
    }

    public static Kind kind(ItemStack stack) {
        if (IGun.getIGunOrNull(stack) != null) {
            return Kind.GUN;
        }
        if (IAmmo.getIAmmoOrNull(stack) != null) {
            return Kind.AMMO;
        }
        return IAttachment.getIAttachmentOrNull(stack) != null ? Kind.ATTACHMENT : Kind.OTHER;
    }

    public enum Kind {
        GUN, AMMO, ATTACHMENT, OTHER
    }
}
