package com.sighs.universalarmory.apricity.element;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.element.MinecraftElement;
import com.sighs.apricityui.registry.annotation.ElementRegister;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import com.sighs.universalarmory.client.render.FirstPersonStyleGunPreviewRenderer;
import com.sighs.universalarmory.client.render.GunRenderCacheQueue;
import com.sighs.universalarmory.gui.util.AttachmentUtils;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.AttachmentItemBuilder;
import com.tacz.guns.item.AttachmentItem;
import com.tacz.guns.resource.index.CommonGunIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ElementRegister("gunshow")
public class GunShowElement extends MinecraftElement {

    private static final Logger LOGGER = LoggerFactory.getLogger(GunShowElement.class);
    private static final String TAG_NAME = "GUNSHOW";
    private static final String CACHE_SIGNATURE_KEY = "ua.gunshow.signature";
    private static final String CACHE_ENTRY_KEY = "ua.gunshow.entry";
    private static final String CACHE_STACK_KEY = "ua.gunshow.stack";
    private static final String LOG_STATE_KEY = "ua.gunshow.log_state";
    private static final String SIGNATURE_VERSION = "attachments-v2";
    private static final String DEFAULT_ITEM_ID = "tacz:modern_kinetic_gun";
    private final FirstPersonStyleGunPreviewRenderer directRenderer = FirstPersonStyleGunPreviewRenderer.getInstance();

    public GunShowElement(Document document) {
        super(document, TAG_NAME);
    }

    @Override
    public void tick() {
        super.tick();

        GunRenderCacheQueue renderCacheQueue = GunRenderCacheQueue.getInstance();
        renderCacheQueue.tick();

        String signature = buildSignature();
        String cachedSignature = (String) getRuntimeCache(CACHE_SIGNATURE_KEY);
        if (signature.equals(cachedSignature)) {
            if (shouldSkipCache()) {
                if (getRuntimeCache(CACHE_STACK_KEY) == null) {
                    putRuntimeCache(CACHE_STACK_KEY, resolveGunStack());
                }
                requestRepaint();
                return;
            }
            GunRenderCacheQueue.CacheEntry entry = (GunRenderCacheQueue.CacheEntry) getRuntimeCache(CACHE_ENTRY_KEY);
            if (entry != null && entry.getStatus() != GunRenderCacheQueue.CacheStatus.READY) {
                requestRepaint();
            }
            return;
        }

        putRuntimeCache(CACHE_SIGNATURE_KEY, signature);
        ItemStack stack = resolveGunStack();
        putRuntimeCache(CACHE_STACK_KEY, stack);
        if (shouldSkipCache()) {
            putRuntimeCache(CACHE_ENTRY_KEY, null);
            requestRepaint();
            return;
        }
        GunRenderCacheQueue.CacheEntry entry = stack.isEmpty() ? null : renderCacheQueue.request(stack);
        putRuntimeCache(CACHE_ENTRY_KEY, entry);
        requestRepaint();
    }

    @Override
    public void drawPhase(PoseStack poseStack, Base.RenderPhase renderPhase) {
        super.drawPhase(poseStack, renderPhase);
        if (renderPhase != Base.RenderPhase.BODY) {
            return;
        }

        Rect rect = Rect.of(this);
        Position position = rect.getContentPosition();
        Size size = rect.getBodyRectSize();
        RenderBounds bounds = resolveRenderBounds(position, size);

        if (shouldSkipCache()) {
            ItemStack stack = getCachedGunStack();
            logRenderState(null, bounds);
            if (!stack.isEmpty()) {
                Base.commitDraws();
                MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
                directRenderer.renderPreview(poseStack, bufferSource, stack, bounds.x, bounds.y, bounds.width, bounds.height);
                Base.beginRendering();
            }
            return;
        }

        GunRenderCacheQueue renderCacheQueue = GunRenderCacheQueue.getInstance();
        GunRenderCacheQueue.CacheEntry entry = (GunRenderCacheQueue.CacheEntry) getRuntimeCache(CACHE_ENTRY_KEY);
        if (entry == null) {
            ItemStack stack = getCachedGunStack();
            entry = stack.isEmpty() ? null : renderCacheQueue.request(stack);
            putRuntimeCache(CACHE_ENTRY_KEY, entry);
        }
        logRenderState(entry, bounds);

        Base.commitDraws();
        renderCacheQueue.renderPreview(poseStack, entry, bounds.x, bounds.y, bounds.width, bounds.height);
        Base.beginRendering();
    }

    @Override
    public ItemStack getTooltipStack() {
        ItemStack stack = resolveGunStack();
        return stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
    }

    @NotNull
    private String buildSignature() {
        String itemId = normalize(getAttribute("item"));
        String gunId = normalize(getAttribute("gun-id"));
        String displayId = normalize(getAttribute("display-id"));
        String skipCache = normalize(getAttribute("skip-cache"));
        String randomFullAttachments = normalize(getAttribute("random-full-attachments"));
        String text = resolveNbtText();
        return SIGNATURE_VERSION + "|" + itemId + "|" + gunId + "|" + displayId + "|" + skipCache + "|" + randomFullAttachments + "|" + text;
    }

    @NotNull
    private ItemStack resolveGunStack() {
        String itemId = normalize(getAttribute("item"));
        Item item = resolveItem(itemId.isBlank() ? DEFAULT_ITEM_ID : itemId);
        if (item == null) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = new ItemStack(item);
        CompoundTag tag = parseTag(resolveNbtText());
        if (tag != null) {
            stack.setTag(tag);
        }

        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null) {
            return ItemStack.EMPTY;
        }

        CompoundTag stackTag = stack.getOrCreateTag();
        ResourceLocation gunId = readResourceLocationAttribute("gun-id");
        if (gunId == null) {
            gunId = readResourceLocation(stackTag, "GunId");
        }
        if (gunId != null) {
            gun.setGunId(stack, gunId);
        }

        ResourceLocation displayId = readResourceLocationAttribute("display-id");
        if (displayId == null) {
            displayId = readResourceLocation(stackTag, "GunDisplayId");
        }
        if (displayId == null && gunId != null) {
            displayId = resolveDefaultDisplayId(gunId);
        }
        if (displayId != null) {
            gun.setGunDisplayId(stack, displayId);
        } else if (gunId != null) {
            gun.setGunDisplayId(stack, gunId);
        }

        if (shouldRandomFullAttachments()) {
            installRandomFullAttachments(stack);
        }

        return stack;
    }

    @NotNull
    private ItemStack getCachedGunStack() {
        String signature = buildSignature();
        String cachedSignature = (String) getRuntimeCache(CACHE_SIGNATURE_KEY);
        ItemStack stack = (ItemStack) getRuntimeCache(CACHE_STACK_KEY);
        if (signature.equals(cachedSignature) && stack != null) {
            return stack;
        }

        stack = resolveGunStack();
        putRuntimeCache(CACHE_SIGNATURE_KEY, signature);
        putRuntimeCache(CACHE_STACK_KEY, stack);
        return stack;
    }

    @NotNull
    private RenderBounds resolveRenderBounds(@NotNull Position position, @NotNull Size size) {
        int x = (int) Math.round(position.x);
        int y = (int) Math.round(position.y);
        int width = (int) Math.round(size.width());
        int height = (int) Math.round(size.height());
        if (width > 1 && height > 1) {
            return new RenderBounds(x, y, width, height, false);
        }

        if (parentElement == null) {
            return new RenderBounds(x, y, Math.max(1, width), Math.max(1, height), false);
        }

        Rect parentRect = Rect.of(parentElement);
        Position parentPosition = parentRect.getContentPosition();
        Size parentSize = parentRect.getBodyRectSize();
        int fallbackWidth = Math.max(1, (int) Math.round(parentSize.width() * 0.86D));
        int fallbackHeight = Math.max(1, (int) Math.round(parentSize.height() * 0.62D));
        int fallbackX = (int) Math.round(parentPosition.x + (parentSize.width() - fallbackWidth) * 0.5D);
        int fallbackY = (int) Math.round(parentPosition.y + (parentSize.height() - fallbackHeight) * 0.5D);
        return new RenderBounds(fallbackX, fallbackY, fallbackWidth, fallbackHeight, true);
    }

    private void logRenderState(@Nullable GunRenderCacheQueue.CacheEntry entry, @NotNull RenderBounds bounds) {
        String status = entry == null ? "null" : entry.getStatus() + "/texture=" + entry.hasTexture();
        ItemStack stack = getCachedGunStack();
        IGun gun = IGun.getIGunOrNull(stack);
        ResourceLocation gunId = gun != null ? gun.getGunId(stack) : null;
        ResourceLocation displayId = gun != null ? gun.getGunDisplayId(stack) : null;
        boolean hasDisplay = !stack.isEmpty() && TimelessAPI.getGunDisplay(stack).isPresent();
        String state = bounds + "|stack=" + ForgeRegistries.ITEMS.getKey(stack.getItem()) + "|gunId=" + gunId
                + "|displayId=" + displayId + "|hasDisplay=" + hasDisplay + "|cache=" + status;
        if (!state.equals(getRuntimeCache(LOG_STATE_KEY))) {
            putRuntimeCache(LOG_STATE_KEY, state);
            LOGGER.info("GunShow render state {}", state);
        }
    }

    @Nullable
    private CompoundTag parseTag(@NotNull String text) {
        if (text.isBlank()) {
            return null;
        }

        try {
            return TagParser.parseTag(text);
        } catch (Exception ignored) {
            return null;
        }
    }

    @NotNull
    private String resolveNbtText() {
        String nbt = normalize(getAttribute("nbt"));
        return nbt.isBlank() ? normalize(innerText) : nbt;
    }

    @Nullable
    private Item resolveItem(@NotNull String itemId) {
        ResourceLocation resourceLocation = ResourceLocation.tryParse(itemId);
        if (resourceLocation == null) {
            return null;
        }
        Item item = ForgeRegistries.ITEMS.getValue(resourceLocation);
        if (item == null || item == net.minecraft.world.item.Items.AIR) {
            return null;
        }
        return item;
    }

    @Nullable
    private ResourceLocation readResourceLocation(@NotNull CompoundTag tag, @NotNull String key) {
        if (!tag.contains(key)) {
            return null;
        }
        return ResourceLocation.tryParse(tag.getString(key));
    }

    @Nullable
    private ResourceLocation readResourceLocationAttribute(@NotNull String key) {
        String value = normalize(getAttribute(key));
        return value.isBlank() ? null : ResourceLocation.tryParse(value);
    }

    @Nullable
    private ResourceLocation resolveDefaultDisplayId(@NotNull ResourceLocation gunId) {
        return TimelessAPI.getCommonGunIndex(gunId)
                .map(CommonGunIndex::getPojo)
                .map(com.tacz.guns.resource.pojo.GunIndexPOJO::getDisplay)
                .orElse(null);
    }

    private boolean shouldSkipCache() {
        String value = normalize(getAttribute("skip-cache"));
        return "true".equalsIgnoreCase(value) || "1".equals(value) || "yes".equalsIgnoreCase(value);
    }

    private boolean shouldRandomFullAttachments() {
        String value = normalize(getAttribute("random-full-attachments"));
        return "true".equalsIgnoreCase(value) || "1".equals(value) || "yes".equalsIgnoreCase(value);
    }

    private void installRandomFullAttachments(@NotNull ItemStack gunStack) {
        IGun gun = IGun.getIGunOrNull(gunStack);
        ResourceLocation gunId = gun != null ? gun.getGunId(gunStack) : null;
        if (gun == null || gunId == null) {
            return;
        }

        int installedCount = 0;
        for (AttachmentType type : AttachmentType.values()) {
            if (type == null || type == AttachmentType.NONE) {
                continue;
            }
            if (hasPreviewAttachmentTag(gunStack, type)) {
                continue;
            }

            List<ItemStack> candidates = collectAttachmentCandidates(type);
            Collections.shuffle(candidates);
            LOGGER.info("GunShow random full attachments candidates gunId={} type={} count={}", gunId, type, candidates.size());
            for (ItemStack candidate : candidates) {
                if (candidate.isEmpty()) {
                    continue;
                }
                IAttachment attachment = IAttachment.getIAttachmentOrNull(candidate);
                if (attachment == null || attachment.getType(candidate) != type) {
                    continue;
                }

                ItemStack toInstall = candidate.copy();
                toInstall.setCount(1);
                boolean installed = AttachmentUtils.installAttachmentLikeRefit(gunStack, toInstall, null);
                if (!installed) {
                    installPreviewAttachment(gunStack, type, toInstall);
                }
                ResourceLocation installedId = readPreviewAttachmentId(gunStack, type);
                LOGGER.info("GunShow random full attachments install gunId={} type={} attachment={} installedLikeRefit={} finalId={} tag={}",
                        gunId, type, attachment.getAttachmentId(candidate), installed, installedId, gunStack.getTag());
                if (installedId != null) {
                    installedCount++;
                    break;
                }
            }
        }
        LOGGER.info("GunShow random full attachments gunId={} installedCount={} attachments={} tag={}",
                gunId, installedCount, describePreviewAttachmentIds(gunStack), gunStack.getTag());
    }

    @NotNull
    private List<ItemStack> collectAttachmentCandidates(@NotNull AttachmentType type) {
        Map<String, ItemStack> uniqueCandidates = new LinkedHashMap<>();
        for (ItemStack stack : AttachmentItem.fillItemCategory(type)) {
            addAttachmentCandidate(uniqueCandidates, type, stack);
        }
        for (Map.Entry<ResourceLocation, com.tacz.guns.client.resource.index.ClientAttachmentIndex> entry : TimelessAPI.getAllClientAttachmentIndex()) {
            ItemStack stack = AttachmentItemBuilder.create()
                    .setId(entry.getKey())
                    .setCount(1)
                    .build();
            addAttachmentCandidate(uniqueCandidates, type, stack);
        }
        return new ArrayList<>(uniqueCandidates.values());
    }

    private void addAttachmentCandidate(@NotNull Map<String, ItemStack> candidates,
                                        @NotNull AttachmentType expectedType,
                                        @NotNull ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        IAttachment attachment = IAttachment.getIAttachmentOrNull(stack);
        if (attachment == null || attachment.getType(stack) != expectedType) {
            return;
        }
        ResourceLocation attachmentId = attachment.getAttachmentId(stack);
        if (attachmentId == null) {
            return;
        }
        ItemStack copy = stack.copy();
        copy.setCount(1);
        candidates.putIfAbsent(attachmentId.toString(), copy);
    }

    private void installPreviewAttachment(@NotNull ItemStack gunStack,
                                          @NotNull AttachmentType type,
                                          @NotNull ItemStack attachmentStack) {
        CompoundTag rootTag = gunStack.getOrCreateTag();
        CompoundTag attachmentTag = new CompoundTag();
        attachmentStack.save(attachmentTag);
        rootTag.put("Attachment" + type.name(), attachmentTag);
        LOGGER.info("GunShow preview attachment installed type={} attachmentId={} attachmentTag={}",
                type,
                IAttachment.getIAttachmentOrNull(attachmentStack) != null
                        ? IAttachment.getIAttachmentOrNull(attachmentStack).getAttachmentId(attachmentStack)
                        : null,
                attachmentTag);
    }

    private boolean hasPreviewAttachmentTag(@NotNull ItemStack gunStack, @NotNull AttachmentType type) {
        return readPreviewAttachmentId(gunStack, type) != null;
    }

    @Nullable
    private ResourceLocation readPreviewAttachmentId(@NotNull ItemStack gunStack, @NotNull AttachmentType type) {
        CompoundTag rootTag = gunStack.getTag();
        if (rootTag == null || !rootTag.contains("Attachment" + type.name(), CompoundTag.TAG_COMPOUND)) {
            return null;
        }
        ItemStack attachmentStack = ItemStack.of(rootTag.getCompound("Attachment" + type.name()));
        IAttachment attachment = IAttachment.getIAttachmentOrNull(attachmentStack);
        return attachment != null ? attachment.getAttachmentId(attachmentStack) : null;
    }

    @NotNull
    private List<ResourceLocation> describePreviewAttachmentIds(@NotNull ItemStack gunStack) {
        List<ResourceLocation> result = new ArrayList<>();
        for (AttachmentType type : AttachmentType.values()) {
            if (type == AttachmentType.NONE) {
                continue;
            }
            ResourceLocation id = readPreviewAttachmentId(gunStack, type);
            if (id != null) {
                result.add(id);
            }
        }
        return result;
    }

    @NotNull
    private String normalize(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    private record RenderBounds(int x, int y, int width, int height, boolean fromParent) {
    }
}
