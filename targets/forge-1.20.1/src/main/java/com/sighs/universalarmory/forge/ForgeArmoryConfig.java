package com.sighs.universalarmory.forge;

import com.sighs.universalarmory.Config;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import java.util.List;

final class ForgeArmoryConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> FAVORITES = BUILDER
            .defineListAllowEmpty("favoriteStacks", List.of(), ForgeArmoryConfig::valid);
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> RECENT = BUILDER
            .defineListAllowEmpty("recentStacks", List.of(), ForgeArmoryConfig::valid);
    static final ForgeConfigSpec SPEC = BUILDER.build();
    private static ModConfig active;

    private ForgeArmoryConfig() {
    }

    static void onLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() == SPEC) {
            active = event.getConfig();
            Config.load(FAVORITES.get(), RECENT.get());
        }
    }

    static void saveFavorites(List<String> entries) {
        FAVORITES.set(entries);
        save();
    }

    static void saveRecent(List<String> entries) {
        RECENT.set(entries);
        save();
    }

    private static void save() {
        if (active != null) {
            active.save();
        }
    }

    private static boolean valid(Object value) {
        return value instanceof String text && !text.isBlank();
    }
}
