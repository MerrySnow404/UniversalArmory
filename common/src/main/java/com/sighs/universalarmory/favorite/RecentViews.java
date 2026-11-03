package com.sighs.universalarmory.favorite;

import com.sighs.universalarmory.Config;
import com.sighs.universalarmory.armory.ArmoryItemIdentity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class RecentViews {
    private RecentViews() {
    }

    public static List<ItemStack> items() {
        Map<String, ItemStack> unique = new LinkedHashMap<>();
        for (String serialized : Config.getRecentStacks()) {
            FavoriteStackCodec.decode(serialized).ifPresent(stack ->
                    unique.putIfAbsent(ArmoryItemIdentity.key(stack), stack));
        }
        return List.copyOf(unique.values());
    }

    public static void record(ItemStack stack) {
        Optional<String> encoded = FavoriteStackCodec.encode(stack);
        if (encoded.isEmpty()) {
            return;
        }
        String key = ArmoryItemIdentity.key(stack);
        List<String> serialized = new ArrayList<>();
        serialized.add(encoded.get());
        for (ItemStack previous : items()) {
            if (!ArmoryItemIdentity.key(previous).equals(key)) {
                FavoriteStackCodec.encode(previous).ifPresent(serialized::add);
            }
        }
        Config.setRecentStacks(serialized);
    }

    public static void remove(ItemStack stack) {
        String key = ArmoryItemIdentity.key(stack);
        List<String> serialized = new ArrayList<>();
        for (ItemStack previous : items()) {
            if (!ArmoryItemIdentity.key(previous).equals(key)) {
                FavoriteStackCodec.encode(previous).ifPresent(serialized::add);
            }
        }
        Config.setRecentStacks(serialized);
    }

    public static void clear() {
        Config.setRecentStacks(List.of());
    }
}
