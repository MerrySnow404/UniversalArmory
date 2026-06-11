package com.sighs.universalarmory.client.render;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.resource.GunDisplayInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import java.util.Optional;

/**
 * 备用方案：
 * 用第一人称物品渲染上下文，把枪固定挪到屏幕中间做侧视预览。
 * 这条路不依赖单独的枪模界面逻辑，后续可以替换现有缓存烘焙入口做实验。
 */
public final class FirstPersonStyleGunPreviewRenderer {

    private static final FirstPersonStyleGunPreviewRenderer INSTANCE = new FirstPersonStyleGunPreviewRenderer();

    private static final float PITCH_DEGREES = 0.0F;
    private static final float YAW_DEGREES = -90.0F;
    private static final float ROLL_DEGREES = 0.0F;
    private static final float OFFSET_X = 0.0F;
    private static final float OFFSET_Y = 0.0F;
    private static final float OFFSET_Z = 160.0F;

    private FirstPersonStyleGunPreviewRenderer() {
    }

    @NotNull
    public static FirstPersonStyleGunPreviewRenderer getInstance() {
        return INSTANCE;
    }

    public void renderPreview(@NotNull PoseStack poseStack,
                              @NotNull MultiBufferSource.BufferSource bufferSource,
                              @NotNull ItemStack gunStack,
                              int x,
                              int y,
                              int width,
                              int height) {
        if (gunStack.isEmpty()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Optional<GunDisplayInstance> gunDisplay = TimelessAPI.getGunDisplay(gunStack);
        GunDisplayInstance displayInstance = gunDisplay.orElse(null);
        if (displayInstance == null) {
            return;
        }
        BedrockGunModel gunModel = displayInstance.getGunModel();
        if (gunModel == null) {
            return;
        }

        float scale = Math.max(28.0F, Math.min(width, height) * 0.92F);
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
    }
}
