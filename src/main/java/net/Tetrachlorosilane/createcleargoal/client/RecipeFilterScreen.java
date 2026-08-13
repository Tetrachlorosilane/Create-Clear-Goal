package net.Tetrachlorosilane.createcleargoal.client;

import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.simibubi.create.foundation.gui.widget.IconButton;

import net.Tetrachlorosilane.createcleargoal.content.recipefilter.FilterMode;
import net.Tetrachlorosilane.createcleargoal.content.recipefilter.OutputMatchMode;
import net.Tetrachlorosilane.createcleargoal.content.recipefilter.RecipeFilterClearPacket;
import net.Tetrachlorosilane.createcleargoal.content.recipefilter.RecipeFilterDeletePacket;
import net.Tetrachlorosilane.createcleargoal.content.recipefilter.RecipeFilterMenu;
import net.Tetrachlorosilane.createcleargoal.content.recipefilter.RecipeFilterModePacket;
import net.Tetrachlorosilane.createcleargoal.content.recipefilter.RecipeFilterNamePacket;
import net.Tetrachlorosilane.createcleargoal.content.recipefilter.RecipeFilterOutputMatchPacket;
import net.Tetrachlorosilane.createcleargoal.content.recipefilter.RecipeFilterSelectPacket;

import net.createmod.catnip.platform.CatnipServices;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Screen for the recipe filter.
 * <p>
 * Layout matches {@code textures/gui/recipe_filter.png} (panel 223x153):
 * <pre>
 *   y 25..41   entry list row: [index] [name EditBox] [delete @182,24]
 *   y 61..115  3x3 input grid (43+col*18, 61+row*18) | outputs (151, 61+i*18)
 *   y 129..147 radio buttons: modes @38/56/74, matches @97/115
 * </pre>
 * The window is sized as panel + player inventory so the whole GUI (panel and
 * inventory together) is centred, mirroring Create's AbstractFilterScreen.
 * Button backgrounds use Create's {@code AllGuiTextures.BUTTON} series (5
 * states); icons come from {@code recipe_filter_icons.png} via {@link ModIcons}.
 */
public class RecipeFilterScreen extends AbstractSimiContainerScreen<RecipeFilterMenu> {

	private EditBox nameBox;
	private IconButton deleteButton;
	private IconButton resetButton;
	private IconButton confirmButton;
	private IconButton[] modeButtons = new IconButton[3];
	private IconButton[] matchButtons = new IconButton[2];
	private boolean nameWasFocused = false;

	public RecipeFilterScreen(RecipeFilterMenu menu, Inventory inv, Component title) {
		super(menu, inv, title);
	}

	@Override
	protected void init() {
		// size the window to the whole GUI (panel + player inventory) so the
		// combined layout is centred, like AbstractFilterScreen does
		setWindowSize(Math.max(ModGuiTextures.RECIPE_FILTER_BG.getWidth(), AllGuiTextures.PLAYER_INVENTORY.getWidth()),
			ModGuiTextures.RECIPE_FILTER_BG.getHeight() + 4 + AllGuiTextures.PLAYER_INVENTORY.getHeight());
		setWindowOffset(-11, 5);
		super.init();
		int x = leftPos;
		int y = topPos;

		// name editor inside the entry list row, transparent like Create's
		// AddressEditBox: y+28 with height 9 centres the text at y=32.5
		nameBox = new EditBox(font, x + 54, y + 28, 112, 9, Component.literal(""));
		nameBox.setMaxLength(RecipeFilterMenu.MAX_NAME_LENGTH);
		nameBox.setCanLoseFocus(true);
		nameBox.setBordered(false);
		nameBox.setTextColor(0xffffff);
		addRenderableWidget(nameBox);

		// delete button at the right end of the entry list row
		deleteButton = new IconButton(x + 182, y + 24, ModIcons.I_DELETE);
		deleteButton.withCallback(() -> {
			// apply locally for instant feedback; the server confirms via packet
			menu.deleteSelectedEntry();
			CatnipServices.NETWORK.sendToServer(new RecipeFilterDeletePacket());
		});
		deleteButton.setToolTip(Component.translatable("recipe_filter.delete"));
		addRenderableWidget(deleteButton);

		// group 1: filter mode radio buttons @38/56/74, 129
		FilterMode[] modes = FilterMode.values();
		ModIcons[] modeIcons = { ModIcons.I_MODE_BLOCK, ModIcons.I_MODE_ALLOW_ONLY, ModIcons.I_MODE_LOCK };
		for (int i = 0; i < modes.length; i++) {
			final FilterMode mode = modes[i];
			modeButtons[i] = new IconButton(x + 38 + i * 18, y + 129, modeIcons[i]);
			modeButtons[i].withCallback(() -> {
				CatnipServices.NETWORK.sendToServer(new RecipeFilterModePacket(mode));
				menu.setMode(mode);
			});
			modeButtons[i].setToolTip(Component.translatable("recipe_filter.mode." + mode.getSerializedName()));
			addRenderableWidget(modeButtons[i]);
		}

		// group 2: output match radio buttons @97/115, 129
		OutputMatchMode[] matches = OutputMatchMode.values();
		ModIcons[] matchIcons = { ModIcons.I_MATCH_EXACT, ModIcons.I_MATCH_CONTAINS };
		for (int i = 0; i < matches.length; i++) {
			final OutputMatchMode match = matches[i];
			matchButtons[i] = new IconButton(x + 97 + i * 18, y + 129, matchIcons[i]);
			matchButtons[i].withCallback(() -> {
				CatnipServices.NETWORK.sendToServer(new RecipeFilterOutputMatchPacket(match));
				menu.setOutputMatch(match);
			});
			matchButtons[i].setToolTip(Component.translatable("recipe_filter.match." + match.getSerializedName()));
			addRenderableWidget(matchButtons[i]);
		}

		// reset + confirm buttons at the panel's bottom-right corner, positioned
		// exactly like Create's AbstractFilterScreen (bgW-62 / bgW-33, bgH-24)
		resetButton = new IconButton(x + ModGuiTextures.RECIPE_FILTER_BG.getWidth() - 62,
			y + ModGuiTextures.RECIPE_FILTER_BG.getHeight() - 24, AllIcons.I_TRASH);
		resetButton.withCallback(() -> {
			// clear all configuration locally and confirm on the server
			menu.clearContents();
			CatnipServices.NETWORK.sendToServer(new RecipeFilterClearPacket());
		});
		confirmButton = new IconButton(x + ModGuiTextures.RECIPE_FILTER_BG.getWidth() - 33,
			y + ModGuiTextures.RECIPE_FILTER_BG.getHeight() - 24, AllIcons.I_CONFIRM);
		confirmButton.withCallback(() -> {
			if (minecraft != null && minecraft.player != null)
				minecraft.player.closeContainer();
		});

		addRenderableWidget(resetButton);
		addRenderableWidget(confirmButton);
	}

	@Override
	public void containerTick() {
		super.containerTick();
		if (nameBox.isFocused()) {
			nameWasFocused = true;
		} else {
			if (nameWasFocused) {
				commitName();
				nameWasFocused = false;
			} else {
				// keep the name box in sync with the selected entry when not editing
				String display = getEntryDisplayName();
				if (!nameBox.getValue().equals(display))
					nameBox.setValue(display);
			}
		}

		// delete disabled while the new-entry placeholder is selected
		deleteButton.active = !menu.isNewEntrySelected();

		// radio highlight (green = selected)
		FilterMode mode = menu.getMode();
		for (int i = 0; i < modeButtons.length; i++)
			modeButtons[i].green = i == mode.ordinal();
		OutputMatchMode match = menu.getOutputMatch();
		for (int i = 0; i < matchButtons.length; i++)
			matchButtons[i].green = i == match.ordinal();
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double delta) {
		// while editing the name, the wheel belongs to the text box (cursor
		// movement), like Create's PackageFilterScreen does for its address box
		if (nameBox.isFocused())
			return nameBox.mouseScrolled(mouseX, mouseY, horizontalAmount, delta);
		int index = menu.getSelectedIndex();
		int max = menu.getEntryCount() < RecipeFilterMenu.ENTRIES ? menu.getEntryCount() : RecipeFilterMenu.ENTRIES - 1;
		int next = (int) Math.max(0, Math.min(max, index + (delta < 0 ? 1 : -1)));
		if (next != index)
			CatnipServices.NETWORK.sendToServer(new RecipeFilterSelectPacket(next));
		return true;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		// clicking the name area (outside the box) starts editing
		if (isInNameArea(mouseX, mouseY) && !nameBox.isMouseOver(mouseX, mouseY)) {
			nameBox.setFocused(true);
			if (menu.isNewEntrySelected())
				nameBox.setValue("");
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	private boolean isInNameArea(double mouseX, double mouseY) {
		return mouseX >= leftPos + 42 && mouseX <= leftPos + 172 && mouseY >= topPos + 25 && mouseY <= topPos + 41;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (nameBox.isFocused() && keyCode == 257) { // Enter
			commitName();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public void onClose() {
		if (nameBox.isFocused())
			commitName();
		super.onClose();
	}

	private void commitName() {
		String name = nameBox.getValue().trim();
		// Apply locally first; the server confirms via packet. Empty names are
		// valid for existing entries and restore the default output-derived name.
		menu.setName(menu.getSelectedIndex(), name);
		CatnipServices.NETWORK.sendToServer(new RecipeFilterNamePacket(menu.getSelectedIndex(), name));
		nameBox.setFocused(false);
	}

	private String getEntryDisplayName() {
		if (menu.isNewEntrySelected())
			return Component.translatable("recipe_filter.new_entry").getString();
		String name = menu.getSelectedTemplate().displayName();
		return name.isEmpty() ? Component.translatable("recipe_filter.new_entry").getString() : name;
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
		// custom upper panel from the texture atlas
		ModGuiTextures.RECIPE_FILTER_BG.render(graphics, leftPos, topPos);

		// player inventory panel: Create's built-in texture, aligned to the menu's
		// player slots (yOffset = panel height + 22)
		renderPlayerInventory(graphics, getLeftOfCentered(AllGuiTextures.PLAYER_INVENTORY.getWidth()),
			topPos + ModGuiTextures.RECIPE_FILTER_BG.getHeight() + 4);
	}

	@Override
	protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
		// entry index at the left of the list row
		graphics.drawString(font, String.valueOf(menu.getSelectedIndex() + 1) + ".", 43, 28, 0xFFE0E0E0, false);
	}
}
