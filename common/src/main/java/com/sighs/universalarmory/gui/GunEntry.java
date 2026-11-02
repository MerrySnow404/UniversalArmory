package com.sighs.universalarmory.gui;

import com.sighs.universalarmory.gui.util.AttachmentUtils;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * 枪械条目 —— 在 UI 中代表一把枪及其配件信息。
 *
 * <p>将枪械的 ItemStack 解析为前端友好的数据结构，
 * 包含枪械 ID、弹药、开火模式、所有配件槽位的状态等。
 */
public class GunEntry {

    /** 在列表中的索引位置 */
    private final int index;

    /** 枪械物品本体 */
    private final ItemStack gunStack;

    /** 枪械的 IGun 接口引用 */
    @Nullable
    private final IGun gun;

    // ---- 缓存数据 ----

    @Nullable
    private ResourceLocation gunId;

    @Nullable
    private ResourceLocation displayId;

    private int ammoCount;

    private int maxAmmo;

    private boolean attachmentLocked;

    /** 配件槽位 Map：type -> SlotInfo */
    private final Map<AttachmentType, SlotInfo> slots = new EnumMap<>(AttachmentType.class);

    public GunEntry(int index, @NotNull ItemStack gunStack) {
        this.index = index;
        this.gunStack = gunStack;
        this.gun = AttachmentUtils.getGunOrNull(gunStack);
        refresh();
    }

    /**
     * 刷新所有缓存数据（枪械状态可能变化时调用）。
     */
    public void refresh() {
        if (gun == null) return;

        this.gunId = gun.getGunId(gunStack);
        this.displayId = gun.getGunDisplayId(gunStack);
        this.ammoCount = gun.getCurrentAmmoCount(gunStack);
        this.maxAmmo = 0; // GunData 中有 ammoAmount，但需要从 CommonAssetsManager 获取
        this.attachmentLocked = gun.hasAttachmentLock(gunStack);

        // 刷新所有槽位
        slots.clear();
        for (AttachmentType type : AttachmentType.values()) {
            if (type == AttachmentType.NONE) continue;
            slots.put(type, new SlotInfo(gunStack, type));
        }
    }

    // ============================================================
    //  Getters
    // ============================================================

    public int getIndex() {
        return index;
    }

    @NotNull
    public ItemStack getGunStack() {
        return gunStack;
    }

    @Nullable
    public IGun getGun() {
        return gun;
    }

    public boolean isValid() {
        return gun != null;
    }

    @Nullable
    public ResourceLocation getGunId() {
        return gunId;
    }

    @Nullable
    public ResourceLocation getDisplayId() {
        return displayId;
    }

    public int getAmmoCount() {
        return ammoCount;
    }

    public boolean isAttachmentLocked() {
        return attachmentLocked;
    }

    /**
     * 获取指定类型的槽位信息。
     */
    @Nullable
    public SlotInfo getSlot(@NotNull AttachmentType type) {
        return slots.get(type);
    }

    /**
     * 获取所有非空槽位（已安装配件的）。
     */
    @NotNull
    public Map<AttachmentType, SlotInfo> getFilledSlots() {
        Map<AttachmentType, SlotInfo> filled = new EnumMap<>(AttachmentType.class);
        for (Map.Entry<AttachmentType, SlotInfo> entry : slots.entrySet()) {
            if (entry.getValue().hasAttachment()) {
                filled.put(entry.getKey(), entry.getValue());
            }
        }
        return filled;
    }

    /**
     * 获取所有支持的槽位类型。
     */
    @NotNull
    public List<AttachmentType> getSupportedSlotTypes() {
        List<AttachmentType> supported = new ArrayList<>();
        for (Map.Entry<AttachmentType, SlotInfo> entry : slots.entrySet()) {
            if (entry.getValue().isSupported()) {
                supported.add(entry.getKey());
            }
        }
        return supported;
    }

    /**
     * 获取枪的显示名称。
     */
    @NotNull
    public String getDisplayName() {
        return gunStack.getHoverName().getString();
    }

    // ============================================================
    //  Map 转换 —— 供 UI 前端使用
    // ============================================================

    /**
     * 将条目序列化为 Map，便于 ApricityUI 前端使用。
     */
    @NotNull
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("index", index);
        map.put("displayName", getDisplayName());
        map.put("gunId", Objects.toString(gunId, ""));
        map.put("displayId", Objects.toString(displayId, ""));
        map.put("ammoCount", ammoCount);
        map.put("attachmentLocked", attachmentLocked);

        Map<String, Object> slotsMap = new LinkedHashMap<>();
        for (Map.Entry<AttachmentType, SlotInfo> entry : slots.entrySet()) {
            slotsMap.put(entry.getKey().name().toLowerCase(), entry.getValue().toMap());
        }
        map.put("slots", slotsMap);
        return map;
    }

    // ============================================================
    //  内部类 —— 配件槽位信息
    // ============================================================

    /**
     * 表示枪械上的一个配件槽位。
     */
    public static class SlotInfo {

        private final AttachmentType type;
        private final boolean supported;
        private final boolean hasBuiltin;
        private final boolean hasAttachment;
        @Nullable
        private final ResourceLocation installedId;
        @Nullable
        private final ResourceLocation builtinId;
        @Nullable
        private final ResourceLocation effectiveId;

        SlotInfo(@NotNull ItemStack gunStack, @NotNull AttachmentType type) {
            this.type = type;
            IGun gun = IGun.getIGunOrNull(gunStack);
            if (gun != null) {
                this.supported = gun.allowAttachmentType(gunStack, type);
                this.installedId = gun.getAttachmentId(gunStack, type);
                this.builtinId = gun.getBuiltInAttachmentId(gunStack, type);
                this.hasBuiltin = builtinId != null;
                this.hasAttachment = installedId != null;
                this.effectiveId = hasAttachment ? installedId : builtinId;
            } else {
                this.supported = false;
                this.installedId = null;
                this.builtinId = null;
                this.hasBuiltin = false;
                this.hasAttachment = false;
                this.effectiveId = null;
            }
        }

        @NotNull
        public AttachmentType getType() {
            return type;
        }

        public boolean isSupported() {
            return supported;
        }

        public boolean hasBuiltin() {
            return hasBuiltin;
        }

        public boolean hasAttachment() {
            return hasAttachment;
        }

        @Nullable
        public ResourceLocation getInstalledId() {
            return installedId;
        }

        @Nullable
        public ResourceLocation getBuiltinId() {
            return builtinId;
        }

        /**
         * 最终生效的配件 ID（优先已安装，其次内置）。
         */
        @Nullable
        public ResourceLocation getEffectiveId() {
            return effectiveId;
        }

        /**
         * 判断该槽位是否为空（无内置也无已安装配件）。
         */
        public boolean isEmpty() {
            return !hasAttachment && !hasBuiltin;
        }

        @NotNull
        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("type", type.name());
            map.put("supported", supported);
            map.put("hasAttachment", hasAttachment);
            map.put("hasBuiltin", hasBuiltin);
            map.put("isEmpty", isEmpty());
            map.put("installedId", Objects.toString(installedId, ""));
            map.put("builtinId", Objects.toString(builtinId, ""));
            map.put("effectiveId", Objects.toString(effectiveId, ""));
            return map;
        }
    }
}
