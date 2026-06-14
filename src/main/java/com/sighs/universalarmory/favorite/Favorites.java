package com.sighs.universalarmory.favorite;

public final class Favorites {
    private static final FavoriteRepository GLOBAL = ConfigFavoriteRepository.INSTANCE;

    private Favorites() {
    }

    public static FavoriteRepository global() {
        return GLOBAL;
    }
}
