package com.sighs.universalarmory.template;

public final class GunTemplateCodecs {
    private static final GunTemplateShareService SHARE_SERVICE = new NbtGunTemplateShareService();

    private GunTemplateCodecs() {
    }

    public static GunTemplateShareService share() {
        return SHARE_SERVICE;
    }
}
