package com.sighs.universalarmory;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

@Mod.EventBusSubscriber(modid = Universalarmory.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> FAVORITE_STACKS = BUILDER
            .comment("Global favorited ItemStacks serialized as SNBT. Can be copied to other instances for sharing.")
            .defineListAllowEmpty("favoriteStacks", List.of(), Config::validateFavoriteEntry);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private static volatile List<String> favoriteStacks = List.of();
    private static volatile ModConfig activeConfig;

    private Config() {
    }

    public static List<String> getFavoriteStacks() {
        return favoriteStacks;
    }

    public static synchronized void setFavoriteStacks(Collection<? extends String> entries) {
        List<String> normalizedEntries = normalizeEntries(entries);
        FAVORITE_STACKS.set(normalizedEntries);
        favoriteStacks = normalizedEntries;
        save();
    }

    private static boolean validateFavoriteEntry(Object value) {
        return value instanceof String entry && !entry.isBlank();
    }

    private static List<String> normalizeEntries(Collection<? extends String> entries) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String entry : entries) {
            if (entry == null) {
                continue;
            }
            String trimmed = entry.trim();
            if (!trimmed.isEmpty()) {
                normalized.add(trimmed);
            }
        }
        return List.copyOf(normalized);
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) {
            return;
        }
        activeConfig = event.getConfig();
        favoriteStacks = normalizeEntries(FAVORITE_STACKS.get());
    }

    private static void save() {
        ModConfig config = activeConfig;
        if (config != null) {
            config.save();
        }
    }
}
