package net.Tetrachlorosilane.createcleargoal.content.recipefilter;

import java.util.ArrayList;
import java.util.List;

import com.simibubi.create.content.logistics.filter.AbstractFilterMenu;

import net.Tetrachlorosilane.createcleargoal.AllDataComponents;
import net.Tetrachlorosilane.createcleargoal.AllMenuTypes;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * Ghost-slot menu for editing the recipe entries of a held RecipeFilterItem.
 * <p>
 * Layout: a single selected entry is edited through a fixed set of slots
 * (9 inputs in a 3x3 grid + 3 outputs). The player scrolls/selects between
 * up to {@link #ENTRIES} entries; the selected index is synchronised via a
 * DataSlot. Entries are written back to the item whenever the selection
 * changes and when the menu closes.
 */
public class RecipeFilterMenu extends AbstractFilterMenu {

	public static final int ENTRIES = 9;
	public static final int INPUT_SLOTS = 9;
	/** GUI output slots; imported basin recipes may store one more hidden output. */
	public static final int OUTPUT_SLOTS = 3;
	public static final int MAX_ITEM_OUTPUTS = 4;
	public static final int MAX_FLUID_INPUTS = 2;
	public static final int MAX_FLUID_OUTPUTS = 2;
	public static final int MAX_NAME_LENGTH = 64;
	public static final int TOTAL_SLOTS = INPUT_SLOTS + OUTPUT_SLOTS;

	private final DataSlot selectedIndex;
	/** Filter-level mode (ordinal of FilterMode), synchronised via DataSlot. */
	private final DataSlot modeSlot;
	/** Selected entry's output-match mode (ordinal of OutputMatchMode). */
	private final DataSlot outputMatchSlot;
	/** Entry templates at open time (name/recipeId/outputMatch); index-aligned. */
	private List<RecipeFilterEntry> templates;

	public RecipeFilterMenu(MenuType<?> type, int id, Inventory inv, RegistryFriendlyByteBuf extraData) {
		super(type, id, inv, extraData);
		selectedIndex = addDataSlot(DataSlot.standalone());
		modeSlot = addDataSlot(DataSlot.standalone());
		outputMatchSlot = addDataSlot(DataSlot.standalone());
		initSlots();
	}

	/** MenuType factory constructor: matches {@code IContainerFactory}. */
	public RecipeFilterMenu(int id, Inventory inv, RegistryFriendlyByteBuf extraData) {
		this(AllMenuTypes.RECIPE_FILTER.get(), id, inv, extraData);
	}

	public RecipeFilterMenu(MenuType<?> type, int id, Inventory inv, ItemStack stack) {
		super(type, id, inv, stack);
		selectedIndex = addDataSlot(DataSlot.standalone());
		modeSlot = addDataSlot(DataSlot.standalone());
		outputMatchSlot = addDataSlot(DataSlot.standalone());
		initSlots();
	}

	private void initSlots() {
		modeSlot.set(RecipeFilterItem.getMode(contentHolder).ordinal());
		RecipeFilterEntry first = RecipeFilterItem.getEntry(contentHolder, 0);
		outputMatchSlot.set(first == null ? OutputMatchMode.EXACT.ordinal() : first.outputMatch().ordinal());
	}

	public static RecipeFilterMenu create(int id, Inventory inv, ItemStack stack) {
		return new RecipeFilterMenu(AllMenuTypes.RECIPE_FILTER.get(), id, inv, stack);
	}

	/**
	 * Clears all configuration of the filter: every entry, the filter mode and
	 * the selection. Mirrors Create's {@code IClearableMenu.clearContents}
	 * pattern; the server packet handler calls this on the server-side menu so
	 * the item's components are reset too.
	 */
	@Override
	public void clearContents() {
		super.clearContents(); // empty the ghost slots
		for (int i = 0; i < ghostInventory.getSlots(); i++)
			getSlot(i + 36).setChanged();
		templates.clear();
		RecipeFilterItem.setEntries(contentHolder, List.of());
		RecipeFilterItem.setMode(contentHolder, FilterMode.LOCK);
		modeSlot.set(FilterMode.LOCK.ordinal());
		outputMatchSlot.set(OutputMatchMode.EXACT.ordinal());
		selectedIndex.set(0);
	}

	// --- public accessors for the screen ---

	public int getSelectedIndex() {
		return selectedIndex.get();
	}

	public FilterMode getMode() {
		return FilterMode.values()[modeSlot.get()];
	}

	/** Sets the filter-level mode and persists it to the edited item. */
	public void setMode(FilterMode mode) {
		RecipeFilterItem.setMode(contentHolder, mode);
		modeSlot.set(mode.ordinal());
	}

	public OutputMatchMode getOutputMatch() {
		return OutputMatchMode.values()[outputMatchSlot.get()];
	}

	public void setOutputMatch(OutputMatchMode mode) {
		outputMatchSlot.set(mode.ordinal());
		int index = getSelectedIndex();
		RecipeFilterEntry edited = RecipeFilterItem.fromHandler(getTemplate(index), ghostInventory).withOutputMatch(mode);
		RecipeFilterItem.setEntry(contentHolder, index, edited);
		while (templates.size() <= index)
			templates.add(RecipeFilterEntry.empty());
		templates.set(index, edited);
	}

	/** Renames the selected entry, preserving any slots already filled. */
	public void setName(int index, String name) {
		if (index != getSelectedIndex())
			return;
		if (name == null)
			name = "";
		if (name.length() > MAX_NAME_LENGTH)
			name = name.substring(0, MAX_NAME_LENGTH);
		RecipeFilterEntry edited = RecipeFilterItem.fromHandler(getTemplate(index), ghostInventory).withName(name);
		RecipeFilterItem.setEntry(contentHolder, index, edited);
		while (templates.size() <= index)
			templates.add(RecipeFilterEntry.empty());
		templates.set(index, edited);
	}

	/** Number of saved entries; an extra "new recipe" slot exists if this is below ENTRIES. */
	public int getEntryCount() {
		return RecipeFilterItem.getEntries(contentHolder).size();
	}

	/** True when the selection points at the trailing "new recipe" placeholder. */
	public boolean isNewEntrySelected() {
		return getSelectedIndex() >= getEntryCount();
	}

	public RecipeFilterEntry getSelectedTemplate() {
		return getTemplate(getSelectedIndex());
	}

	private RecipeFilterEntry getTemplate(int index) {
		if (index < templates.size())
			return templates.get(index);
		return RecipeFilterEntry.empty();
	}

	// --- selection switching (server-side, driven by packets) ---

	/** Saves the currently selected entry back into the item, then switches. */
	public void selectEntry(int index) {
		int max = Math.min(RecipeFilterItem.getEntries(contentHolder).size(), ENTRIES - 1);
		if (index < 0 || index > max)
			return;
		saveCurrentEntry();
		selectedIndex.set(index);
		loadSelectedEntry();
	}

	/**
	 * Imports a recipe entry (JEI) into the filter, appending it and selecting it
	 * so an open GUI shows the imported recipe immediately. Both the client and
	 * the server menu apply this; the server copy is authoritative.
	 */
	public void importEntry(RecipeFilterEntry entry) {
		List<RecipeFilterEntry> entries = new ArrayList<>(RecipeFilterItem.getEntries(contentHolder));
		if (entries.size() >= ENTRIES)
			return;
		int index = entries.size();
		entries.add(entry);
		RecipeFilterItem.setEntries(contentHolder, entries);
		while (templates.size() <= index)
			templates.add(RecipeFilterEntry.empty());
		templates.set(index, entry);
		// Selecting must NOT save the old selection onto the just-imported slot:
		// when the old selection is the trailing "new recipe" placeholder, its
		// index equals the import's new index and a save would overwrite the
		// freshly imported entry (the reported "JEI import does not show" bug).
		if (getSelectedIndex() != index)
			saveCurrentEntry();
		selectedIndex.set(index);
		loadSelectedEntry();
	}

	public void saveCurrentEntry() {
		int index = selectedIndex.get();
		RecipeFilterEntry edited = RecipeFilterItem.fromHandler(getTemplate(index), ghostInventory);
		RecipeFilterItem.setEntry(contentHolder, index, edited);
		// keep templates in sync so a later save round-trips the same name/mode
		while (templates.size() <= index)
			templates.add(RecipeFilterEntry.empty());
		templates.set(index, edited);
	}

	private void loadSelectedEntry() {
		RecipeFilterEntry entry = RecipeFilterItem.getEntry(contentHolder, getSelectedIndex());
		ItemStackHandler loaded = RecipeFilterItem.toHandler(entry == null ? RecipeFilterEntry.empty() : entry);
		for (int i = 0; i < ghostInventory.getSlots(); i++) {
			ghostInventory.setStackInSlot(i, loaded.getStackInSlot(i));
			getSlot(i + 36).setChanged();
		}
		outputMatchSlot.set((entry == null ? OutputMatchMode.EXACT : entry.outputMatch()).ordinal());
	}

	/** Deletes the selected entry and moves the selection to the same slot. */
	public void deleteSelectedEntry() {
		int index = getSelectedIndex();
		if (isNewEntrySelected())
			return;
		RecipeFilterItem.removeEntry(contentHolder, index);
		if (index >= templates.size())
			return;
		templates.remove(index);
		loadSelectedEntry();
	}

	// --- menu plumbing ---

	@Override
	protected int getPlayerInventoryXOffset() {
		// aligns with renderPlayerInventory(getLeftOfCentered(...)): the slot row
		// must sit on the texture's slot frames. For a 223px window this is
		// (223-176)/2 + 11 (window offset) + 8 (frame inset in the texture) = 42.
		return 42;
	}

	@Override
	protected int getPlayerInventoryYOffset() {
		return 175; // panel height (153) + 22, matching Create's alignment convention
	}

	@Override
	protected void addFilterSlots() {
		// 3x3 input grid at (43,61), 18px pitch — aligned to the panel texture
		for (int row = 0; row < 3; row++)
			for (int col = 0; col < 3; col++)
				addSlot(new SlotItemHandler(ghostInventory, col + row * 3, 43 + col * 18, 61 + row * 18));
		// 3 vertical output slots at x=151
		for (int i = 0; i < OUTPUT_SLOTS; i++)
			addSlot(new SlotItemHandler(ghostInventory, INPUT_SLOTS + i, 151, 61 + i * 18));
	}

	@Override
	protected ItemStackHandler createGhostInventory() {
		// NOTE: this runs inside super() (MenuBase.init), before field
		// initializers execute — lazy-init the template list here.
		if (templates == null)
			templates = new ArrayList<>();
		// templates for all saved entries + placeholders up to the selection
		templates.clear();
		templates.addAll(RecipeFilterItem.getEntries(contentHolder));
		ItemStackHandler handler = new ItemStackHandler(TOTAL_SLOTS);
		// load entry 0 into the slot view
		RecipeFilterEntry first = RecipeFilterItem.getEntry(contentHolder, 0);
		if (first != null) {
			ItemStackHandler loaded = RecipeFilterItem.toHandler(first);
			for (int i = 0; i < handler.getSlots(); i++)
				handler.setStackInSlot(i, loaded.getStackInSlot(i));
		}
		return handler;
	}

	@Override
	protected void saveData(ItemStack contentHolder) {
		saveCurrentEntry();
		// trim trailing empty entries
		List<RecipeFilterEntry> entries = new ArrayList<>(RecipeFilterItem.getEntries(contentHolder));
		while (!entries.isEmpty() && entries.get(entries.size() - 1).isEmpty())
			entries.remove(entries.size() - 1);
		if (entries.isEmpty()) {
			contentHolder.remove(AllDataComponents.RECIPE_FILTER_ENTRIES.get());
			return;
		}
		RecipeFilterItem.setEntries(contentHolder, entries);
	}
}
