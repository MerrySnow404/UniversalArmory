package com.sighs.universalarmory.favorite;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

public interface FavoriteRepository {
    @NotNull
    List<ItemStack> getFavorites();

    @NotNull
    List<String> getSerializedFavorites();

    boolean addFavorite(@NotNull ItemStack stack);

    boolean removeFavorite(@NotNull ItemStack stack);

    boolean containsFavorite(@NotNull ItemStack stack);

    boolean replaceFavorites(@NotNull Collection<String> serializedFavorites);

    void clearFavorites();

    boolean supports(@NotNull ItemStack stack);
}
