package com.sighs.universalarmory.template;

import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public final class GunTemplate {
    private final String templateName;
    private final ResourceLocation gunId;
    @Nullable
    private final ResourceLocation displayId;
    private final boolean attachmentLocked;
    private final Map<AttachmentType, ResourceLocation> attachments;

    public GunTemplate(@NotNull String templateName,
                       @NotNull ResourceLocation gunId,
                       @Nullable ResourceLocation displayId,
                       boolean attachmentLocked,
                       @NotNull Map<AttachmentType, ResourceLocation> attachments) {
        this.templateName = normalizeTemplateName(templateName);
        this.gunId = Objects.requireNonNull(gunId, "gunId");
        this.displayId = displayId;
        this.attachmentLocked = attachmentLocked;

        EnumMap<AttachmentType, ResourceLocation> normalizedAttachments = new EnumMap<>(AttachmentType.class);
        for (Map.Entry<AttachmentType, ResourceLocation> entry : attachments.entrySet()) {
            AttachmentType type = Objects.requireNonNull(entry.getKey(), "attachmentType");
            ResourceLocation attachmentId = Objects.requireNonNull(entry.getValue(), "attachmentId");
            if (type == AttachmentType.NONE) {
                continue;
            }
            normalizedAttachments.put(type, attachmentId);
        }
        this.attachments = Collections.unmodifiableMap(normalizedAttachments);
    }

    @NotNull
    public String getTemplateName() {
        return templateName;
    }

    @NotNull
    public ResourceLocation getGunId() {
        return gunId;
    }

    @Nullable
    public ResourceLocation getDisplayId() {
        return displayId;
    }

    public boolean isAttachmentLocked() {
        return attachmentLocked;
    }

    @NotNull
    public Map<AttachmentType, ResourceLocation> getAttachments() {
        return attachments;
    }

    private static String normalizeTemplateName(@NotNull String templateName) {
        String normalized = templateName.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Template name must not be blank");
        }
        return normalized;
    }
}
