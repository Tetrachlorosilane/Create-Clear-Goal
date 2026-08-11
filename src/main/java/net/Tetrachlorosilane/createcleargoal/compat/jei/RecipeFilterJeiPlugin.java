package net.Tetrachlorosilane.createcleargoal.compat.jei;

import javax.annotation.ParametersAreNonnullByDefault;

import com.simibubi.create.AllRecipeTypes;

import net.Tetrachlorosilane.createcleargoal.client.RecipeFilterScreen;
import net.Tetrachlorosilane.createcleargoal.Createcleargoal;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * JEI plugin for the recipe filter: registers a transfer handler for every
 * Create recipe type so any shown recipe can be imported into the held filter,
 * plus ghost-ingredient dragging into the filter's input/output slots (reusing
 * Create's GhostIngredientHandler for GhostItemMenu-based screens).
 * <p>
 * The class is only loaded by JEI's own plugin scanner, so it is safe to ship
 * with JEI absent.
 */
@JeiPlugin
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class RecipeFilterJeiPlugin implements IModPlugin {

	private static final ResourceLocation ID =
		ResourceLocation.fromNamespaceAndPath(Createcleargoal.MODID, "jei_plugin");

	@Override
	public ResourceLocation getPluginUid() {
		return ID;
	}

	@Override
	public void registerGuiHandlers(IGuiHandlerRegistration registration) {
		// dragging items from JEI into the filter's 12 ghost slots
		registration.addGhostIngredientHandler(RecipeFilterScreen.class, new RecipeFilterGhostIngredientHandler());
	}

	@Override
	public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
		for (AllRecipeTypes type : AllRecipeTypes.values()) {
			net.minecraft.world.item.crafting.RecipeType<?> mcType = type.getType();
			if (mcType == null)
				continue;
			ResourceLocation id = BuiltInRegistries.RECIPE_TYPE.getKey(mcType);
			if (id == null)
				continue;

			@SuppressWarnings({ "unchecked", "rawtypes" })
			RecipeType<RecipeHolder<?>> jeiType =
				(RecipeType<RecipeHolder<?>>) (RecipeType) RecipeType.<Recipe<?>>createRecipeHolderType(id);
			registration.addRecipeTransferHandler(new RecipeImportTransferHandler(jeiType), jeiType);
		}
	}
}
