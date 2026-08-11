package net.Tetrachlorosilane.createcleargoal.content.recipefilter;

import java.util.ArrayList;
import java.util.List;

import com.simibubi.create.content.logistics.filter.FilterItem;
import com.simibubi.create.content.logistics.filter.FilterItemStack;

import net.Tetrachlorosilane.createcleargoal.AllDataComponents;
import net.Tetrachlorosilane.createcleargoal.Createcleargoal;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

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

	public static RecipeFilterItem get() {
		return Createcleargoal.RECIPE_FILTER.get();
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

	/** Replaces or appends an entry; returns the actual index it ended up at. */
	public static int setEntry(ItemStack stack, int index, RecipeFilterEntry entry) {
		List<RecipeFilterEntry> entries = new ArrayList<>(getEntries(stack));
		if (index < 0 || index >= RecipeFilterMenu.ENTRIES)
			return -1;
		while (entries.size() <= index)
			entries.add(RecipeFilterEntry.empty());
		entries.set(index, entry);
		setEntries(stack, entries);
		return index;
	}

	/** Removes an entry and returns true if it existed. */
	public static boolean removeEntry(ItemStack stack, int index) {
		List<RecipeFilterEntry> entries = new ArrayList<>(getEntries(stack));
		if (index < 0 || index >= entries.size())
			return false;
		entries.remove(index);
		setEntries(stack, entries);
		return true;
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
		return template.withContents(inputs, outputs);
	}
}
