package com.sighs.universalarmory.favorite;

import com.sighs.universalarmory.Config;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class ConfigFavoriteRepository implements FavoriteRepository {
    public static final ConfigFavoriteRepository INSTANCE = new ConfigFavoriteRepository();

    private ConfigFavoriteRepository() {
    }

    @Override
    @NotNull
    public List<ItemStack> getFavorites() {
        List<ItemStack> favorites = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (String serialized : Config.getFavoriteStacks()) {
            FavoriteStackCodec.decode(serialized).ifPresent(stack -> {
                if (seen.add(com.sighs.universalarmory.armory.ArmoryItemIdentity.key(stack))) {
                    favorites.add(stack);
                }
            });
        }
        return List.copyOf(favorites);
    }

    @Override
    @NotNull
    public List<String> getSerializedFavorites() {
        return Config.getFavoriteStacks();
    }

    @Override
    public boolean addFavorite(@NotNull ItemStack stack) {
        Optional<String> encoded = FavoriteStackCodec.encode(stack);
        if (encoded.isEmpty() || containsFavorite(stack)) {
            return false;
        }
        List<String> favorites = new ArrayList<>(Config.getFavoriteStacks());
        favorites.add(encoded.get());
        Config.setFavoriteStacks(favorites);
        return true;
    }

    @Override
    public boolean removeFavorite(@NotNull ItemStack stack) {
        if (!supports(stack)) {
            return false;
        }
        String key = com.sighs.universalarmory.armory.ArmoryItemIdentity.key(stack);
        List<String> favorites = new ArrayList<>(Config.getFavoriteStacks());
        boolean removed = favorites.removeIf(serialized -> FavoriteStackCodec.decode(serialized)
                .map(item -> com.sighs.universalarmory.armory.ArmoryItemIdentity.key(item).equals(key)).orElse(false));
        if (removed) {
            Config.setFavoriteStacks(favorites);
        }
        return removed;
    }

    @Override
    public boolean containsFavorite(@NotNull ItemStack stack) {
        if (!supports(stack)) {
            return false;
        }
        String key = com.sighs.universalarmory.armory.ArmoryItemIdentity.key(stack);
        return getFavorites().stream().anyMatch(item ->
                com.sighs.universalarmory.armory.ArmoryItemIdentity.key(item).equals(key));
    }

    @Override
    public boolean replaceFavorites(@NotNull Collection<String> serializedFavorites) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (String serialized : serializedFavorites) {
            FavoriteStackCodec.decode(serialized).ifPresent(stack -> {
                if (seen.add(com.sighs.universalarmory.armory.ArmoryItemIdentity.key(stack))) {
                    FavoriteStackCodec.encode(stack).ifPresent(normalized::add);
                }
            });
        }

        List<String> updated = List.copyOf(normalized);
        if (Objects.equals(updated, Config.getFavoriteStacks())) {
            return false;
        }

        Config.setFavoriteStacks(updated);
        return true;
    }

    @Override
    public void clearFavorites() {
        Config.setFavoriteStacks(List.of());
    }

    @Override
    public boolean supports(@NotNull ItemStack stack) {
        return FavoriteStackCodec.supports(stack);
    }
}
