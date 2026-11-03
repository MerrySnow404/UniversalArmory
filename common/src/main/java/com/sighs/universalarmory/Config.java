package com.sighs.universalarmory;

import com.sighs.universalarmory.spi.ArmoryServices;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

public final class Config {
    private static volatile List<String> favoriteStacks = List.of();
    private static volatile List<String> recentStacks = List.of();

    private Config() {
    }

    public static List<String> getFavoriteStacks() {
        return favoriteStacks;
    }

    public static List<String> getRecentStacks() {
        return recentStacks;
    }

    public static synchronized void setFavoriteStacks(Collection<? extends String> entries) {
        List<String> normalized = normalizeEntries(entries);
        ArmoryServices.platform().saveFavorites(normalized);
        favoriteStacks = normalized;
    }

    public static synchronized void setRecentStacks(Collection<? extends String> entries) {
        List<String> normalized = normalizeEntries(entries);
        ArmoryServices.platform().saveRecentViews(normalized);
        recentStacks = normalized;
    }

    public static synchronized void load(Collection<? extends String> favorites, Collection<? extends String> recent) {
        favoriteStacks = normalizeEntries(favorites);
        recentStacks = normalizeEntries(recent);
    }

    private static List<String> normalizeEntries(Collection<? extends String> entries) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String entry : entries) {
            if (entry != null && !entry.isBlank()) {
                normalized.add(entry.trim());
            }
        }
        return List.copyOf(normalized);
    }
}
