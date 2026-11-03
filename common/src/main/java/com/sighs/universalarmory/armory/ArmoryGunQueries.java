package com.sighs.universalarmory.armory;

import com.sighs.universalarmory.gui.util.AttachmentUtils;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.init.ModItems;
import com.tacz.guns.item.AttachmentItem;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.client.resource.index.ClientGunIndex;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;

public final class ArmoryGunQueries {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArmoryGunQueries.class);

    private ArmoryGunQueries() {
    }

    @NotNull
    public static List<ItemStack> getPlayerGuns(@NotNull Player player) {
        List<ItemStack> guns = new ArrayList<>();
        Set<Integer> visitedSlots = new HashSet<>();

        addGunIfPresent(guns, player.getMainHandItem());
        addGunIfPresent(guns, player.getOffhandItem());

        int selectedSlot = player.getInventory().selected;
        addGunFromSlot(player, guns, visitedSlots, selectedSlot);

        for (int slot = 0; slot < 9; slot++) {
            if (slot == selectedSlot) {
                continue;
            }
            addGunFromSlot(player, guns, visitedSlots, slot);
        }

        for (int slot = 9; slot < player.getInventory().getContainerSize(); slot++) {
            addGunFromSlot(player, guns, visitedSlots, slot);
        }

        return guns;
    }

    @NotNull
    public static List<ItemStack> getContainerGuns(@NotNull Container container) {
        List<ItemStack> guns = new ArrayList<>();
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (AttachmentUtils.isGun(stack)) {
                guns.add(stack.copy());
            }
        }
        return guns;
    }

    @NotNull
    public static List<ItemStack> getContainerGuns(@NotNull ArmoryInstance armory) {
        return armory.getAllGuns();
    }

    @NotNull
    public static List<ItemStack> getCraftableGuns(@NotNull Level level) {
        Map<String, ItemStack> uniqueGuns = new LinkedHashMap<>();
        for (Recipe<?> recipe : level.getRecipeManager().getRecipes()) {
            ItemStack result = recipe.getResultItem(level.registryAccess());
            if (result.isEmpty() || !AttachmentUtils.isGun(result)) {
                continue;
            }
            uniqueGuns.putIfAbsent(buildKey(result), result.copy());
        }

        List<ItemStack> guns = new ArrayList<>(uniqueGuns.values());
        guns.sort((left, right) -> {
            String leftId = Objects.toString(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(left.getItem()), "");
            String rightId = Objects.toString(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(right.getItem()), "");
            return leftId.compareTo(rightId);
        });
        return guns;
    }

    @NotNull
    public static List<ItemStack> getClientIndexedGuns() {
        List<ItemStack> guns = new ArrayList<>();
        Set<Entry<net.minecraft.resources.ResourceLocation, ClientGunIndex>> clientGunEntries = TimelessAPI.getAllClientGunIndex();
        LOGGER.info("Armory client gun index count={}", clientGunEntries.size());
        for (Entry<net.minecraft.resources.ResourceLocation, ClientGunIndex> entry : clientGunEntries) {
            LOGGER.info("Armory client gun index entry gunId={} name={} type={} itemType={}",
                    entry.getKey(), entry.getValue().getName(), entry.getValue().getType(), entry.getValue().getItemType());
            ItemStack stack = createIndexedGunStack(entry.getKey(), entry.getValue());
            if (!stack.isEmpty()) {
                LOGGER.info("Armory created indexed gun stack gunId={} item={} displayId={} tag={}",
                        entry.getKey(),
                        ForgeRegistries.ITEMS.getKey(stack.getItem()),
                        IGun.getIGunOrNull(stack) != null ? IGun.getIGunOrNull(stack).getGunDisplayId(stack) : null,
                        stack.getTag());
                guns.add(stack);
            } else {
                LOGGER.warn("Armory failed to create indexed gun stack for gunId={}", entry.getKey());
            }
        }

        guns.sort((left, right) -> {
            String leftId = Objects.toString(ForgeRegistries.ITEMS.getKey(left.getItem()), "");
            String rightId = Objects.toString(ForgeRegistries.ITEMS.getKey(right.getItem()), "");
            return leftId.compareTo(rightId);
        });
        return guns;
    }

    @NotNull
    public static List<ItemStack> getRandomCraftableGuns(@NotNull Level level, int amount) {
        if (amount <= 0) {
            return Collections.emptyList();
        }

        List<ItemStack> guns = getCraftableGuns(level);
        Collections.shuffle(guns);
        if (guns.size() <= amount) {
            return guns;
        }
        return new ArrayList<>(guns.subList(0, amount));
    }

    @NotNull
    private static String buildKey(@NotNull ItemStack stack) {
        return Objects.toString(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()), "")
                + "#"
                + Objects.toString(stack.getTag(), "");
    }

    @NotNull
    private static ItemStack createIndexedGunStack(@NotNull net.minecraft.resources.ResourceLocation gunId, @NotNull ClientGunIndex gunIndex) {
        Item item = resolveGunItem(gunIndex);
        if (item == null) {
            LOGGER.warn("Armory createIndexedGunStack missing item for gunId={} name={} type={} itemType={}",
                    gunId, gunIndex.getName(), gunIndex.getType(), gunIndex.getItemType());
            return ItemStack.EMPTY;
        }

        ItemStack stack = new ItemStack(item);
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null) {
            LOGGER.warn("Armory createIndexedGunStack item is not IGun gunId={} item={}",
                    gunId, ForgeRegistries.ITEMS.getKey(item));
            return ItemStack.EMPTY;
        }

        gun.setGunId(stack, gunId);
        net.minecraft.resources.ResourceLocation displayId = resolveDisplayId(gunId);
        if (displayId != null) {
            gun.setGunDisplayId(stack, displayId);
            LOGGER.info("Armory createIndexedGunStack set missing displayId to gunId={} item={}",
                    gunId, ForgeRegistries.ITEMS.getKey(item));
        }
        if (gun.getGunDisplayId(stack) == null && gunIndex.getDefaultDisplay() != null) {
            gun.setGunDisplayId(stack, gunId);
            LOGGER.info("Armory createIndexedGunStack retried displayId assignment gunId={} item={}",
                    gunId, ForgeRegistries.ITEMS.getKey(item));
        }
        LOGGER.info("Armory createIndexedGunStack result gunId={} item={} finalDisplayId={} hoverName={}",
                gunId,
                ForgeRegistries.ITEMS.getKey(item),
                gun.getGunDisplayId(stack),
                stack.getHoverName().getString());
        return stack;
    }

    @NotNull
    private static Item resolveGunItem(@NotNull ClientGunIndex gunIndex) {
        String itemType = gunIndex.getItemType();
        if (Objects.equals(itemType, "modern_kinetic")) {
            return ModItems.MODERN_KINETIC_GUN.get();
        }

        net.minecraft.resources.ResourceLocation itemId = new net.minecraft.resources.ResourceLocation("tacz", itemType + "_gun");
        Item registryItem = ForgeRegistries.ITEMS.getValue(itemId);
        return registryItem != null ? registryItem : net.minecraft.world.item.Items.AIR;
    }

    private static net.minecraft.resources.ResourceLocation resolveDisplayId(@NotNull net.minecraft.resources.ResourceLocation gunId) {
        return TimelessAPI.getCommonGunIndex(gunId)
                .map(CommonGunIndex::getPojo)
                .map(com.tacz.guns.resource.pojo.GunIndexPOJO::getDisplay)
                .orElse(null);
    }

    private static void fillPreviewAttachments(@NotNull ItemStack gunStack) {
        if (!AttachmentUtils.isGun(gunStack)) {
            LOGGER.warn("Armory preview attachment skip: stack is not gun item={} tag={}",
                    ForgeRegistries.ITEMS.getKey(gunStack.getItem()), gunStack.getTag());
            return;
        }

        IGun gun = IGun.getIGunOrNull(gunStack);
        net.minecraft.resources.ResourceLocation gunId = gun != null ? gun.getGunId(gunStack) : null;
        if (gun == null || gunId == null) {
            LOGGER.warn("Armory preview attachment skip: missing gun interface or gunId item={} tag={}",
                    ForgeRegistries.ITEMS.getKey(gunStack.getItem()), gunStack.getTag());
            return;
        }

        CommonGunIndex gunIndex = TimelessAPI.getCommonGunIndex(gunId).orElse(null);
        if (gunIndex == null) {
            LOGGER.warn("Armory preview attachment skip: missing common gun index gunId={} tag={}", gunId, gunStack.getTag());
            return;
        }

        GunData gunData = gunIndex.getGunData();
        if (gunData == null) {
            LOGGER.warn("Armory preview attachment skip: missing gun data gunId={} tag={}", gunId, gunStack.getTag());
            return;
        }

        List<AttachmentType> allowedTypes = gunData.getAllowAttachments();
        LOGGER.info("Armory preview attachment start gunId={} allowedTypes={} initialTag={}",
                gunId, allowedTypes, gunStack.getTag());
        if (allowedTypes == null || allowedTypes.isEmpty()) {
            LOGGER.warn("Armory preview attachment no allowed slots gunId={} tag={}", gunId, gunStack.getTag());
            return;
        }

        for (AttachmentType type : allowedTypes) {
            if (type == null || type == AttachmentType.NONE) {
                LOGGER.info("Armory preview attachment skip invalid slot gunId={} slot={}", gunId, type);
                continue;
            }
            if (AttachmentUtils.hasAttachment(gunStack, type) || AttachmentUtils.hasBuiltinAttachment(gunStack, type)) {
                LOGGER.info("Armory preview attachment skip occupied slot gunId={} slot={} installed={} builtin={}",
                        gunId,
                        type,
                        AttachmentUtils.getAttachmentId(gunStack, type),
                        AttachmentUtils.getBuiltinAttachmentId(gunStack, type));
                continue;
            }

            List<ItemStack> candidates = AttachmentItem.fillItemCategory(type);
            LOGGER.info("Armory preview attachment candidates gunId={} slot={} count={}", gunId, type, candidates.size());
            boolean installedAny = false;
            for (ItemStack candidate : candidates) {
                if (candidate.isEmpty()) {
                    LOGGER.info("Armory preview attachment skip empty candidate gunId={} slot={}", gunId, type);
                    continue;
                }

                IAttachment attachment = IAttachment.getIAttachmentOrNull(candidate);
                net.minecraft.resources.ResourceLocation attachmentId = attachment != null ? attachment.getAttachmentId(candidate) : null;
                boolean canAttach = AttachmentUtils.canAttach(gunStack, candidate);
                LOGGER.info("Armory preview attachment try gunId={} slot={} attachment={} canAttach={} candidateTag={}",
                        gunId, type, attachmentId, canAttach, candidate.getTag());
                if (!canAttach) {
                    continue;
                }

                ItemStack toInstall = candidate.copy();
                toInstall.setCount(1);
                boolean installed = AttachmentUtils.installAttachmentLikeRefit(gunStack, toInstall, null);
                LOGGER.info("Armory preview attachment result gunId={} slot={} attachment={} installed={} installedId={} installedTag={}",
                        gunId,
                        type,
                        attachmentId,
                        installed,
                        AttachmentUtils.getAttachmentId(gunStack, type),
                        AttachmentUtils.getAttachmentTag(gunStack, type));
                if (installed) {
                    installedAny = true;
                    break;
                }
            }

            if (!installedAny) {
                LOGGER.warn("Armory preview attachment failed to install any candidate gunId={} slot={} finalSlotId={}",
                        gunId, type, AttachmentUtils.getAttachmentId(gunStack, type));
            }
        }

        LOGGER.info("Armory preview attachment complete gunId={} finalAttachments={} finalTag={}",
                gunId, AttachmentUtils.getAllAttachmentIds(gunStack), gunStack.getTag());
    }

    private static void addGunFromSlot(@NotNull Player player, @NotNull List<ItemStack> guns, @NotNull Set<Integer> visitedSlots, int slot) {
        if (!visitedSlots.add(slot)) {
            return;
        }
        ItemStack stack = player.getInventory().getItem(slot);
        addGunIfPresent(guns, stack);
    }

    private static void addGunIfPresent(@NotNull List<ItemStack> guns, @NotNull ItemStack stack) {
        if (AttachmentUtils.isGun(stack)) {
            guns.add(stack.copy());
        }
    }
}
