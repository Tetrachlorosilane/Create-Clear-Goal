package net.Tetrachlorosilane.createcleargoal.content.recipefilter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.simibubi.create.content.kinetics.saw.SawBlockEntity;
import com.simibubi.create.content.logistics.filter.ListFilterItem;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import com.simibubi.create.foundation.item.ItemHelper;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Core restriction logic for the recipe filter, driven by the filter's
 * {@link FilterMode}:
 * <ul>
 *   <li>{@link FilterMode#BLOCK} - drop candidates that match a recorded entry
 *       (output matching per the entry's {@link OutputMatchMode}).</li>
 *   <li>{@link FilterMode#ALLOW_ONLY} - keep only candidates that match a
 *       recorded entry.</li>
 *   <li>{@link FilterMode#LOCK} - only when the machine's inputs equal a
 *       recorded entry's inputs, lock onto that entry's recipe; otherwise
 *       leave selection untouched. If the recorded recipe is currently
 *       unavailable (wrong heat/quantity, removed recipe), the machine halts
 *       instead of silently falling back to other candidates.</li>
 * </ul>
 * <p>
 * LOCK semantics (as specified): if input A can be processed into B or C, an
 * entry "A -> B" means input A only ever produces B, while other inputs (D, E)
 * are unaffected and follow the normal recipe selection.
 * <p>
 * Consumed by the Create mixins; never blocks plain item flow (the
 * {@link RecipeFilterItemStack} test always passes).
 */
public final class RecipeFilterHelper {

	private RecipeFilterHelper() {
	}

	/**
	 * @return the candidate list to use for a basin operator (mixer / press), or
	 *         {@code null} to let Create's original selection logic run; an
	 *         empty list halts the machine (LOCK with an unavailable recipe).
	 */
	public static List<Recipe<?>> tryLockBasin(BasinBlockEntity basin, List<Recipe<?>> candidates) {
		ItemStack filterStack = filterStackOf(basin.getFilter());
		if (filterStack == null)
			return null;
		List<RecipeFilterEntry> entries = RecipeFilterItem.getEntries(filterStack);
		if (entries.isEmpty())
			return null;

		Level level = basin.getLevel();
		if (level == null)
			return null;
		RecipeManager manager = level.getRecipeManager();

		FilterMode mode = RecipeFilterItem.getMode(filterStack);
		switch (mode) {
		case BLOCK -> {
			List<Recipe<?>> result = new ArrayList<>();
			for (Recipe<?> candidate : candidates)
				if (!matchesAnyEntry(entries, candidate, manager, level))
					result.add(candidate);
			return result;
		}
		case ALLOW_ONLY -> {
			List<Recipe<?>> result = new ArrayList<>();
			for (Recipe<?> candidate : candidates)
				if (matchesAnyEntry(entries, candidate, manager, level))
					result.add(candidate);
			return result;
		}
		case LOCK -> {
			List<ItemStack> machineInputs = basinInputs(basin);
			for (RecipeFilterEntry entry : entries) {
				if (entry.recipeId().isPresent()) {
					Recipe<?> locked = lockBasinEntry(manager, entry, candidates, level);
					if (locked != null) {
						List<Recipe<?>> result = new ArrayList<>(1);
						result.add(locked);
						return result;
					}
					// Imported entries match using the real recipe's ingredients,
					// including tags and fluids; halt if the basin currently
					// satisfies that recipe but the candidate is unavailable.
					if (basinRecipeInputsMatch(manager, basin, entry))
						return List.of();
					continue;
				}
				if (!inputsMatch(entry, machineInputs))
					continue;
				// Placeholder-ish entries (no recipe id and no recorded outputs)
				// cannot lock onto anything; skip them instead of halting.
				if (entry.nonEmptyOutputs().isEmpty())
					continue;
				Recipe<?> locked = lockBasinEntry(manager, entry, candidates, level);
				if (locked != null) {
					// mutable: subclasses of BasinOperatingBlockEntity may append
					// candidates to the returned list (e.g. the mixer's potion recipes)
					List<Recipe<?>> result = new ArrayList<>(1);
					result.add(locked);
					return result;
				}
				// The recorded entry's inputs match, but its recipe cannot run
				// right now (wrong heat/quantity, removed recipe, ...). LOCK means
				// "this input only runs the recorded recipe", so halt instead of
				// silently falling back to other candidates.
				return List.of();
			}
			return null;
		}
		}
		return null;
	}

	private static Recipe<?> lockBasinEntry(RecipeManager manager, RecipeFilterEntry entry,
		List<Recipe<?>> candidates, Level level) {
		if (entry.recipeId().isPresent()) {
			Optional<RecipeHolder<?>> holder = manager.byKey(entry.recipeId().get());
			if (holder.isPresent())
				for (Recipe<?> candidate : candidates)
					if (candidate == holder.get().value())
						return candidate;
			return null;
		}
		// Manual entry: the machine input matched; lock the first candidate whose
		// outputs match the recorded outputs.
		if (entry.nonEmptyOutputs().isEmpty())
			return null;
		for (Recipe<?> candidate : candidates)
			if (outputMatches(candidate, entry, level))
				return candidate;
		return null;
	}

	/**
	 * Whether the basin currently satisfies an imported entry's recipe
	 * <b>inputs</b> (item types and fluids only). This deliberately ignores the
	 * recipe's heat requirement and the basin's output space: LOCK gates on
	 * "do the inputs match the recorded recipe" alone, then decides run-vs-halt
	 * from the recipe's actual availability - it must never fall back to other
	 * recipes once the recorded inputs are present. If the recipe was removed,
	 * falls back to the recorded item inputs.
	 */
	private static boolean basinRecipeInputsMatch(RecipeManager manager, BasinBlockEntity basin,
		RecipeFilterEntry entry) {
		Optional<RecipeHolder<?>> holder = manager.byKey(entry.recipeId().orElseThrow());
		if (holder.isEmpty())
			return inputsMatch(entry, basinInputs(basin));
		return basinInputsSatisfy(basin, holder.get().value());
	}

	/**
	 * Checks whether the basin currently contains the recorded recipe's input
	 * <b>types</b> (items and fluids). Quantities are intentionally ignored:
	 * recipe matching only cares about which item/fluid types are present, not
	 * how many of each. Heat and output-space checks are also excluded, so this
	 * is a pure LOCK gate rather than a "can run right now" test.
	 */
	private static boolean basinInputsSatisfy(BasinBlockEntity basin, Recipe<?> recipe) {
		Level level = basin.getLevel();
		if (level == null)
			return false;
		IItemHandler items = level.getCapability(Capabilities.ItemHandler.BLOCK, basin.getBlockPos(), null);
		IFluidHandler fluids = level.getCapability(Capabilities.FluidHandler.BLOCK, basin.getBlockPos(), null);
		if (items == null || fluids == null)
			return false;

		for (Ingredient ingredient : recipe.getIngredients()) {
			if (ingredient.isEmpty())
				continue;
			boolean found = false;
			for (int slot = 0; slot < items.getSlots(); slot++) {
				ItemStack stack = items.getStackInSlot(slot);
				if (!stack.isEmpty() && ingredient.test(stack)) {
					found = true;
					break;
				}
			}
			if (!found)
				return false;
		}

		List<SizedFluidIngredient> fluidIngredients = recipe instanceof ProcessingRecipe<?, ?> processing
			? processing.getFluidIngredients()
			: List.of();
		for (SizedFluidIngredient fluidIngredient : fluidIngredients) {
			boolean found = false;
			for (int tank = 0; tank < fluids.getTanks(); tank++) {
				FluidStack fluidStack = fluids.getFluidInTank(tank);
				if (!fluidStack.isEmpty() && fluidIngredient.test(fluidStack)) {
					found = true;
					break;
				}
			}
			if (!found)
				return false;
		}
		return true;
	}

	/**
	 * @return the candidate list to use for the mechanical saw, or {@code null}
	 *         to let Create's original selection logic run; an empty list halts
	 *         the machine (LOCK with an unavailable recipe).
	 */
	public static List<RecipeHolder<? extends Recipe<?>>> tryLockSaw(SawBlockEntity saw, FilteringBehaviour filtering,
		List<RecipeHolder<? extends Recipe<?>>> candidates) {
		ItemStack filterStack = filterStackOf(filtering);
		if (filterStack == null)
			return null;
		List<RecipeFilterEntry> entries = RecipeFilterItem.getEntries(filterStack);
		if (entries.isEmpty())
			return null;

		Level level = saw.getLevel();
		if (level == null)
			return null;
		RecipeManager manager = level.getRecipeManager();
		ItemStack input = saw.inventory.getStackInSlot(0);

		FilterMode mode = RecipeFilterItem.getMode(filterStack);
		switch (mode) {
		case BLOCK -> {
			List<RecipeHolder<? extends Recipe<?>>> result = new ArrayList<>();
			for (RecipeHolder<? extends Recipe<?>> candidate : candidates)
				if (!matchesAnyEntry(entries, candidate, level))
					result.add(candidate);
			return result;
		}
		case ALLOW_ONLY -> {
			List<RecipeHolder<? extends Recipe<?>>> result = new ArrayList<>();
			for (RecipeHolder<? extends Recipe<?>> candidate : candidates)
				if (matchesAnyEntry(entries, candidate, level))
					result.add(candidate);
			return result;
		}
		case LOCK -> {
			for (RecipeFilterEntry entry : entries) {
				if (entry.recipeId().isPresent()) {
					RecipeHolder<? extends Recipe<?>> locked = lockSawEntry(manager, entry, candidates, level);
					if (locked != null) {
						List<RecipeHolder<? extends Recipe<?>>> result = new ArrayList<>(1);
						result.add(locked);
						return result;
					}
					if (sawRecipeInputMatches(manager, entry, input))
						return List.of();
					continue;
				}
				if (!sawInputMatches(entry, input))
					continue;
				if (entry.nonEmptyOutputs().isEmpty())
					continue;
				RecipeHolder<? extends Recipe<?>> locked = lockSawEntry(manager, entry, candidates, level);
				if (locked != null) {
					List<RecipeHolder<? extends Recipe<?>>> result = new ArrayList<>(1);
					result.add(locked);
					return result;
				}
				// Same LOCK semantics as the basin: halt, don't fall back.
				return List.of();
			}
			return null;
		}
		}
		return null;
	}

	private static RecipeHolder<? extends Recipe<?>> lockSawEntry(RecipeManager manager, RecipeFilterEntry entry,
		List<RecipeHolder<? extends Recipe<?>>> candidates, Level level) {
		if (entry.recipeId().isPresent()) {
			Optional<RecipeHolder<?>> holder = manager.byKey(entry.recipeId().get());
			if (holder.isPresent())
				for (RecipeHolder<? extends Recipe<?>> candidate : candidates)
					if (candidate.id().equals(holder.get().id()))
						return candidate;
			return null;
		}
		// Manual entry: lock the first candidate whose outputs match the recorded outputs.
		if (entry.nonEmptyOutputs().isEmpty())
			return null;
		for (RecipeHolder<? extends Recipe<?>> candidate : candidates)
			if (outputMatches(candidate.value(), entry, level))
				return candidate;
		return null;
	}

	/** Whether the saw input matches an imported entry's real first ingredient. */
	private static boolean sawRecipeInputMatches(RecipeManager manager, RecipeFilterEntry entry, ItemStack input) {
		Optional<RecipeHolder<?>> holder = manager.byKey(entry.recipeId().orElseThrow());
		if (holder.isEmpty())
			return sawInputMatches(entry, input);
		List<Ingredient> ingredients = holder.get().value().getIngredients();
		return !ingredients.isEmpty() && ingredients.get(0).test(input);
	}

	// --- matching helpers ---

	private static ItemStack filterStackOf(FilteringBehaviour filtering) {
		if (filtering == null)
			return null;
		return findRecipeFilter(filtering.getFilter(), 0);
	}

	/**
	 * Finds the actual recipe filter behind a filter slot. Besides a directly
	 * placed RecipeFilterItem, this also walks into Create's list filter so the
	 * item can be nested inside one. The list-filter mixin keeps nested recipe
	 * filters out of Create's ordinary allow/deny item matching.
	 */
	private static ItemStack findRecipeFilter(ItemStack stack, int depth) {
		if (stack.isEmpty() || depth > 4)
			return null;
		if (stack.getItem() instanceof RecipeFilterItem)
			return stack;
		if (stack.getItem() instanceof ListFilterItem listFilter) {
			ItemStackHandler contents = listFilter.getFilterItemHandler(stack);
			for (int i = 0; i < contents.getSlots(); i++) {
				ItemStack nested = findRecipeFilter(contents.getStackInSlot(i), depth + 1);
				if (nested != null)
					return nested;
			}
		}
		return null;
	}

	/**
	 * BLOCK / ALLOW_ONLY gate for a candidate recipe. An entry matches the
	 * candidate when it IS the imported recipe (recipeId anchor, keeps tag /
	 * fluid / 4-output semantics exact) OR when its recorded outputs match the
	 * candidate's outputs per the entry's {@link OutputMatchMode}. The OR keeps
	 * the per-entry Exact/Contains mode meaningful for imported recipes: e.g.
	 * BLOCK + Exact on an imported "A -> B" also removes a different "C -> B"
	 * candidate. If the recorded recipe no longer exists, output matching alone
	 * still applies.
	 */
	private static boolean matchesAnyEntry(List<RecipeFilterEntry> entries, Recipe<?> candidate, RecipeManager manager,
		Level level) {
		for (RecipeFilterEntry entry : entries) {
			boolean byId = false;
			if (entry.recipeId().isPresent()) {
				Optional<RecipeHolder<?>> holder = manager.byKey(entry.recipeId().get());
				byId = holder.isPresent() && holder.get().value() == candidate;
			}
			if (byId || outputMatches(candidate, entry, level))
				return true;
		}
		return false;
	}

	private static boolean matchesAnyEntry(List<RecipeFilterEntry> entries, RecipeHolder<? extends Recipe<?>> candidate,
		Level level) {
		for (RecipeFilterEntry entry : entries) {
			boolean byId = entry.recipeId().isPresent() && candidate.id().equals(entry.recipeId().get());
			if (byId || outputMatches(candidate.value(), entry, level))
				return true;
		}
		return false;
	}

	/** Non-empty slots of the basin's input inventory. */
	private static List<ItemStack> basinInputs(BasinBlockEntity basin) {
		List<ItemStack> inputs = new ArrayList<>();
		for (int i = 0; i < basin.inputInventory.getSlots(); i++) {
			ItemStack stack = basin.inputInventory.getStackInSlot(i);
			if (!stack.isEmpty())
				inputs.add(stack);
		}
		return inputs;
	}

	/**
	 * LOCK gate: the machine's inputs must equal the entry's recorded inputs,
	 * ignoring quantities - duplicate stacks of the same item are treated as
	 * one. The recorded and machine input sets must have the same distinct item
	 * types; any extra, missing or different type fails the gate.
	 */
	private static boolean inputsMatch(RecipeFilterEntry entry, List<ItemStack> machineInputs) {
		List<ItemStack> recorded = distinct(entry.nonEmptyInputs());
		List<ItemStack> machine = distinct(machineInputs);
		if (recorded.isEmpty() || machine.isEmpty() || recorded.size() != machine.size())
			return false;
		boolean[] used = new boolean[machine.size()];
		for (ItemStack recordedStack : recorded) {
			int found = -1;
			for (int i = 0; i < machine.size(); i++)
				if (!used[i] && ItemHelper.sameItem(machine.get(i), recordedStack)) {
					found = i;
					break;
				}
			if (found == -1)
				return false;
			used[found] = true;
		}
		return true;
	}

	/** LOCK gate for the saw: the single input slot must match the entry's sole recorded input type. */
	private static boolean sawInputMatches(RecipeFilterEntry entry, ItemStack input) {
		List<ItemStack> recorded = distinct(entry.nonEmptyInputs());
		return recorded.size() == 1 && ItemHelper.sameItem(recorded.get(0), input);
	}

	/** Removes duplicate stacks (same item type) so quantity is irrelevant. */
	private static List<ItemStack> distinct(List<ItemStack> stacks) {
		List<ItemStack> result = new ArrayList<>();
		for (ItemStack stack : stacks)
			if (!containsSame(result, stack))
				result.add(stack);
		return result;
	}

	/**
	 * Compares a candidate recipe's outputs against the entry's recorded outputs,
	 * honouring the entry's {@link OutputMatchMode}.
	 */
	private static boolean outputMatches(Recipe<?> recipe, RecipeFilterEntry entry, Level level) {
		List<ItemStack> recordedItems = entry.nonEmptyOutputs();
		List<FluidStack> recordedFluids = entry.nonEmptyFluidOutputs();
		if (recordedItems.isEmpty() && recordedFluids.isEmpty())
			return false;

		List<ItemStack> recipeItems = new ArrayList<>();
		ItemStack result = recipe.getResultItem(level.registryAccess());
		if (!result.isEmpty())
			recipeItems.add(result);

		List<FluidStack> recipeFluids = new ArrayList<>();
		if (recipe instanceof ProcessingRecipe<?, ?> processing)
			for (ProcessingOutput output : processing.getRollableResults()) {
				ItemStack stack = output.getStack();
				if (!stack.isEmpty() && !containsSame(recipeItems, stack))
					recipeItems.add(stack);
			}
		if (recipe instanceof ProcessingRecipe<?, ?> processing)
			for (FluidStack fluid : processing.getFluidResults()) {
				if (!fluid.isEmpty() && !containsSameFluid(recipeFluids, fluid))
					recipeFluids.add(fluid);
			}

		switch (entry.outputMatch()) {
		case EXACT -> {
			// Compare as sets of item types: duplicate recordings must not match
			// a recipe with extra distinct outputs (recorded [A,A] vs [A,B]).
			List<ItemStack> recordedItemDistinct = distinct(recordedItems);
			List<ItemStack> recipeItemDistinct = distinct(recipeItems);
			List<FluidStack> recordedFluidDistinct = distinctFluids(recordedFluids);
			List<FluidStack> recipeFluidDistinct = distinctFluids(recipeFluids);
			if (recipeItemDistinct.size() != recordedItemDistinct.size()
				|| recipeFluidDistinct.size() != recordedFluidDistinct.size())
				return false;
			for (ItemStack r : recordedItemDistinct)
				if (!containsSame(recipeItemDistinct, r))
					return false;
			for (FluidStack r : recordedFluidDistinct)
				if (!containsSameFluid(recipeFluidDistinct, r))
					return false;
			return true;
		}
		case CONTAINS -> {
			for (ItemStack r : recordedItems)
				if (!containsSame(recipeItems, r))
					return false;
			for (FluidStack r : recordedFluids)
				if (!containsSameFluid(recipeFluids, r))
					return false;
			return true;
		}
		}
		return false;
	}

	private static boolean containsSame(List<ItemStack> stacks, ItemStack stack) {
		for (ItemStack s : stacks)
			if (ItemHelper.sameItem(s, stack))
				return true;
		return false;
	}

	private static List<FluidStack> distinctFluids(List<FluidStack> stacks) {
		List<FluidStack> result = new ArrayList<>();
		for (FluidStack stack : stacks)
			if (!containsSameFluid(result, stack))
				result.add(stack);
		return result;
	}

	private static boolean containsSameFluid(List<FluidStack> stacks, FluidStack stack) {
		for (FluidStack s : stacks)
			if (FluidStack.isSameFluid(s, stack))
				return true;
		return false;
	}
}
