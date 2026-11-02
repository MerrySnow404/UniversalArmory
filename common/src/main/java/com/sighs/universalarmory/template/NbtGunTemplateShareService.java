package com.sighs.universalarmory.template;

import com.sighs.universalarmory.gui.util.AttachmentUtils;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.init.ModItems;
import com.tacz.guns.resource.index.CommonGunIndex;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class NbtGunTemplateShareService implements GunTemplateShareService {
    private static final String CODE_PREFIX = "UAT2:";
    private static final int ATTACHMENT_MASK_BITS = 6;
    private static final int FLAG_HAS_DISPLAY_OVERRIDE = 1 << ATTACHMENT_MASK_BITS;
    private static final int FLAG_ATTACHMENT_LOCKED = 1 << (ATTACHMENT_MASK_BITS + 1);
    private static final int RESOURCE_MODE_TACZ_PATH_ONLY = 0;
    private static final int RESOURCE_MODE_MINECRAFT_PATH_ONLY = 1;
    private static final int RESOURCE_MODE_FULL = 2;
    private static final String DEFAULT_TACZ_NAMESPACE = "tacz";
    private static final String DEFAULT_MINECRAFT_NAMESPACE = "minecraft";
    private static final AttachmentType[] ENCODED_ATTACHMENT_ORDER = {
            AttachmentType.SCOPE,
            AttachmentType.MUZZLE,
            AttachmentType.STOCK,
            AttachmentType.GRIP,
            AttachmentType.LASER,
            AttachmentType.EXTENDED_MAG
    };

    @Override
    public boolean supports(@NotNull ItemStack stack) {
        return AttachmentUtils.isGun(stack);
    }

    @Override
    @NotNull
    public GunTemplate exportTemplate(@NotNull ItemStack gunStack, @NotNull String templateName) {
        if (!supports(gunStack)) {
            throw new IllegalArgumentException("ItemStack is not a gun");
        }

        IGun gun = Objects.requireNonNull(AttachmentUtils.getGunOrNull(gunStack), "gun");
        ResourceLocation gunId = gun.getGunId(gunStack);
        if (gunId == null) {
            throw new IllegalArgumentException("Gun stack is missing gunId");
        }

        EnumMap<AttachmentType, ResourceLocation> attachments = new EnumMap<>(AttachmentType.class);
        for (AttachmentType type : AttachmentType.values()) {
            if (type == AttachmentType.NONE) {
                continue;
            }
            ResourceLocation attachmentId = AttachmentUtils.getAttachmentId(gunStack, type);
            if (attachmentId != null) {
                attachments.put(type, attachmentId);
            }
        }

        return new GunTemplate(
                templateName,
                gunId,
                gun.getGunDisplayId(gunStack),
                gun.hasAttachmentLock(gunStack),
                attachments
        );
    }

    @Override
    @NotNull
    public String exportCode(@NotNull ItemStack gunStack, @NotNull String templateName) {
        return exportCode(exportTemplate(gunStack, templateName));
    }

    @Override
    @NotNull
    public String exportCode(@NotNull GunTemplate template) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             DataOutputStream data = new DataOutputStream(output)) {
            writeString(data, template.getTemplateName());
            writeResourceLocation(data, template.getGunId());

            ResourceLocation defaultDisplayId = resolveDefaultDisplayId(template.getGunId());
            boolean hasDisplayOverride = template.getDisplayId() != null && !template.getDisplayId().equals(defaultDisplayId);

            int header = 0;
            if (template.isAttachmentLocked()) {
                header |= FLAG_ATTACHMENT_LOCKED;
            }
            if (hasDisplayOverride) {
                header |= FLAG_HAS_DISPLAY_OVERRIDE;
            }

            ResourceLocation[] encodedAttachments = new ResourceLocation[ENCODED_ATTACHMENT_ORDER.length];
            for (int index = 0; index < ENCODED_ATTACHMENT_ORDER.length; index++) {
                ResourceLocation attachmentId = template.getAttachments().get(ENCODED_ATTACHMENT_ORDER[index]);
                encodedAttachments[index] = attachmentId;
                if (attachmentId != null) {
                    header |= (1 << index);
                }
            }
            data.writeByte(header);

            if (hasDisplayOverride) {
                writeResourceLocation(data, Objects.requireNonNull(template.getDisplayId(), "displayId"));
            }

            for (ResourceLocation attachmentId : encodedAttachments) {
                if (attachmentId != null) {
                    writeResourceLocation(data, attachmentId);
                }
            }
            data.flush();
            return CODE_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(output.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to encode gun template", e);
        }
    }

    @Override
    @NotNull
    public GunTemplate importCode(@NotNull String code) {
        if (!code.startsWith(CODE_PREFIX)) {
            throw new IllegalArgumentException("Unsupported gun template code prefix");
        }
        return importCompactCode(code);
    }

    @Override
    @NotNull
    public ItemStack createPreviewStack(@NotNull GunTemplate template) {
        Item item = resolveGunItem(template.getGunId());
        if (item == null) {
            throw new IllegalArgumentException("Unknown gun item for gunId: " + template.getGunId());
        }

        ItemStack stack = new ItemStack(item);
        IGun gun = AttachmentUtils.getGunOrNull(stack);
        if (gun == null) {
            throw new IllegalArgumentException("Configured item is not a gun: " + ForgeRegistries.ITEMS.getKey(item));
        }

        gun.setGunId(stack, template.getGunId());
        ResourceLocation displayId = template.getDisplayId() != null ? template.getDisplayId() : resolveDefaultDisplayId(template.getGunId());
        if (displayId != null) {
            gun.setGunDisplayId(stack, displayId);
        }

        for (Map.Entry<AttachmentType, ResourceLocation> entry : template.getAttachments().entrySet()) {
            ItemStack attachmentStack = createAttachmentStack(entry.getValue());
            if (!AttachmentUtils.installAttachmentLikeRefit(stack, attachmentStack, null)) {
                throw new IllegalArgumentException("Failed to install attachment " + entry.getValue() + " for slot " + entry.getKey());
            }
        }

        AttachmentUtils.setAttachmentLock(stack, template.isAttachmentLocked());
        return stack;
    }

    @NotNull
    private GunTemplate importCompactCode(@NotNull String code) {
        try {
            String payload = code.substring(CODE_PREFIX.length()).trim();
            byte[] bytes = Base64.getUrlDecoder().decode(payload);
            try (ByteArrayInputStream input = new ByteArrayInputStream(bytes);
                 DataInputStream data = new DataInputStream(input)) {
                String templateName = readString(data);
                ResourceLocation gunId = readResourceLocation(data);
                int header = data.readUnsignedByte();
                ResourceLocation displayId = (header & FLAG_HAS_DISPLAY_OVERRIDE) != 0 ? readResourceLocation(data) : null;
                int attachmentMask = header & ((1 << ATTACHMENT_MASK_BITS) - 1);

                EnumMap<AttachmentType, ResourceLocation> attachments = new EnumMap<>(AttachmentType.class);
                for (int index = 0; index < ENCODED_ATTACHMENT_ORDER.length; index++) {
                    if ((attachmentMask & (1 << index)) != 0) {
                        attachments.put(ENCODED_ATTACHMENT_ORDER[index], readResourceLocation(data));
                    }
                }

                Item item = resolveGunItem(gunId);
                if (item == null) {
                    throw new IllegalArgumentException("Unknown gun item for gunId: " + gunId);
                }

                return new GunTemplate(
                        templateName,
                        gunId,
                        displayId,
                        (header & FLAG_ATTACHMENT_LOCKED) != 0,
                        attachments
                );
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to decode compact gun template code", e);
        }
    }

    @NotNull
    private static ItemStack createAttachmentStack(@NotNull ResourceLocation attachmentId) {
        Item item = ForgeRegistries.ITEMS.getValue(attachmentId);
        if (item == null) {
            throw new IllegalArgumentException("Unknown attachment item: " + attachmentId);
        }
        ItemStack stack = new ItemStack(item);
        if (!AttachmentUtils.isAttachment(stack)) {
            throw new IllegalArgumentException("Configured item is not an attachment: " + attachmentId);
        }
        return stack;
    }

    @Nullable
    private static Item resolveGunItem(@NotNull ResourceLocation gunId) {
        Optional<CommonGunIndex> commonGunIndex = TimelessAPI.getCommonGunIndex(gunId);
        if (commonGunIndex.isEmpty()) {
            return null;
        }

        String itemType = commonGunIndex.get().getPojo().getItemType();
        if (Objects.equals(itemType, "modern_kinetic")) {
            return ModItems.MODERN_KINETIC_GUN.get();
        }

        ResourceLocation itemId = new ResourceLocation(DEFAULT_TACZ_NAMESPACE, itemType + "_gun");
        return ForgeRegistries.ITEMS.getValue(itemId);
    }

    @Nullable
    private static ResourceLocation resolveDefaultDisplayId(@NotNull ResourceLocation gunId) {
        return TimelessAPI.getCommonGunIndex(gunId)
                .map(CommonGunIndex::getPojo)
                .map(com.tacz.guns.resource.pojo.GunIndexPOJO::getDisplay)
                .orElse(null);
    }

    private static void writeResourceLocation(@NotNull DataOutputStream data, @NotNull ResourceLocation id) throws IOException {
        String namespace = id.getNamespace();
        if (DEFAULT_TACZ_NAMESPACE.equals(namespace)) {
            data.writeByte(RESOURCE_MODE_TACZ_PATH_ONLY);
            writeString(data, id.getPath());
        } else if (DEFAULT_MINECRAFT_NAMESPACE.equals(namespace)) {
            data.writeByte(RESOURCE_MODE_MINECRAFT_PATH_ONLY);
            writeString(data, id.getPath());
        } else {
            data.writeByte(RESOURCE_MODE_FULL);
            writeString(data, namespace);
            writeString(data, id.getPath());
        }
    }

    @NotNull
    private static ResourceLocation readResourceLocation(@NotNull DataInputStream data) throws IOException {
        int namespaceMode = data.readUnsignedByte();
        String namespace;
        String path;
        if (namespaceMode == RESOURCE_MODE_TACZ_PATH_ONLY) {
            namespace = DEFAULT_TACZ_NAMESPACE;
            path = readString(data);
        } else if (namespaceMode == RESOURCE_MODE_MINECRAFT_PATH_ONLY) {
            namespace = DEFAULT_MINECRAFT_NAMESPACE;
            path = readString(data);
        } else if (namespaceMode == RESOURCE_MODE_FULL) {
            namespace = readString(data);
            path = readString(data);
        } else {
            throw new IOException("Unsupported namespace mode: " + namespaceMode);
        }
        return new ResourceLocation(namespace, path);
    }

    private static void writeString(@NotNull DataOutputStream data, @NotNull String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(data, bytes.length);
        data.write(bytes);
    }

    @NotNull
    private static String readString(@NotNull DataInputStream data) throws IOException {
        int length = readVarInt(data);
        if (length < 0) {
            throw new IOException("Negative string length: " + length);
        }
        byte[] bytes = new byte[length];
        data.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void writeVarInt(@NotNull DataOutputStream data, int value) throws IOException {
        int remaining = value;
        while ((remaining & ~0x7F) != 0) {
            data.writeByte((remaining & 0x7F) | 0x80);
            remaining >>>= 7;
        }
        data.writeByte(remaining);
    }

    private static int readVarInt(@NotNull DataInputStream data) throws IOException {
        int value = 0;
        int position = 0;
        while (position < 32) {
            int currentByte = data.readUnsignedByte();
            value |= (currentByte & 0x7F) << position;
            if ((currentByte & 0x80) == 0) {
                return value;
            }
            position += 7;
        }
        throw new IOException("VarInt is too large");
    }
}
