package com.sighs.universalarmory.client.render;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.resource.GunDisplayInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.Optional;

public final class FirstPersonStyleGunPreviewRenderer {

    private static final Logger LOGGER = LoggerFactory.getLogger(FirstPersonStyleGunPreviewRenderer.class);
    private static final FirstPersonStyleGunPreviewRenderer INSTANCE = new FirstPersonStyleGunPreviewRenderer();

    private static final float PITCH_DEGREES = 0.0F;
    private static final float YAW_DEGREES = -90.0F;
    private static final float ROLL_DEGREES = 0.0F;
    private static final float OFFSET_X = -28.0F;
    private static final float OFFSET_Y = 24.0F;
    private static final float OFFSET_Z = 160.0F;
    private static final Field CURRENT_GUN_ITEM_FIELD = findField("currentGunItem");
    private static final Field CURRENT_EXTEND_MAG_LEVEL_FIELD = findField("currentExtendMagLevel");
    private static final Field RENDER_MOUNT_FIELD = findField("renderMount");
    private static final Field ADAPTER_TO_RENDER_FIELD = findField("adapterToRender");

    private FirstPersonStyleGunPreviewRenderer() {
    }

    @NotNull
    public static FirstPersonStyleGunPreviewRenderer getInstance() {
        return INSTANCE;
    }

    public boolean renderPreview(@NotNull PoseStack poseStack,
                                 @NotNull MultiBufferSource.BufferSource bufferSource,
                                 @NotNull ItemStack gunStack,
                                 int x,
                                 int y,
                                 int width,
                                 int height) {
        if (gunStack.isEmpty()) {
            LOGGER.info("Gun preview skip: empty stack");
            return false;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Optional<GunDisplayInstance> gunDisplay = resolveGunDisplay(gunStack);
        GunDisplayInstance displayInstance = gunDisplay.orElse(null);
        if (displayInstance == null) {
            LOGGER.info("Gun preview skip: missing display item={} tag={}",
                    net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(gunStack.getItem()), gunStack.getTag());
            return false;
        }
        BedrockGunModel gunModel = displayInstance.getGunModel();
        if (gunModel == null) {
            LOGGER.info("Gun preview skip: missing model item={} tag={}",
                    net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(gunStack.getItem()), gunStack.getTag());
            return false;
        }

        float scale = Math.max(1.0F, Math.min(width * 0.20F, height * 0.60F));
        float centerX = x + width * 0.5F + OFFSET_X;
        float centerY = y + height * 0.5F + OFFSET_Y;

        poseStack.pushPose();
        poseStack.translate(centerX, centerY, OFFSET_Z);
        poseStack.scale(scale, -scale, scale);
        poseStack.mulPose(Axis.ZP.rotationDegrees(ROLL_DEGREES));
        poseStack.mulPose(Axis.XP.rotationDegrees(PITCH_DEGREES));
        poseStack.mulPose(Axis.YP.rotationDegrees(YAW_DEGREES));

        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
        Lighting.setupForFlatItems();
        boolean restoreRenderHand = gunModel.getRenderHand();
        gunModel.setRenderHand(false);
        try {
            poseStack.translate(0.0F, 1.5F, 0.0F);
            poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));

            RenderType renderType = displayInstance.enablesTransparency()
                    ? RenderType.entityTranslucent(displayInstance.getModelTexture())
                    : RenderType.entityCutout(displayInstance.getModelTexture());
            gunModel.render(
                    poseStack,
                    gunStack,
                    ItemDisplayContext.FIXED,
                    renderType,
                    LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY
            );
        } finally {
            gunModel.setRenderHand(restoreRenderHand);
            gunModel.cleanAnimationTransform();
        }
        bufferSource.endBatch();
        Lighting.setupFor3DItems();
        RenderSystem.disableBlend();
        poseStack.popPose();
        LOGGER.info("Gun preview rendered item={} tag={} displayTexture={}",
                net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(gunStack.getItem()),
                gunStack.getTag(),
                displayInstance.getModelTexture());
        return true;
    }

    @NotNull
    private Optional<GunDisplayInstance> resolveGunDisplay(@NotNull ItemStack gunStack) {
        Optional<GunDisplayInstance> display = TimelessAPI.getGunDisplay(gunStack);
        if (display.isPresent()) {
            LOGGER.info("Gun preview display resolved from stack item={} tag={}",
                    net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(gunStack.getItem()), gunStack.getTag());
            return display;
        }

        IGun gun = IGun.getIGunOrNull(gunStack);
        if (gun == null) {
            LOGGER.info("Gun preview display resolve failed: stack is not IGun item={} tag={}",
                    net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(gunStack.getItem()), gunStack.getTag());
            return Optional.empty();
        }

        ResourceLocation gunId = gun.getGunId(gunStack);
        ResourceLocation displayId = gun.getGunDisplayId(gunStack);
        if (displayId == null) {
            LOGGER.info("Gun preview display resolve failed: missing displayId gunId={} item={} tag={}",
                    gunId, net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(gunStack.getItem()), gunStack.getTag());
            return Optional.empty();
        }
        Optional<GunDisplayInstance> fallback = TimelessAPI.getGunDisplay(displayId, gunId);
        LOGGER.info("Gun preview display fallback gunId={} displayId={} commonIndex={} resolved={}",
                gunId, displayId, TimelessAPI.getCommonGunIndex(gunId).isPresent(), fallback.isPresent());
        return fallback;
    }

    @NotNull
    private static Field findField(@NotNull String fieldName) {
        try {
            Field field = BedrockGunModel.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Missing BedrockGunModel field " + fieldName, exception);
        }
    }
}
