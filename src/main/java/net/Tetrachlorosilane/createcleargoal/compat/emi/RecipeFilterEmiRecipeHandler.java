package net.Tetrachlorosilane.createcleargoal.compat.emi;

import java.util.List;

import net.Tetrachlorosilane.createcleargoal.content.recipefilter.RecipeFilterEntry;
import net.Tetrachlorosilane.createcleargoal.content.recipefilter.RecipeFilterImportPacket;
import net.Tetrachlorosilane.createcleargoal.content.recipefilter.RecipeFilterItem;
import net.Tetrachlorosilane.createcleargoal.content.recipefilter.RecipeFilterMenu;

import net.createmod.catnip.platform.CatnipServices;

import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.api.recipe.handler.EmiRecipeHandler;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * EMI recipe handler for the recipe filter menu: clicking EMI's fill button
 * while the filter GUI is open imports the shown recipe into the held filter
 * (recipe id + input/output snapshots), mirroring the JEI transfer handler.
 */
public class RecipeFilterEmiRecipeHandler implements EmiRecipeHandler<RecipeFilterMenu> {

	@Override
	public EmiPlayerInventory getInventory(AbstractContainerScreen<RecipeFilterMenu> screen) {
		// Importing a recipe consumes nothing; craftability is irrelevant.
		return new EmiPlayerInventory(List.of());
	}

	@Override
	public boolean supportsRecipe(EmiRecipe recipe) {
		// only recipes resolvable by the vanilla RecipeManager can be imported
		// (the server re-resolves the id from the RecipeManager)
		return recipe.getBackingRecipe() != null;
	}

	@Override
	public boolean canCraft(EmiRecipe recipe, EmiCraftContext<RecipeFilterMenu> context) {
		return supportsRecipe(recipe);
	}

	@Override
	public boolean craft(EmiRecipe recipe, EmiCraftContext<RecipeFilterMenu> context) {
		RecipeHolder<?> holder = recipe.getBackingRecipe();
		if (holder == null)
			return false;
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null)
			return false;
		// apply locally for instant feedback; the server re-imports authoritatively
		RecipeFilterEntry entry = RecipeFilterItem.fromRecipe(holder.id(), holder.value(), mc.level.registryAccess());
		context.getScreenHandler().importEntry(entry);
		CatnipServices.NETWORK.sendToServer(new RecipeFilterImportPacket(holder.id()));
		return true;
	}
}
