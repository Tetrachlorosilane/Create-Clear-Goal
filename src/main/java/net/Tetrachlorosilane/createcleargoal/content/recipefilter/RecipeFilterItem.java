package net.Tetrachlorosilane.createcleargoal.content.recipefilter;

import java.util.ArrayList;
import java.util.List;

import com.simibubi.create.content.logistics.filter.FilterItem;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;

import net.Tetrachlorosilane.createcleargoal.AllDataComponents;

import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * A filter item that records whole recipes (input + output) and lets
 * processing machines restrict their recipe selection according to the
 * filter's mode (block / allow-only / lock).
 * <p>
 * The actual behaviour is implemented in {@link RecipeFilterHelper} and
 * consumed by Create's recipe-selection points; {@link FilterItemStack#test}
 * is a no-op (always passes) so the filter never accidentally blocks plain
 * item flow.
 */
public class RecipeFilterItem extends FilterItem {

	public RecipeFilterItem(Properties properties) {
		super(properties);
	}

	// --- filter-level property: mode ---

	public static FilterMode getMode(ItemStack stack) {
		return stack.getOrDefault(AllDataComponents.RECIPE_FILTER_MODE.get(), FilterMode.LOCK);
	}

	public static void setMode(ItemStack stack, FilterMode mode) {
		stack.set(AllDataComponents.RECIPE_FILTER_MODE.get(), mode);
	}

	// --- entries ---

	public static RecipeFilterEntry getEntry(ItemStack stack, int index) {
		List<RecipeFilterEntry> entries = getEntries(stack);
		return index >= 0 && index < entries.size() ? entries.get(index) : null;
	}

	public static List<RecipeFilterEntry> getEntries(ItemStack stack) {
		return stack.getOrDefault(AllDataComponents.RECIPE_FILTER_ENTRIES.get(), List.of());
	}

	public static void setEntries(ItemStack stack, List<RecipeFilterEntry> entries) {
		stack.set(AllDataComponents.RECIPE_FILTER_ENTRIES.get(), List.copyOf(entries));
	}

	/** Appends an entry, capped at the number of entries the GUI can display. */
	public static void addEntry(ItemStack stack, RecipeFilterEntry entry) {
		List<RecipeFilterEntry> entries = new ArrayList<>(getEntries(stack));
		if (entries.size() >= RecipeFilterMenu.ENTRIES)
			return;
		entries.add(entry);
		setEntries(stack, entries);
	}

	/** Replaces or appends an entry. */
	public static void setEntry(ItemStack stack, int index, RecipeFilterEntry entry) {
		List<RecipeFilterEntry> entries = new ArrayList<>(getEntries(stack));
		if (index < 0 || index >= RecipeFilterMenu.ENTRIES)
			return;
		while (entries.size() <= index)
			entries.add(RecipeFilterEntry.empty());
		entries.set(index, entry);
		setEntries(stack, entries);
	}

	/** Removes an entry. */
	public static void removeEntry(ItemStack stack, int index) {
		List<RecipeFilterEntry> entries = new ArrayList<>(getEntries(stack));
		if (index < 0 || index >= entries.size())
			return;
		entries.remove(index);
		setEntries(stack, entries);
	}

	/**
	 * Builds an entry from a recipe (JEI import): snapshots the first item of
	 * each ingredient, the first fluid of each fluid ingredient, and the
	 * recipe's item/fluid outputs. Imported entries are matched by their
	 * {@code recipeId} at runtime, so tag ingredients and fluid ingredients keep
	 * their full recipe semantics instead of being reduced to one stack.
	 */
	public static RecipeFilterEntry fromRecipe(ResourceLocation recipeId, Recipe<?> recipe,
		HolderLookup.Provider registries) {
		List<ItemStack> inputs = new ArrayList<>();
		List<List<ItemStack>> inputAlternatives = new ArrayList<>();
		for (Ingredient ingredient : recipe.getIngredients()) {
			List<ItemStack> alternatives = new ArrayList<>();
			for (ItemStack stack : ingredient.getItems())
				if (!stack.isEmpty())
					alternatives.add(stack.copy());
			if (alternatives.isEmpty())
				continue;
			inputAlternatives.add(alternatives);
			inputs.add(alternatives.get(0));
			if (inputs.size() >= RecipeFilterMenu.INPUT_SLOTS)
				break;
		}

		List<ItemStack> outputs = new ArrayList<>();
		ItemStack result = recipe.getResultItem(registries);
		if (!result.isEmpty())
			outputs.add(result);

		List<FluidStack> fluidInputs = new ArrayList<>();
		List<FluidStack> fluidOutputs = new ArrayList<>();
		List<List<FluidStack>> fluidInputAlternatives = new ArrayList<>();
		if (recipe instanceof ProcessingRecipe<?, ?> processing) {
			for (SizedFluidIngredient ingredient : processing.getFluidIngredients()) {
				List<FluidStack> alternatives = new ArrayList<>();
				for (FluidStack fluid : ingredient.getFluids())
					if (!fluid.isEmpty())
						alternatives.add(fluid.copy());
				if (alternatives.isEmpty())
					continue;
				fluidInputAlternatives.add(alternatives);
				fluidInputs.add(alternatives.get(0));
				if (fluidInputs.size() >= RecipeFilterMenu.MAX_FLUID_INPUTS)
					break;
			}
			for (ProcessingOutput output : processing.getRollableResults()) {
				if (outputs.size() >= RecipeFilterMenu.MAX_ITEM_OUTPUTS)
					break;
				ItemStack stack = output.getStack();
				if (!stack.isEmpty() && outputs.stream().noneMatch(o -> ItemStack.isSameItem(o, stack)))
					outputs.add(stack);
			}
			for (FluidStack fluid : processing.getFluidResults()) {
				if (fluidOutputs.size() >= RecipeFilterMenu.MAX_FLUID_OUTPUTS)
					break;
				if (!fluid.isEmpty() && fluidOutputs.stream().noneMatch(o -> FluidStack.isSameFluid(o, fluid)))
					fluidOutputs.add(fluid.copy());
			}
		}

		String defaultName;
		if (!outputs.isEmpty())
			defaultName = outputs.get(0).getHoverName().getString();
		else if (!fluidOutputs.isEmpty())
			defaultName = fluidOutputs.get(0).getHoverName().getString();
		else
			defaultName = "";
		return RecipeFilterEntry.ofRecipe(defaultName, recipeId, inputs, outputs, fluidInputs, fluidOutputs,
			inputAlternatives, fluidInputAlternatives);
	}

	// --- FilterItem overrides ---

	@Override
	public List<Component> makeSummary(ItemStack filter) {
		List<Component> list = new ArrayList<>();
		List<RecipeFilterEntry> entries = getEntries(filter);
		if (entries.isEmpty())
			return list;

		list.add(Component.translatable("recipe_filter.summary", entries.size())
			.withStyle(ChatFormatting.GOLD));
		int shown = 0;
		for (RecipeFilterEntry entry : entries) {
			if (shown >= 3) {
				list.add(Component.literal("- ...").withStyle(ChatFormatting.DARK_GRAY));
				break;
			}
			list.add(Component.literal("- " + entry.displayName()).withStyle(ChatFormatting.GRAY));
			shown++;
		}
		return list;
	}

	@Override
	public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
		return RecipeFilterMenu.create(id, inv, player.getMainHandItem());
	}

	@Override
	public DataComponentType<?> getComponentType() {
		return AllDataComponents.RECIPE_FILTER_ENTRIES.get();
	}

	@Override
	public FilterItemStack makeStackWrapper(ItemStack filter) {
		return new RecipeFilterItemStack(filter);
	}

	@Override
	public ItemStack[] getFilterItems(ItemStack stack) {
		List<ItemStack> all = new ArrayList<>();
		for (RecipeFilterEntry entry : getEntries(stack)) {
			all.addAll(entry.nonEmptyInputs());
			all.addAll(entry.nonEmptyOutputs());
		}
		return all.toArray(ItemStack[]::new);
	}

	// --- GUI handler round-trip: fixed 9 inputs + 3 outputs ---

	public static ItemStackHandler toHandler(RecipeFilterEntry entry) {
		ItemStackHandler handler = new ItemStackHandler(RecipeFilterMenu.TOTAL_SLOTS);
		List<ItemStack> inputs = entry.inputs();
		for (int i = 0; i < RecipeFilterMenu.INPUT_SLOTS && i < inputs.size(); i++)
			handler.setStackInSlot(i, inputs.get(i));
		List<ItemStack> outputs = entry.outputs();
		for (int i = 0; i < RecipeFilterMenu.OUTPUT_SLOTS && i < outputs.size(); i++)
			handler.setStackInSlot(RecipeFilterMenu.INPUT_SLOTS + i, outputs.get(i));
		return handler;
	}

	public static RecipeFilterEntry fromHandler(RecipeFilterEntry template, ItemStackHandler handler) {
		List<ItemStack> inputs = new ArrayList<>();
		for (int i = 0; i < RecipeFilterMenu.INPUT_SLOTS; i++)
			inputs.add(handler.getStackInSlot(i));

		List<ItemStack> outputs = new ArrayList<>();
		for (int i = 0; i < RecipeFilterMenu.OUTPUT_SLOTS; i++) {
			ItemStack out = handler.getStackInSlot(RecipeFilterMenu.INPUT_SLOTS + i);
			if (!out.isEmpty())
				outputs.add(out);
		}

		// Imported basin recipes may have four item outputs while the GUI exposes
		// three. Preserve any extra imported outputs so saving an imported entry
		// does not silently truncate it back to three.
		List<ItemStack> storedOutputs = template.nonEmptyOutputs();
		for (int i = RecipeFilterMenu.OUTPUT_SLOTS; i < storedOutputs.size() && i < RecipeFilterMenu.MAX_ITEM_OUTPUTS; i++)
			outputs.add(storedOutputs.get(i));

		return template.withContents(inputs, outputs, template.fluidInputs(), template.fluidOutputs());
	}
}
