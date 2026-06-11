package com.sighs.universalarmory.gui.util;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.nbt.GunItemDataAccessor;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.index.CommonAttachmentIndex;
import com.tacz.guns.resource.pojo.data.attachment.AttachmentData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 配件编辑工具类 —— 万用军械库改枪 UI 的核心工具。
 *
 * <p>提供对 TACZ 枪械配件的完整操作能力：
 * <ul>
 *     <li>查询枪械上已安装的配件</li>
 *     <li>安装 / 卸载配件</li>
 *     <li>兼容性检查</li>
 *     <li>从玩家背包筛选可用的兼容配件</li>
 *     <li>配件数据读取（重量、属性修饰器等）</li>
 * </ul>
 *
 * <p>所有方法均为静态，可直接调用。
 */
public final class AttachmentUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(AttachmentUtils.class);
    private static final ResourceLocation EMPTY_ATTACHMENT_ID = new ResourceLocation("tacz", "empty");

    @NotNull
    private static String getItemRegistryName(@NotNull ItemStack stack) {
        return Objects.toString(ForgeRegistries.ITEMS.getKey(stack.getItem()), "");
    }

    // ============================================================
    //  类型检测
    // ============================================================

    /**
     * 判断一个 ItemStack 是否为枪械。
     */
    public static boolean isGun(@Nullable ItemStack stack) {
        return stack != null && !stack.isEmpty() && IGun.getIGunOrNull(stack) != null;
    }

    /**
     * 从 ItemStack 获取 IGun 接口，若物品不是枪械则返回 null。
     */
    @Nullable
    public static IGun getGunOrNull(@Nullable ItemStack stack) {
        return IGun.getIGunOrNull(stack);
    }

    /**
     * 从 ItemStack 获取 IGun 接口，若物品不是枪械则抛出异常。
     */
    @NotNull
    public static IGun getGun(@Nullable ItemStack stack) {
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null) {
            throw new IllegalArgumentException("ItemStack is not a gun: " + (stack == null ? "null" : stack.getItem().toString()));
        }
        return gun;
    }

    /**
     * 判断一个 ItemStack 是否为配件。
     */
    public static boolean isAttachment(@Nullable ItemStack stack) {
        return stack != null && !stack.isEmpty() && IAttachment.getIAttachmentOrNull(stack) != null;
    }

    /**
     * 从 ItemStack 获取 IAttachment 接口，若物品不是配件则返回 null。
     */
    @Nullable
    public static IAttachment getAttachmentOrNull(@Nullable ItemStack stack) {
        return IAttachment.getIAttachmentOrNull(stack);
    }

    /**
     * 从 ItemStack 获取 IAttachment 接口，若物品不是配件则抛出异常。
     */
    @NotNull
    public static IAttachment getAttachment(@Nullable ItemStack stack) {
        IAttachment attachment = IAttachment.getIAttachmentOrNull(stack);
        if (attachment == null) {
            throw new IllegalArgumentException("ItemStack is not an attachment: " + (stack == null ? "null" : stack.getItem().toString()));
        }
        return attachment;
    }

    // ============================================================
    //  配件查询 —— 枪上已有的配件
    // ============================================================

    /**
     * 获取枪械上指定类型的配件 ItemStack。
     *
     * @param gunStack 枪械物品
     * @param type     配件类型
     * @return 已安装的配件 ItemStack（可能为空），若槽位为空返回 {@link ItemStack#EMPTY}
     */
    @NotNull
    public static ItemStack getAttachment(@NotNull ItemStack gunStack, @NotNull AttachmentType type) {
        IGun gun = getGun(gunStack);
        ItemStack attachment = gun.getAttachment(gunStack, type);
        return attachment != null ? attachment : ItemStack.EMPTY;
    }

    /**
     * 获取枪械上指定类型的配件注册 ID。
     *
     * @return 配件 ID，若槽位为空则返回 null
     */
    @Nullable
    public static ResourceLocation getAttachmentId(@NotNull ItemStack gunStack, @NotNull AttachmentType type) {
        ResourceLocation id = getGun(gunStack).getAttachmentId(gunStack, type);
        return isEmptyAttachmentId(id) ? null : id;
    }

    /**
     * 获取枪械上指定类型的配件 NBT 数据。
     *
     * @return 配件 NBT，若槽位为空则返回 null
     */
    @Nullable
    public static CompoundTag getAttachmentTag(@NotNull ItemStack gunStack, @NotNull AttachmentType type) {
        return getGun(gunStack).getAttachmentTag(gunStack, type);
    }

    /**
     * 获取枪械上所有已安装的配件，以 {@link AttachmentType} 为 key。
     */
    @NotNull
    public static Map<AttachmentType, ItemStack> getAllAttachments(@NotNull ItemStack gunStack) {
        IGun gun = getGun(gunStack);
        Map<AttachmentType, ItemStack> result = new EnumMap<>(AttachmentType.class);
        for (AttachmentType type : AttachmentType.values()) {
            if (type == AttachmentType.NONE) continue;
            ItemStack attachment = gun.getAttachment(gunStack, type);
            if (attachment != null && !attachment.isEmpty() && !isEmptyAttachmentId(gun.getAttachmentId(gunStack, type))) {
                result.put(type, attachment);
            }
        }
        return result;
    }

    /**
     * 获取枪械上所有已安装配件的注册 ID 列表。
     */
    @NotNull
    public static List<ResourceLocation> getAllAttachmentIds(@NotNull ItemStack gunStack) {
        IGun gun = getGun(gunStack);
        List<ResourceLocation> ids = new ArrayList<>();
        for (AttachmentType type : AttachmentType.values()) {
            if (type == AttachmentType.NONE) continue;
            ResourceLocation id = gun.getAttachmentId(gunStack, type);
            if (!isEmptyAttachmentId(id)) {
                ids.add(id);
            }
        }
        return ids;
    }

    /**
     * 判断枪械指定配件槽位是否有配件。
     */
    public static boolean hasAttachment(@NotNull ItemStack gunStack, @NotNull AttachmentType type) {
        return getAttachmentId(gunStack, type) != null;
    }

    /**
     * 获取枪械已安装的配件数量。
     */
    public static int getAttachmentCount(@NotNull ItemStack gunStack) {
        return getAllAttachments(gunStack).size();
    }

    // ============================================================
    //  内置配件（枪械自带不可拆卸的配件）
    // ============================================================

    /**
     * 获取枪械指定类型的内置配件 ID。
     */
    @Nullable
    public static ResourceLocation getBuiltinAttachmentId(@NotNull ItemStack gunStack, @NotNull AttachmentType type) {
        ResourceLocation id = getGun(gunStack).getBuiltInAttachmentId(gunStack, type);
        return isEmptyAttachmentId(id) ? null : id;
    }

    /**
     * 判断指定类型是否为内置配件（枪自带的，不可拆卸）。
     */
    public static boolean hasBuiltinAttachment(@NotNull ItemStack gunStack, @NotNull AttachmentType type) {
        return getBuiltinAttachmentId(gunStack, type) != null;
    }

    /**
     * 获取枪械上指定槽位的最终配件 ID。
     * 优先返回玩家安装的配件，若没有则回退到内置配件。
     */
    @Nullable
    public static ResourceLocation getEffectiveAttachmentId(@NotNull ItemStack gunStack, @NotNull AttachmentType type) {
        ResourceLocation id = getAttachmentId(gunStack, type);
        if (id != null) return id;
        return getBuiltinAttachmentId(gunStack, type);
    }

    /**
     * 获取枪械上指定槽位的最终配件 ItemStack。
     * 优先返回玩家安装的配件，若没有则回退到内置配件。
     */
    @NotNull
    public static ItemStack getEffectiveAttachment(@NotNull ItemStack gunStack, @NotNull AttachmentType type) {
        ItemStack attachment = getAttachment(gunStack, type);
        if (!attachment.isEmpty()) return attachment;
        // 内置配件没有真实的 ItemStack，只能返回 getAttachment 的结果
        return attachment;
    }

    // ============================================================
    //  配件安装 / 卸载
    // ============================================================

    /**
     * 安装配件到枪械上。
     *
     * @param gunStack        枪械物品（会被修改）
     * @param attachmentStack 要安装的配件物品（会被消耗，数量 -1）
     * @return 安装是否成功
     */
    public static boolean installAttachment(@NotNull ItemStack gunStack, @NotNull ItemStack attachmentStack) {
        if (!isGun(gunStack) || !isAttachment(attachmentStack)) {
            return false;
        }
        IGun gun = getGun(gunStack);
        // 先检查兼容性
        if (!gun.allowAttachment(gunStack, attachmentStack)) {
            return false;
        }
        try {
            gun.installAttachment(gunStack, attachmentStack);
            attachmentStack.shrink(1);
            return true;
        } catch (Exception e) {
            LOGGER.error("Failed to install attachment {} on gun {}",
                    getItemRegistryName(attachmentStack), getItemRegistryName(gunStack), e);
            return false;
        }
    }

    /**
     * 按照 TACZ ClientMessageRefitGun 的顺序安装配件。
     * 会先读取旧配件，再执行 installAttachment，并在需要时触发属性刷新和扩容弹匣清空弹药逻辑。
     *
     * @param gunStack                  目标枪械
     * @param attachmentStack           待安装配件
     * @param owner                     持有者，可为空；非空时会触发属性刷新，且扩容弹匣会清空弹药
     * @param replacedAttachmentConsumer 用于接收被替换下来的旧配件，可为空
     * @return 是否安装成功
     */
    public static boolean installAttachmentLikeRefit(@NotNull ItemStack gunStack,
                                                     @NotNull ItemStack attachmentStack,
                                                     @Nullable LivingEntity owner,
                                                     @Nullable Consumer<ItemStack> replacedAttachmentConsumer) {
        if (!isGun(gunStack) || !isAttachment(attachmentStack)) {
            return false;
        }

        IGun gun = getGun(gunStack);
        if (gun.hasAttachmentLock(gunStack) || !gun.allowAttachment(gunStack, attachmentStack)) {
            return false;
        }

        IAttachment attachment = getAttachmentOrNull(attachmentStack);
        if (attachment == null) {
            return false;
        }

        AttachmentType type = attachment.getType(attachmentStack);
        ItemStack replacedAttachment = gun.getAttachment(gunStack, type);

        try {
            gun.installAttachment(gunStack, attachmentStack);
            if (owner != null) {
                AttachmentPropertyManager.postChangeEvent(owner, gunStack);
                if (type == AttachmentType.EXTENDED_MAG && owner instanceof Player player) {
                    gun.dropAllAmmo(player, gunStack);
                }
            }
            if (replacedAttachmentConsumer != null) {
                replacedAttachmentConsumer.accept(replacedAttachment);
            }
            return true;
        } catch (Exception e) {
            LOGGER.error("Failed to install attachment {} on gun {} with refit flow",
                    getItemRegistryName(attachmentStack), getItemRegistryName(gunStack), e);
            return false;
        }
    }

    public static boolean installAttachmentLikeRefit(@NotNull ItemStack gunStack,
                                                     @NotNull ItemStack attachmentStack,
                                                     @Nullable LivingEntity owner) {
        return installAttachmentLikeRefit(gunStack, attachmentStack, owner, null);
    }

    /**
     * 从枪械上卸载指定类型的配件。
     *
     * @param gunStack 枪械物品（会被修改）
     * @param type     要卸载的配件类型
     * @return 卸载出的配件 ItemStack，若无配件可卸返回 {@link ItemStack#EMPTY}
     */
    @NotNull
    public static ItemStack unloadAttachment(@NotNull ItemStack gunStack, @NotNull AttachmentType type) {
        if (!isGun(gunStack)) {
            return ItemStack.EMPTY;
        }
        IGun gun = getGun(gunStack);

        // 读取旧配件信息用于构造返回值
        ResourceLocation oldId = getAttachmentId(gunStack, type);
        if (oldId == null) {
            return ItemStack.EMPTY; // 槽位为空
        }

        CompoundTag oldTag = gun.getAttachmentTag(gunStack, type);
        try {
            gun.unloadAttachment(gunStack, type);
        } catch (Exception e) {
            LOGGER.error("Failed to unload attachment {} from gun {}", oldId, getItemRegistryName(gunStack), e);
            return ItemStack.EMPTY;
        }

        // 构造一个代表被卸下配件的 ItemStack
        ItemStack result = new ItemStack(Objects.requireNonNull(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(oldId)), 1);
        if (oldTag != null && !oldTag.isEmpty()) {
            result.setTag(oldTag.copy());
        }
        return result;
    }

    /**
     * 卸载配件并直接丢给玩家（掉落或进背包）。
     *
     * @param player   玩家
     * @param gunStack 枪械物品
     * @param type     要卸载的配件类型
     * @return 是否成功卸下
     */
    public static boolean unloadAttachmentToPlayer(@NotNull Player player, @NotNull ItemStack gunStack, @NotNull AttachmentType type) {
        ItemStack detached = unloadAttachment(gunStack, type);
        if (detached.isEmpty()) {
            return false;
        }
        ItemHandlerHelper.giveItemToPlayer(player, detached);
        return true;
    }

    // ============================================================
    //  兼容性检查
    // ============================================================

    /**
     * 检查配件是否可以安装到枪械上（综合检查类型 + 标签 + 其他条件）。
     */
    public static boolean canAttach(@NotNull ItemStack gunStack, @NotNull ItemStack attachmentStack) {
        if (!isGun(gunStack) || !isAttachment(attachmentStack)) {
            return false;
        }
        return getGun(gunStack).allowAttachment(gunStack, attachmentStack);
    }

    /**
     * 检查枪械是否支持指定配件类型。
     */
    public static boolean canAttachType(@NotNull ItemStack gunStack, @NotNull AttachmentType type) {
        if (!isGun(gunStack) || type == AttachmentType.NONE) {
            return false;
        }
        return getGun(gunStack).allowAttachmentType(gunStack, type);
    }

    /**
     * 获取枪械支持的所有配件类型（排除 NONE）。
     */
    @NotNull
    public static List<AttachmentType> getSupportedAttachmentTypes(@NotNull ItemStack gunStack) {
        if (!isGun(gunStack)) {
            return Collections.emptyList();
        }
        IGun gun = getGun(gunStack);
        List<AttachmentType> supported = new ArrayList<>();
        for (AttachmentType type : AttachmentType.values()) {
            if (type == AttachmentType.NONE) continue;
            if (gun.allowAttachmentType(gunStack, type)) {
                supported.add(type);
            }
        }
        return supported;
    }

    // ============================================================
    //  配件信息查询
    // ============================================================

    /**
     * 获取配件的 {@link AttachmentType}。
     */
    @Nullable
    public static AttachmentType getAttachmentType(@Nullable ItemStack attachmentStack) {
        if (!isAttachment(attachmentStack)) return null;
        return getAttachment(attachmentStack).getType(attachmentStack);
    }

    /**
     * 获取配件的注册 ID（ResourceLocation）。
     */
    @Nullable
    public static ResourceLocation getAttachmentRegistryName(@Nullable ItemStack attachmentStack) {
        if (!isAttachment(attachmentStack)) return null;
        return getAttachment(attachmentStack).getAttachmentId(attachmentStack);
    }

    /**
     * 获取配件的 {@link AttachmentData}（重量、修饰器等信息）。
     *
     * @param attachmentStack 配件物品
     * @return AttachmentData，若无法获取返回 null
     */
    @Nullable
    public static AttachmentData getAttachmentData(@Nullable ItemStack attachmentStack) {
        if (!isAttachment(attachmentStack)) return null;
        ResourceLocation id = getAttachmentRegistryName(attachmentStack);
        if (id == null) return null;
        return CommonAssetsManager.getInstance().getAttachmentData(id);
    }

    /**
     * 获取配件的 {@link CommonAttachmentIndex}（包含排序、显示名称等）。
     */
    @Nullable
    public static CommonAttachmentIndex getAttachmentIndex(@Nullable ItemStack attachmentStack) {
        if (!isAttachment(attachmentStack)) return null;
        ResourceLocation id = getAttachmentRegistryName(attachmentStack);
        if (id == null) return null;
        return CommonAssetsManager.getInstance().getAttachmentIndex(id);
    }

    /**
     * 获取配件的重量。若没有数据则返回 0。
     */
    public static float getAttachmentWeight(@Nullable ItemStack attachmentStack) {
        AttachmentData data = getAttachmentData(attachmentStack);
        return data != null ? data.getWeight() : 0f;
    }

    // ============================================================
    //  背包筛选 —— 从玩家背包找兼容配件
    // ============================================================

    /**
     * 从玩家背包中找出所有指定类型且兼容当前枪械的配件。
     *
     * @param player   玩家
     * @param gunStack 枪械
     * @param type     需要的配件类型
     * @return 兼容配件列表
     */
    @NotNull
    public static List<ItemStack> findCompatibleAttachments(@NotNull Player player, @NotNull ItemStack gunStack, @NotNull AttachmentType type) {
        if (!isGun(gunStack)) {
            return Collections.emptyList();
        }
        IGun gun = getGun(gunStack);
        List<ItemStack> result = new ArrayList<>();

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty() || !isAttachment(stack)) continue;
            if (getAttachmentType(stack) != type) continue;
            if (!gun.allowAttachment(gunStack, stack)) continue;
            result.add(stack.copy());
        }
        return result;
    }

    /**
     * 从任意 IItemHandler 中找出所有指定类型且兼容枪械的配件。
     */
    @NotNull
    public static List<ItemStack> findCompatibleAttachments(@NotNull IItemHandler inventory, @NotNull ItemStack gunStack, @NotNull AttachmentType type) {
        if (!isGun(gunStack)) return Collections.emptyList();
        IGun gun = getGun(gunStack);
        List<ItemStack> result = new ArrayList<>();

        for (int i = 0; i < inventory.getSlots(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (stack.isEmpty() || !isAttachment(stack)) continue;
            if (getAttachmentType(stack) != type) continue;
            if (gun.allowAttachment(gunStack, stack)) {
                result.add(stack.copy());
            }
        }
        return result;
    }

    /**
     * 找出玩家背包中所有与枪械兼容的配件，按 {@link AttachmentType} 分组。
     */
    @NotNull
    public static Map<AttachmentType, List<ItemStack>> findAllCompatibleAttachmentsGrouped(@NotNull Player player, @NotNull ItemStack gunStack) {
        if (!isGun(gunStack)) return Collections.emptyMap();
        IGun gun = getGun(gunStack);
        Map<AttachmentType, List<ItemStack>> result = new EnumMap<>(AttachmentType.class);

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty() || !isAttachment(stack)) continue;
            if (!gun.allowAttachment(gunStack, stack)) continue;

            AttachmentType type = getAttachmentType(stack);
            if (type == null || type == AttachmentType.NONE) continue;

            result.computeIfAbsent(type, k -> new ArrayList<>()).add(stack.copy());
        }
        return result;
    }

    /**
     * 统计玩家背包中各类型兼容配件的数量。
     */
    @NotNull
    public static Map<AttachmentType, Integer> countCompatibleAttachments(@NotNull Player player, @NotNull ItemStack gunStack) {
        if (!isGun(gunStack)) return Collections.emptyMap();
        IGun gun = getGun(gunStack);
        Map<AttachmentType, Integer> counts = new EnumMap<>(AttachmentType.class);

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty() || !isAttachment(stack)) continue;
            if (!gun.allowAttachment(gunStack, stack)) continue;

            AttachmentType type = getAttachmentType(stack);
            if (type != null && type != AttachmentType.NONE) {
                counts.merge(type, 1, Integer::sum);
            }
        }
        return counts;
    }

    // ============================================================
    //  配件锁
    // ============================================================

    /**
     * 判断枪械配件是否被锁定（锁定状态下不可拆卸/安装配件）。
     */
    public static boolean isAttachmentLocked(@NotNull ItemStack gunStack) {
        IGun gun = getGunOrNull(gunStack);
        return gun != null && gun.hasAttachmentLock(gunStack);
    }

    /**
     * 设置枪械的配件锁定状态。
     */
    public static void setAttachmentLock(@NotNull ItemStack gunStack, boolean locked) {
        IGun gun = getGunOrNull(gunStack);
        if (gun != null) {
            gun.setAttachmentLock(gunStack, locked);
        }
    }

    // ============================================================
    //  工具方法 —— 用于 UI 展示
    // ============================================================

    /**
     * 获取枪械的 UI 展示信息。
     * 返回一个 Map，便于 JSON 序列化传给前端。
     *
     * @return Map 包含枪械的基本信息和已安装配件列表
     */
    @NotNull
    public static Map<String, Object> getGunDisplayData(@NotNull ItemStack gunStack) {
        if (!isGun(gunStack)) return Collections.emptyMap();

        IGun gun = getGun(gunStack);
        Map<String, Object> data = new LinkedHashMap<>();

        // 基本信息
        data.put("registryName", getItemRegistryName(gunStack));
        data.put("displayName", gunStack.getHoverName().getString());
        data.put("gunId", Objects.toString(gun.getGunId(gunStack), ""));
        data.put("fireMode", Objects.toString(gun.getFireMode(gunStack), ""));
        data.put("ammoCount", gun.getCurrentAmmoCount(gunStack));
        data.put("attachmentLocked", gun.hasAttachmentLock(gunStack));

        // 配件信息
        Map<String, Object> attachments = new LinkedHashMap<>();
        for (AttachmentType type : AttachmentType.values()) {
            if (type == AttachmentType.NONE) continue;
            Map<String, Object> slotInfo = new LinkedHashMap<>();

            ResourceLocation builtinId = gun.getBuiltInAttachmentId(gunStack, type);
            ResourceLocation installedId = gun.getAttachmentId(gunStack, type);
            ResourceLocation effectiveId = installedId != null ? installedId : builtinId;

            slotInfo.put("supported", gun.allowAttachmentType(gunStack, type));
            slotInfo.put("hasBuiltin", builtinId != null);
            slotInfo.put("hasInstalled", installedId != null);
            slotInfo.put("installedId", Objects.toString(installedId, ""));
            slotInfo.put("builtinId", Objects.toString(builtinId, ""));
            slotInfo.put("effectiveId", Objects.toString(effectiveId, ""));

            attachments.put(type.name().toLowerCase(), slotInfo);
        }
        data.put("attachments", attachments);

        return data;
    }

    /**
     * 获取选中的展示信息分组，用于 UI 的"配件列表"页面。
     *
     * @param player   玩家
     * @param gunStack 枪械
     * @return 分组后的配件列表，key 为配件类型的显示名称
     */
    @NotNull
    public static Map<String, Object> getAttachmentSelectionData(@NotNull Player player, @NotNull ItemStack gunStack) {
        if (!isGun(gunStack)) return Collections.emptyMap();

        Map<String, Object> result = new LinkedHashMap<>();

        // 当前安装的配件
        Map<String, Object> currentAttachments = new LinkedHashMap<>();
        for (Map.Entry<AttachmentType, ItemStack> entry : getAllAttachments(gunStack).entrySet()) {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("type", entry.getKey().name());
            info.put("registryName", getItemRegistryName(entry.getValue()));
            info.put("displayName", entry.getValue().getHoverName().getString());
            currentAttachments.put(entry.getKey().name().toLowerCase(), info);
        }
        result.put("current", currentAttachments);

        // 背包中可用的兼容配件
        Map<String, Object> available = new LinkedHashMap<>();
        Map<AttachmentType, List<ItemStack>> grouped = findAllCompatibleAttachmentsGrouped(player, gunStack);
        for (Map.Entry<AttachmentType, List<ItemStack>> entry : grouped.entrySet()) {
            List<Map<String, Object>> attachmentList = entry.getValue().stream().map(stack -> {
                Map<String, Object> info = new LinkedHashMap<>();
                info.put("registryName", getItemRegistryName(stack));
                info.put("displayName", stack.getHoverName().getString());
                info.put("count", stack.getCount());
                return info;
            }).collect(Collectors.toList());
            available.put(entry.getKey().name().toLowerCase(), attachmentList);
        }
        result.put("available", available);

        // 所有支持的槽位
        List<String> supportedSlots = getSupportedAttachmentTypes(gunStack)
                .stream()
                .map(AttachmentType::name)
                .collect(Collectors.toList());
        result.put("supportedSlots", supportedSlots);

        return result;
    }

    public static boolean isEmptyAttachmentId(@Nullable ResourceLocation id) {
        return id == null || EMPTY_ATTACHMENT_ID.equals(id);
    }
}
