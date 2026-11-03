package com.sighs.universalarmory.armory;

import com.sighs.universalarmory.gui.util.AttachmentUtils;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.math.BigInteger;
import java.util.List;
import java.util.Objects;

public record ArmoryEntry(String key, ItemStack stack, BigInteger quantity,
                          List<String> inventoryIds, Origin origin, int recentOrder) {
    public ArmoryEntry {
        stack = stack.copy();
        stack.setCount(1);
        inventoryIds = List.copyOf(inventoryIds);
        Objects.requireNonNull(key);
        Objects.requireNonNull(quantity);
        Objects.requireNonNull(origin);
    }

    @Override
    public ItemStack stack() {
        return stack.copy();
    }

    public ArmoryItemIdentity.Kind kind() {
        return ArmoryItemIdentity.kind(stack);
    }

    public ResourceLocation resourceId() {
        return ArmoryItemIdentity.resourceId(stack);
    }

    public String name() {
        return stack.getHoverName().getString();
    }

    public String namespace() {
        ResourceLocation id = resourceId();
        return id == null ? "" : id.getNamespace();
    }

    public String type() {
        if (kind() == ArmoryItemIdentity.Kind.GUN) {
            return gunIndex() == null ? "" : gunIndex().getPojo().getType();
        }
        IAttachment attachment = IAttachment.getIAttachmentOrNull(stack);
        return attachment == null ? "" : attachment.getType(stack).name();
    }

    public String ammoId() {
        if (kind() == ArmoryItemIdentity.Kind.AMMO) {
            return Objects.toString(resourceId(), "");
        }
        GunData data = gunData();
        return data == null ? "" : Objects.toString(data.getAmmoId(), "");
    }

    public int attachmentCount() {
        return kind() == ArmoryItemIdentity.Kind.GUN ? AttachmentUtils.getAllAttachmentIds(stack).size() : 0;
    }

    public boolean attachmentLocked() {
        IGun gun = IGun.getIGunOrNull(stack);
        return gun != null && gun.hasAttachmentLock(stack);
    }

    public GunData gunData() {
        CommonGunIndex index = gunIndex();
        return index == null ? null : index.getGunData();
    }

    private CommonGunIndex gunIndex() {
        IGun gun = IGun.getIGunOrNull(stack);
        ResourceLocation id = gun == null ? null : gun.getGunId(stack);
        return id == null ? null : TimelessAPI.getCommonGunIndex(id).orElse(null);
    }

    public enum Origin {
        INVENTORY, CATALOGUE, FAVORITE, RECENT, IMPORT
    }
}
