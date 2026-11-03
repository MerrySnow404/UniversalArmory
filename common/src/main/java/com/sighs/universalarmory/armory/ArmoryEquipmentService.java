package com.sighs.universalarmory.armory;

import com.sighs.universalarmory.block.entity.ArmoryBlockEntity;
import com.sighs.universalarmory.template.GunTemplateCodecs;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.util.AttachmentDataUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ArmoryEquipmentService {
    private ArmoryEquipmentService() {
    }

    public static Result perform(ServerPlayer player, ArmoryBlockEntity armory, String action, String entryId, CompoundTag operation) {
        ArmoryTransaction transaction = new ArmoryTransaction(player, armory);
        ItemStack result;
        BigInteger count = BigInteger.ONE;
        if (action.equals("craft")) {
            Recipe<?> recipe = recipe(player, operation.getString("Recipe"));
            int batches = operation.getInt("Batches");
            if (batches < 1 || batches > 64) {
                throw new IllegalArgumentException("单次制作批数必须为 1 至 64，可重复制作");
            }
            result = recipe.getResultItem(player.serverLevel().registryAccess()).copy();
            if (result.isEmpty() || ArmoryItemIdentity.kind(result) == ArmoryItemIdentity.Kind.OTHER) {
                throw new IllegalArgumentException("该配方不是军备配方");
            }
            count = BigInteger.valueOf(result.getCount()).multiply(BigInteger.valueOf(batches));
            result.setCount(1);
            transaction.consume(ingredients(recipe, batches));
            String code = operation.getString("Code");
            if (!code.isBlank()) {
                List<ArmoryTransaction.Requirement> requirements = new ArrayList<>();
                configure(result, decode(code), transaction, requirements, count.longValueExact());
                transaction.consume(requirements);
            }
        } else {
            result = transaction.storage().withdraw(entryId, 1);
            IGun gun = IGun.getIGunOrNull(result);
            if (gun == null) {
                throw new IllegalArgumentException("库存中的目标枪械已不存在，请刷新");
            }
            if (action.equals("reload")) {
                reload(result, transaction);
            } else {
                if (gun.hasAttachmentLock(result)) {
                    throw new IllegalArgumentException("该枪械已锁定配件改装");
                }
                switch (action) {
                    case "install" -> {
                        ResourceLocation id = parseId(operation.getString("Attachment"));
                        ItemStack attachment = transaction.find(stack -> {
                            IAttachment item = IAttachment.getIAttachmentOrNull(stack);
                            return item != null && id.equals(item.getAttachmentId(stack));
                        });
                        if (attachment.isEmpty()) {
                            throw new IllegalArgumentException("库存和背包中没有该配件");
                        }
                        List<ArmoryTransaction.Requirement> requirements = new ArrayList<>();
                        replaceAttachment(result, attachment, IAttachment.getIAttachmentOrNull(attachment).getType(attachment),
                                transaction, requirements, 1);
                        transaction.consume(requirements);
                    }
                    case "uninstall" -> {
                        AttachmentType type = AttachmentType.valueOf(operation.getString("Slot"));
                        if (type == AttachmentType.NONE) {
                            throw new IllegalArgumentException("无效的配件槽位");
                        }
                        ItemStack removed = gun.getAttachment(result, type).copy();
                        if (removed.isEmpty()) {
                            throw new IllegalArgumentException("该槽位没有可拆卸配件");
                        }
                        gun.unloadAttachment(result, type);
                        transaction.storage().deposit(removed);
                    }
                    case "apply-code" -> {
                        List<ArmoryTransaction.Requirement> requirements = new ArrayList<>();
                        configure(result, decode(operation.getString("Code")), transaction, requirements, 1);
                        transaction.consume(requirements);
                    }
                    default -> throw new IllegalArgumentException("未知的军备操作");
                }
                returnExcessAmmo(result, transaction, 1);
            }
        }
        String key = ArmoryItemIdentity.key(result);
        String id = transaction.storage().deposit(result, count);
        transaction.commit();
        return new Result(result.copy(), id, key, count);
    }

    public static List<Recipe<?>> recipesFor(ServerPlayer player, ItemStack target) {
        Map<ResourceLocation, Recipe<?>> recipes = new LinkedHashMap<>();
        TimelessAPI.getAllRecipes().forEach(recipes::put);
        for (Recipe<?> recipe : player.serverLevel().getRecipeManager().getRecipes()) {
            if (recipe instanceof GunSmithTableRecipe || recipe.getType() == RecipeType.CRAFTING) {
                recipes.put(recipe.getId(), recipe);
            }
        }
        return recipes.values().stream().filter(recipe -> {
            ItemStack output = recipe.getResultItem(player.serverLevel().registryAccess());
            return !output.isEmpty() && ArmoryItemIdentity.kind(output) == ArmoryItemIdentity.kind(target)
                    && Objects.equals(ArmoryItemIdentity.resourceId(output), ArmoryItemIdentity.resourceId(target));
        }).sorted(java.util.Comparator.comparing(recipe -> recipe.getId().toString())).toList();
    }

    public static List<ArmoryTransaction.Requirement> ingredients(Recipe<?> recipe, int batches) {
        List<ArmoryTransaction.Requirement> result = new ArrayList<>();
        if (recipe instanceof GunSmithTableRecipe gunRecipe) {
            for (var input : gunRecipe.getInputs()) {
                result.add(new ArmoryTransaction.Requirement(input.getIngredient()::test, Math.multiplyExact((long) input.getCount(), batches)));
            }
        } else {
            recipe.getIngredients().stream().filter(ingredient -> !ingredient.isEmpty()).forEach(ingredient ->
                    result.add(new ArmoryTransaction.Requirement(ingredient::test, batches)));
        }
        return result;
    }

    private static Recipe<?> recipe(ServerPlayer player, String id) {
        ResourceLocation location = parseId(id);
        Recipe<?> recipe = TimelessAPI.getRecipe(location).orElse(null);
        if (recipe == null) {
            recipe = player.serverLevel().getRecipeManager().byKey(location).orElse(null);
        }
        if (recipe == null || !(recipe instanceof GunSmithTableRecipe) && recipe.getType() != RecipeType.CRAFTING) {
            throw new IllegalArgumentException("该军备配方不存在或不受支持");
        }
        return recipe;
    }

    private static ItemStack decode(String code) {
        if (code.isBlank() || code.length() > 65536) {
            throw new IllegalArgumentException("无效的改枪码长度");
        }
        return GunTemplateCodecs.share().createPreviewStack(GunTemplateCodecs.share().importCode(code));
    }

    private static void configure(ItemStack actual, ItemStack desired, ArmoryTransaction transaction,
                                  List<ArmoryTransaction.Requirement> requirements, long copies) {
        IGun gun = IGun.getIGunOrNull(actual);
        IGun target = IGun.getIGunOrNull(desired);
        if (gun == null || target == null || !Objects.equals(gun.getGunId(actual), target.getGunId(desired))) {
            throw new IllegalArgumentException("改枪码与目标枪械型号不一致");
        }
        if (gun.hasAttachmentLock(actual)) {
            throw new IllegalArgumentException("该枪械已锁定配件改装");
        }
        for (AttachmentType type : AttachmentType.values()) {
            if (type == AttachmentType.NONE) {
                continue;
            }
            ItemStack old = gun.getAttachment(actual, type);
            ItemStack replacement = target.getAttachment(desired, type);
            if (Objects.equals(attachmentId(old), attachmentId(replacement))) {
                continue;
            }
            replaceAttachment(actual, replacement, type, transaction, requirements, copies);
        }
        ResourceLocation display = target.getGunDisplayId(desired);
        if (display != null) {
            gun.setGunDisplayId(actual, display);
        }
        gun.setAttachmentLock(actual, target.hasAttachmentLock(desired));
        returnExcessAmmo(actual, transaction, copies);
    }

    private static void replaceAttachment(ItemStack actual, ItemStack replacement, AttachmentType type,
                                          ArmoryTransaction transaction, List<ArmoryTransaction.Requirement> requirements, long copies) {
        IGun gun = Objects.requireNonNull(IGun.getIGunOrNull(actual));
        ItemStack previous = gun.getAttachment(actual, type).copy();
        if (replacement.isEmpty()) {
            gun.unloadAttachment(actual, type);
        } else {
            if (!gun.allowAttachmentType(actual, type) || !gun.allowAttachment(actual, replacement)) {
                throw new IllegalArgumentException("配件与枪械不兼容：" + attachmentId(replacement));
            }
            requirements.add(new ArmoryTransaction.Requirement(stack -> ItemStack.isSameItemSameTags(stack, replacement), copies));
            gun.installAttachment(actual, replacement);
        }
        if (!previous.isEmpty()) {
            transaction.storage().deposit(previous, BigInteger.valueOf(copies));
        }
    }

    private static void reload(ItemStack stack, ArmoryTransaction transaction) {
        IGun gun = Objects.requireNonNull(IGun.getIGunOrNull(stack));
        if (gun.useDummyAmmo(stack) || gun.useInventoryAmmo(stack)) {
            throw new IllegalArgumentException("该枪械使用特殊或背包弹药，请使用对应类型的弹药补给");
        }
        GunData data = gunData(stack);
        int capacity = AttachmentDataUtils.getAmmoCountWithAttachment(stack, data);
        int missing = Math.max(0, capacity - gun.getCurrentAmmoCount(stack));
        if (missing == 0) {
            throw new IllegalArgumentException("弹匣已满");
        }
        transaction.consume(List.of(new ArmoryTransaction.Requirement(ammo -> {
            IAmmo item = IAmmo.getIAmmoOrNull(ammo);
            return item != null && data.getAmmoId().equals(item.getAmmoId(ammo));
        }, missing)));
        gun.setCurrentAmmoCount(stack, capacity);
    }

    private static void returnExcessAmmo(ItemStack stack, ArmoryTransaction transaction, long copies) {
        IGun gun = Objects.requireNonNull(IGun.getIGunOrNull(stack));
        if (gun.useDummyAmmo(stack) || gun.useInventoryAmmo(stack)) {
            return;
        }
        GunData data = gunData(stack);
        int capacity = AttachmentDataUtils.getAmmoCountWithAttachment(stack, data);
        if (capacity < 0) {
            throw new IllegalArgumentException("枪械弹匣容量数据无效");
        }
        int excess = Math.max(0, gun.getCurrentAmmoCount(stack) - capacity);
        if (excess > 0) {
            ItemStack ammo = AmmoItemBuilder.create().setId(data.getAmmoId()).setCount(1).build();
            transaction.storage().deposit(ammo, BigInteger.valueOf(excess).multiply(BigInteger.valueOf(copies)));
            gun.setCurrentAmmoCount(stack, capacity);
        }
    }

    private static GunData gunData(ItemStack stack) {
        IGun gun = Objects.requireNonNull(IGun.getIGunOrNull(stack));
        GunData data = TimelessAPI.getCommonGunIndex(gun.getGunId(stack)).orElseThrow(() ->
                new IllegalArgumentException("枪械数据未加载")).getGunData();
        if (data == null) {
            throw new IllegalArgumentException("枪械数据未加载");
        }
        return data;
    }

    private static ResourceLocation attachmentId(ItemStack stack) {
        IAttachment attachment = IAttachment.getIAttachmentOrNull(stack);
        return attachment == null ? null : attachment.getAttachmentId(stack);
    }

    private static ResourceLocation parseId(String text) {
        ResourceLocation result = ResourceLocation.tryParse(text);
        if (result == null) {
            throw new IllegalArgumentException("无效的资源 ID");
        }
        return result;
    }

    public record Result(ItemStack stack, String inventoryId, String key, BigInteger quantity) { }
}
