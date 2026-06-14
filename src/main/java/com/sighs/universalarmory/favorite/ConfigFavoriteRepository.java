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
        for (String serialized : Config.getFavoriteStacks()) {
            FavoriteStackCodec.decode(serialized).ifPresent(favorites::add);
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
        if (encoded.isEmpty()) {
            return false;
        }

        List<String> favorites = new ArrayList<>(Config.getFavoriteStacks());
        if (favorites.contains(encoded.get())) {
            return false;
        }

        favorites.add(encoded.get());
        Config.setFavoriteStacks(favorites);
        return true;
    }

    @Override
    public boolean removeFavorite(@NotNull ItemStack stack) {
        Optional<String> encoded = FavoriteStackCodec.encode(stack);
        if (encoded.isEmpty()) {
            return false;
        }

        List<String> favorites = new ArrayList<>(Config.getFavoriteStacks());
        boolean removed = favorites.remove(encoded.get());
        if (removed) {
            Config.setFavoriteStacks(favorites);
        }
        return removed;
    }

    @Override
    public boolean containsFavorite(@NotNull ItemStack stack) {
        Optional<String> encoded = FavoriteStackCodec.encode(stack);
        return encoded.isPresent() && Config.getFavoriteStacks().contains(encoded.get());
    }

    @Override
    public boolean replaceFavorites(@NotNull Collection<String> serializedFavorites) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String serialized : serializedFavorites) {
            FavoriteStackCodec.decode(serialized)
                    .flatMap(FavoriteStackCodec::encode)
                    .ifPresent(normalized::add);
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
