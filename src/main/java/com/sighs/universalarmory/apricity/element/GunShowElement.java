package com.sighs.universalarmory.apricity.element;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.instance.element.MinecraftElement;
import com.sighs.apricityui.registry.annotation.ElementRegister;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.style.Position;
import com.sighs.apricityui.style.Size;
import com.sighs.universalarmory.client.render.GunRenderCacheQueue;
import com.tacz.guns.api.item.IGun;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ElementRegister("gunshow")
public class GunShowElement extends MinecraftElement {

    private static final String TAG_NAME = "GUNSHOW";
    private static final String CACHE_SIGNATURE_KEY = "ua.gunshow.signature";
    private static final String CACHE_ENTRY_KEY = "ua.gunshow.entry";
    private static final String DEFAULT_ITEM_ID = "tacz:modern_kinetic_gun";

    public GunShowElement(Document document) {
        super(document, TAG_NAME);
    }

    @Override
    public void tick() {
        super.tick();

        String signature = buildSignature();
        String cachedSignature = (String) getRuntimeCache(CACHE_SIGNATURE_KEY);
        if (signature.equals(cachedSignature)) {
            return;
        }

        putRuntimeCache(CACHE_SIGNATURE_KEY, signature);
        ItemStack stack = resolveGunStack();
        GunRenderCacheQueue.CacheEntry entry = stack.isEmpty() ? null : GunRenderCacheQueue.getInstance().request(stack);
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
        int x = (int) Math.round(position.x);
        int y = (int) Math.round(position.y);
        int width = Math.max(1, (int) Math.round(size.width()));
        int height = Math.max(1, (int) Math.round(size.height()));

        GunRenderCacheQueue.getInstance().renderPreview(
                poseStack,
                (GunRenderCacheQueue.CacheEntry) getRuntimeCache(CACHE_ENTRY_KEY),
                x,
                y,
                width,
                height
        );
    }

    @Override
    public ItemStack getTooltipStack() {
        ItemStack stack = resolveGunStack();
        return stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
    }

    @NotNull
    private String buildSignature() {
        String itemId = normalize(getAttribute("item"));
        String text = normalize(innerText);
        return itemId + "|" + text;
    }

    @NotNull
    private ItemStack resolveGunStack() {
        String itemId = normalize(getAttribute("item"));
        Item item = resolveItem(itemId.isBlank() ? DEFAULT_ITEM_ID : itemId);
        if (item == null) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = new ItemStack(item);
        CompoundTag tag = parseTag(normalize(innerText));
        if (tag != null) {
            stack.setTag(tag);
        }

        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null) {
            return ItemStack.EMPTY;
        }

        CompoundTag stackTag = stack.getOrCreateTag();
        ResourceLocation gunId = readResourceLocation(stackTag, "GunId");
        if (gunId != null) {
            gun.setGunId(stack, gunId);
        }

        ResourceLocation displayId = readResourceLocation(stackTag, "GunDisplayId");
        if (displayId != null) {
            gun.setGunDisplayId(stack, displayId);
        } else if (gunId != null) {
            gun.setGunDisplayId(stack, gunId);
        }

        return stack;
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

    @NotNull
    private String normalize(@Nullable String value) {
        return value == null ? "" : value.trim();
    }
}
