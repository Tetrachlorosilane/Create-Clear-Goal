package net.Tetrachlorosilane.createcleargoal.content.recipefilter;

import java.util.List;

import com.simibubi.create.content.logistics.filter.FilterItemStack;

import net.Tetrachlorosilane.createcleargoal.AllDataComponents;

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

	private final List<RecipeFilterEntry> entries;

	public RecipeFilterItemStack(ItemStack filter) {
		super(filter);
		entries = filter.getOrDefault(AllDataComponents.RECIPE_FILTER_ENTRIES.get(), List.of());
	}

	public List<RecipeFilterEntry> getEntries() {
		return entries;
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
