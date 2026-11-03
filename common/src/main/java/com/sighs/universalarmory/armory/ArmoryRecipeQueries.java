package com.sighs.universalarmory.armory;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ArmoryRecipeQueries {
    private ArmoryRecipeQueries() {
    }

    public static List<Recipe<?>> forItem(Level level, ItemStack target) {
        if (level == null || target.isEmpty()) {
            return List.of();
        }
        Map<ResourceLocation, Recipe<?>> recipes = new LinkedHashMap<>();
        TimelessAPI.getAllRecipes().forEach(recipes::put);
        for (Recipe<?> recipe : level.getRecipeManager().getRecipes()) {
            if (recipe instanceof GunSmithTableRecipe || recipe.getType() == RecipeType.CRAFTING) {
                recipes.put(recipe.getId(), recipe);
            }
        }
        return recipes.values().stream().filter(recipe -> {
            ItemStack output = recipe.getResultItem(level.registryAccess());
            return !output.isEmpty() && ArmoryItemIdentity.kind(output) == ArmoryItemIdentity.kind(target)
                    && Objects.equals(ArmoryItemIdentity.resourceId(output), ArmoryItemIdentity.resourceId(target));
        }).sorted(Comparator.comparing(recipe -> recipe.getId().toString())).toList();
    }
}
