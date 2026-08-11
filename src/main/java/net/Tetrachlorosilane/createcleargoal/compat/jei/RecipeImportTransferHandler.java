package net.Tetrachlorosilane.createcleargoal.compat.jei;

import java.util.Optional;

import javax.annotation.ParametersAreNonnullByDefault;

import org.jetbrains.annotations.Nullable;

import net.Tetrachlorosilane.createcleargoal.content.recipefilter.RecipeFilterImportPacket;
import net.Tetrachlorosilane.createcleargoal.content.recipefilter.RecipeFilterMenu;

import net.createmod.catnip.platform.CatnipServices;

import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * JEI transfer handler for the recipe filter menu: clicking the JEI transfer
 * button while the filter GUI is open imports the shown recipe into the held
 * filter (recipe id + input/output snapshots).
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class RecipeImportTransferHandler implements IRecipeTransferHandler<RecipeFilterMenu, RecipeHolder<?>> {

	private final RecipeType<RecipeHolder<?>> type;

	public RecipeImportTransferHandler(RecipeType<RecipeHolder<?>> type) {
		this.type = type;
	}

	@Override
	public Class<? extends RecipeFilterMenu> getContainerClass() {
		return RecipeFilterMenu.class;
	}

	@Override
	public Optional<MenuType<RecipeFilterMenu>> getMenuType() {
		return Optional.empty();
	}

	@Override
	public RecipeType<RecipeHolder<?>> getRecipeType() {
		return type;
	}

	@Override
	public @Nullable IRecipeTransferError transferRecipe(RecipeFilterMenu menu, RecipeHolder<?> recipe,
		IRecipeSlotsView recipeSlots, Player player, boolean maxTransfer, boolean doTransfer) {
		if (!doTransfer)
			return null;
		CatnipServices.NETWORK.sendToServer(new RecipeFilterImportPacket(recipe.id()));
		return null;
	}
}
