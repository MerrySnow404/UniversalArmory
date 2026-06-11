package com.sighs.universalarmory.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.math.Axis;
import com.sighs.universalarmory.Universalarmory;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import com.mojang.blaze3d.platform.Lighting;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

public final class GunRenderCacheQueue {

    private static final GunRenderCacheQueue INSTANCE = new GunRenderCacheQueue();
    private static final int CACHE_TEXTURE_WIDTH = 768;
    private static final int CACHE_TEXTURE_HEIGHT = 384;
    private static final float CACHE_SCALE = 56.0F;
    private static final int MAX_CACHE_SIZE = 64;

    private final Map<GunRenderCacheKey, CacheEntry> cache = new LinkedHashMap<>();
    private final Queue<GunRenderCacheKey> pendingQueue = new ArrayDeque<>();
    private final Set<GunRenderCacheKey> queuedKeys = new HashSet<>();
    private final FirstPersonStyleGunPreviewRenderer firstPersonPreviewRenderer = FirstPersonStyleGunPreviewRenderer.getInstance();

    private GunRenderCacheQueue() {
    }

    @NotNull
    public static GunRenderCacheQueue getInstance() {
        return INSTANCE;
    }

    public void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || pendingQueue.isEmpty()) {
            return;
        }

        GunRenderCacheKey key = pendingQueue.poll();
        if (key == null) {
            return;
        }
        queuedKeys.remove(key);

        CacheEntry entry = cache.get(key);
        if (entry == null) {
            return;
        }
        if (entry.status == CacheStatus.READY || entry.status == CacheStatus.RENDERING) {
            return;
        }

        entry.status = CacheStatus.RENDERING;
        try {
            bakeEntry(minecraft, key, entry);
            entry.status = CacheStatus.READY;
        } catch (PreviewNotReadyException ignored) {
            entry.status = CacheStatus.PENDING;
            if (queuedKeys.add(key)) {
                pendingQueue.offer(key);
            }
        } catch (Exception exception) {
            entry.status = CacheStatus.FAILED;
        }
        entry.lastAccessTick = minecraft.level.getGameTime();
        trimCacheIfNeeded(minecraft);
    }

    public void renderPreview(@NotNull GuiGraphics guiGraphics, @NotNull ItemStack gunStack, int x, int y, int width, int height) {
        renderPreview(guiGraphics, request(gunStack), x, y, width, height);
    }

    public void renderPreview(@NotNull GuiGraphics guiGraphics, @Nullable CacheEntry entry, int x, int y, int width, int height) {
        if (entry != null && entry.textureLocation != null && entry.status == CacheStatus.READY) {
            BlitRect rect = computeBlitRect(entry, x, y, width, height);
            RenderSystem.enableBlend();
            guiGraphics.blit(entry.textureLocation, rect.drawX, rect.drawY, rect.drawWidth, rect.drawHeight,
                    rect.sourceX, rect.sourceY, rect.sourceWidth, rect.sourceHeight, CACHE_TEXTURE_WIDTH, CACHE_TEXTURE_HEIGHT);
            RenderSystem.disableBlend();
            return;
        }

        guiGraphics.fill(x, y, x + width, y + height, 0x55222222);
        guiGraphics.drawCenteredString(Minecraft.getInstance().font, "...", x + width / 2, y + Math.max((height - 8) / 2, 0), 0xC0C0C0);
    }

    public void renderPreview(@NotNull PoseStack poseStack, @Nullable CacheEntry entry, int x, int y, int width, int height) {
        if (entry == null || entry.textureLocation == null || entry.status != CacheStatus.READY) {
            return;
        }

        BlitRect rect = computeBlitRect(entry, x, y, width, height);
        float u0 = (float) rect.sourceX / (float) CACHE_TEXTURE_WIDTH;
        float v0 = (float) rect.sourceY / (float) CACHE_TEXTURE_HEIGHT;
        float u1 = (float) (rect.sourceX + rect.sourceWidth) / (float) CACHE_TEXTURE_WIDTH;
        float v1 = (float) (rect.sourceY + rect.sourceHeight) / (float) CACHE_TEXTURE_HEIGHT;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, entry.textureLocation);

        Matrix4f matrix = poseStack.last().pose();
        BufferBuilder bufferBuilder = Tesselator.getInstance().getBuilder();
        bufferBuilder.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        bufferBuilder.vertex(matrix, rect.drawX, rect.drawY + rect.drawHeight, 0.0F).uv(u0, v1).endVertex();
        bufferBuilder.vertex(matrix, rect.drawX + rect.drawWidth, rect.drawY + rect.drawHeight, 0.0F).uv(u1, v1).endVertex();
        bufferBuilder.vertex(matrix, rect.drawX + rect.drawWidth, rect.drawY, 0.0F).uv(u1, v0).endVertex();
        bufferBuilder.vertex(matrix, rect.drawX, rect.drawY, 0.0F).uv(u0, v0).endVertex();
        BufferUploader.drawWithShader(bufferBuilder.end());
        RenderSystem.disableBlend();
    }

    @Nullable
    public CacheEntry request(@NotNull ItemStack gunStack) {
        if (gunStack.isEmpty()) {
            return null;
        }

        Minecraft minecraft = Minecraft.getInstance();
        ItemStack normalizedStack = normalizeGunStack(gunStack.copy());
        GunRenderCacheKey key = GunRenderCacheKey.from(normalizedStack);
        CacheEntry entry = cache.get(key);
        if (entry == null) {
            entry = new CacheEntry(normalizedStack);
            cache.put(key, entry);
        }
        entry.lastAccessTick = minecraft.level != null ? minecraft.level.getGameTime() : 0L;
        if (entry.status == CacheStatus.PENDING && queuedKeys.add(key)) {
            pendingQueue.offer(key);
        }
        return entry;
    }

    @NotNull
    private ItemStack normalizeGunStack(@NotNull ItemStack stack) {
        return stack;
    }

    public void clear() {
        Minecraft minecraft = Minecraft.getInstance();
        for (CacheEntry entry : cache.values()) {
            releaseEntryTexture(minecraft, entry);
        }
        cache.clear();
        pendingQueue.clear();
        queuedKeys.clear();
    }

    private void bakeEntry(@NotNull Minecraft minecraft, @NotNull GunRenderCacheKey key, @NotNull CacheEntry entry) throws Exception {
        RenderTarget mainTarget = minecraft.getMainRenderTarget();
        TextureTarget cacheTarget = new TextureTarget(CACHE_TEXTURE_WIDTH, CACHE_TEXTURE_HEIGHT, true, Minecraft.ON_OSX);
        try {
            cacheTarget.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
            cacheTarget.clear(Minecraft.ON_OSX);
            cacheTarget.bindWrite(true);

            RenderSystem.viewport(0, 0, CACHE_TEXTURE_WIDTH, CACHE_TEXTURE_HEIGHT);
            RenderSystem.enableBlend();
            RenderSystem.enableDepthTest();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

            PoseStack poseStack = new PoseStack();

            MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
            RenderResult result = renderGunDisplayFirstPersonStyle(entry.gunStack, poseStack, bufferSource);
            bufferSource.endBatch();
            Lighting.setupFor3DItems();
            if (result == RenderResult.NOT_READY) {
                throw new PreviewNotReadyException();
            }
            if (result != RenderResult.READY) {
                throw new IllegalStateException("Gun display render failed for " + entry.gunStack.getItem());
            }

            NativeImage image = new NativeImage(CACHE_TEXTURE_WIDTH, CACHE_TEXTURE_HEIGHT, false);
            cacheTarget.bindRead();
            RenderSystem.bindTexture(cacheTarget.getColorTextureId());
            image.downloadTexture(0, true);
            image.flipY();

            DynamicTexture dynamicTexture = new DynamicTexture(image);
            net.minecraft.resources.ResourceLocation location = minecraft.getTextureManager().register(
                    Universalarmory.MODID + "/gun_cache/" + Integer.toHexString(key.hashCode()),
                    dynamicTexture
            );

            releaseEntryTexture(minecraft, entry);
            updateContentBounds(entry, image);
            entry.texture = dynamicTexture;
            entry.textureLocation = location;
        } finally {
            mainTarget.bindWrite(true);
            RenderSystem.viewport(0, 0, mainTarget.width, mainTarget.height);
            cacheTarget.destroyBuffers();
        }
    }

    private void trimCacheIfNeeded(@NotNull Minecraft minecraft) {
        if (cache.size() <= MAX_CACHE_SIZE) {
            return;
        }

        List<Map.Entry<GunRenderCacheKey, CacheEntry>> entries = new ArrayList<>(cache.entrySet());
        entries.sort(Comparator.comparingLong(entry -> entry.getValue().lastAccessTick));

        int removeCount = cache.size() - MAX_CACHE_SIZE;
        for (int i = 0; i < removeCount; i++) {
            Map.Entry<GunRenderCacheKey, CacheEntry> toRemove = entries.get(i);
            releaseEntryTexture(minecraft, toRemove.getValue());
            cache.remove(toRemove.getKey());
            queuedKeys.remove(toRemove.getKey());
            pendingQueue.remove(toRemove.getKey());
        }
    }

    private void releaseEntryTexture(@NotNull Minecraft minecraft, @NotNull CacheEntry entry) {
        if (entry.textureLocation != null) {
            minecraft.getTextureManager().release(entry.textureLocation);
            entry.textureLocation = null;
        }
        if (entry.texture != null) {
            entry.texture.close();
            entry.texture = null;
        }
    }

    private void updateContentBounds(@NotNull CacheEntry entry, @NotNull NativeImage image) {
        int minX = CACHE_TEXTURE_WIDTH;
        int minY = CACHE_TEXTURE_HEIGHT;
        int maxX = -1;
        int maxY = -1;

        for (int y = 0; y < CACHE_TEXTURE_HEIGHT; y++) {
            for (int x = 0; x < CACHE_TEXTURE_WIDTH; x++) {
                int pixel = image.getPixelRGBA(x, y);
                int alpha = (pixel >>> 24) & 0xFF;
                int blue = pixel & 0xFF;
                int green = (pixel >>> 8) & 0xFF;
                int red = (pixel >>> 16) & 0xFF;
                int brightness = red + green + blue;

                // Convert the cleared black background to transparent, then use the
                // remaining non-black pixels as the preview bounds.
                if (alpha <= 20 || brightness <= 42) {
                    image.setPixelRGBA(x, y, 0);
                    continue;
                }
                if (x < minX) minX = x;
                if (y < minY) minY = y;
                if (x > maxX) maxX = x;
                if (y > maxY) maxY = y;
            }
        }

        if (maxX < minX || maxY < minY) {
            entry.contentMinX = 0;
            entry.contentMinY = 0;
            entry.contentWidth = CACHE_TEXTURE_WIDTH;
            entry.contentHeight = CACHE_TEXTURE_HEIGHT;
            return;
        }

        entry.contentMinX = Math.max(0, minX - 2);
        entry.contentMinY = Math.max(0, minY - 2);
        entry.contentWidth = Math.min(CACHE_TEXTURE_WIDTH - entry.contentMinX, (maxX - minX) + 5);
        entry.contentHeight = Math.min(CACHE_TEXTURE_HEIGHT - entry.contentMinY, (maxY - minY) + 5);
    }

    @NotNull
    private BlitRect computeBlitRect(@NotNull CacheEntry entry, int x, int y, int width, int height) {
        int sourceX = entry.contentMinX;
        int sourceY = entry.contentMinY;
        int sourceWidth = Math.max(1, entry.contentWidth);
        int sourceHeight = Math.max(1, entry.contentHeight);

        float widthScale = (float) (width - 4) / (float) sourceWidth;
        float heightScale = (float) (height - 4) / (float) sourceHeight;
        float scale = Math.max(0.1F, Math.min(widthScale, heightScale));

        int drawWidth = Math.max(1, Math.round(sourceWidth * scale));
        int drawHeight = Math.max(1, Math.round(sourceHeight * scale));
        int drawX = x + (width - drawWidth) / 2;
        int drawY = y + (height - drawHeight) / 2;

        return new BlitRect(sourceX, sourceY, sourceWidth, sourceHeight, drawX, drawY, drawWidth, drawHeight);
    }

    public enum CacheStatus {
        PENDING,
        RENDERING,
        READY,
        FAILED
    }

    public static final class CacheEntry {
        private final ItemStack gunStack;
        private CacheStatus status = CacheStatus.PENDING;
        private long lastAccessTick;
        @Nullable
        private DynamicTexture texture;
        @Nullable
        private ResourceLocation textureLocation;
        private int contentMinX = 0;
        private int contentMinY = 0;
        private int contentWidth = CACHE_TEXTURE_WIDTH;
        private int contentHeight = CACHE_TEXTURE_HEIGHT;

        private CacheEntry(@NotNull ItemStack gunStack) {
            this.gunStack = gunStack;
        }

        @NotNull
        public CacheStatus getStatus() {
            return status;
        }
    }

    private static final class GunRenderCacheKey {
        private final String itemId;
        private final String tagData;

        private GunRenderCacheKey(@NotNull String itemId, @NotNull String tagData) {
            this.itemId = itemId;
            this.tagData = tagData;
        }

        @NotNull
        public static GunRenderCacheKey from(@NotNull ItemStack stack) {
            String itemId = Objects.toString(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()), "");
            String tagData = Objects.toString(stack.getTag(), "");
            return new GunRenderCacheKey(itemId, tagData);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof GunRenderCacheKey other)) {
                return false;
            }
            return itemId.equals(other.itemId) && tagData.equals(other.tagData);
        }

        @Override
        public int hashCode() {
            return Objects.hash(itemId, tagData);
        }

        @Override
        public String toString() {
            return itemId + "|" + tagData;
        }
    }

    private RenderResult renderGunDisplay(@NotNull ItemStack stack, @NotNull PoseStack poseStack, @NotNull MultiBufferSource.BufferSource bufferSource) {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            Lighting.setupForFlatItems();
            minecraft.getItemRenderer().renderStatic(
                    stack,
                    ItemDisplayContext.FIXED,
                    LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY,
                    poseStack,
                    bufferSource,
                    minecraft.level,
                    0
            );
            return RenderResult.READY;
        } catch (Exception exception) {
            return RenderResult.INVALID;
        }
    }

    private RenderResult renderGunDisplayFirstPersonStyle(@NotNull ItemStack stack,
                                                          @NotNull PoseStack poseStack,
                                                          @NotNull MultiBufferSource.BufferSource bufferSource) {
        try {
            firstPersonPreviewRenderer.renderPreview(
                    poseStack,
                    bufferSource,
                    stack,
                    CACHE_TEXTURE_WIDTH / 8,
                    CACHE_TEXTURE_HEIGHT / 5,
                    CACHE_TEXTURE_WIDTH * 3 / 4,
                    CACHE_TEXTURE_HEIGHT * 3 / 5
            );
            return RenderResult.READY;
        } catch (Exception exception) {
            return RenderResult.INVALID;
        }
    }

    private enum RenderResult {
        READY,
        NOT_READY,
        INVALID
    }

    private static final class PreviewNotReadyException extends Exception {
    }

    private record BlitRect(
            int sourceX,
            int sourceY,
            int sourceWidth,
            int sourceHeight,
            int drawX,
            int drawY,
            int drawWidth,
            int drawHeight
    ) {
    }

}
