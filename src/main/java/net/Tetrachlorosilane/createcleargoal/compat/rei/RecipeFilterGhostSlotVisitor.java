package net.Tetrachlorosilane.createcleargoal.compat.rei;

import java.util.stream.Stream;

import com.simibubi.create.foundation.gui.menu.GhostItemSubmitPacket;

import me.shedaniel.math.Point;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.gui.drag.DraggableStack;
import me.shedaniel.rei.api.client.gui.drag.DraggableStackVisitor;
import me.shedaniel.rei.api.client.gui.drag.DraggedAcceptorResult;
import me.shedaniel.rei.api.client.gui.drag.DraggingContext;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.entry.type.VanillaEntryTypes;

import net.Tetrachlorosilane.createcleargoal.client.RecipeFilterScreen;
import net.Tetrachlorosilane.createcleargoal.content.recipefilter.RecipeFilterMenu;

import net.createmod.catnip.platform.CatnipServices;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * REI drag &amp; drop visitor for {@link RecipeFilterScreen}: dropping an item
 * from REI onto one of the filter's 12 ghost slots places it there and syncs
 * the change to the server (Create's {@code GhostItemSubmitPacket}).
 */
public class RecipeFilterGhostSlotVisitor implements DraggableStackVisitor<RecipeFilterScreen> {

	@Override
	public <R extends Screen> boolean isHandingScreen(R screen) {
		return screen instanceof RecipeFilterScreen;
	}

	@Override
	public DraggedAcceptorResult acceptDraggedStack(DraggingContext<RecipeFilterScreen> context, DraggableStack stack) {
		EntryStack<?> entry = stack.getStack();
		if (entry.getType() != VanillaEntryTypes.ITEM)
			return DraggedAcceptorResult.PASS;
		ItemStack itemStack = entry.castValue();
		if (itemStack.isEmpty())
			return DraggedAcceptorResult.PASS;
		Point pos = context.getCurrentPosition();
		if (pos == null)
			return DraggedAcceptorResult.PASS;

		RecipeFilterScreen screen = context.getScreen();
		for (int i = 36; i < 36 + RecipeFilterMenu.TOTAL_SLOTS; i++) {
			Slot slot = screen.getMenu().slots.get(i);
			if (!slot.isActive())
				continue;
			int x = screen.getGuiLeft() + slot.x;
			int y = screen.getGuiTop() + slot.y;
			if (pos.x >= x && pos.x < x + 16 && pos.y >= y && pos.y < y + 16) {
				ItemStack copy = itemStack.copy();
				copy.setCount(1);
				int slotIndex = i - 36;
				screen.getMenu().ghostInventory.setStackInSlot(slotIndex, copy);
				CatnipServices.NETWORK.sendToServer(new GhostItemSubmitPacket(copy, slotIndex));
				return DraggedAcceptorResult.CONSUMED;
			}
		}
		return DraggedAcceptorResult.PASS;
	}

	@Override
	public Stream<BoundsProvider> getDraggableAcceptingBounds(DraggingContext<RecipeFilterScreen> context,
		DraggableStack stack) {
		RecipeFilterScreen screen = context.getScreen();
		Stream.Builder<BoundsProvider> builder = Stream.builder();
		for (int i = 36; i < 36 + RecipeFilterMenu.TOTAL_SLOTS; i++) {
			Slot slot = screen.getMenu().slots.get(i);
			if (!slot.isActive())
				continue;
			int x = screen.getGuiLeft() + slot.x;
			int y = screen.getGuiTop() + slot.y;
			builder.accept(BoundsProvider.ofRectangle(new Rectangle(x, y, 16, 16)));
		}
		return builder.build();
	}
}
