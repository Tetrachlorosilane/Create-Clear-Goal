package net.Tetrachlorosilane.createcleargoal.content.recipefilter;

import com.simibubi.create.content.logistics.filter.FilterItemStack;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import net.neoforged.neoforge.fluids.FluidStack;

/**
 * FilterItemStack wrapper for {@link RecipeFilterItem}.
 * <p>
 * {@code test} always passes: the recipe filter is a marker that is consumed
 * by the recipe-selection logic (see {@link RecipeFilterHelper}) rather than
 * by plain item/fluid flow tests. This keeps it safe in any filter slot.
 */
public class RecipeFilterItemStack extends FilterItemStack {

	public RecipeFilterItemStack(ItemStack filter) {
		super(filter);
	}

	@Override
	public boolean test(Level world, ItemStack stack, boolean matchNBT) {
		return true;
	}

	@Override
	public boolean test(Level world, FluidStack stack, boolean matchNBT) {
		return true;
	}
}
