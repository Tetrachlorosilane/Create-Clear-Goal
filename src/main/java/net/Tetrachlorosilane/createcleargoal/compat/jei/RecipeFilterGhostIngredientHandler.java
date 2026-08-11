package net.Tetrachlorosilane.createcleargoal.compat.jei;

import java.util.LinkedList;
import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

import com.simibubi.create.foundation.gui.menu.GhostItemSubmitPacket;

import net.Tetrachlorosilane.createcleargoal.client.RecipeFilterScreen;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * JEI ghost-ingredient handler for {@link RecipeFilterScreen}: dragging an item
 * from JEI onto one of the filter's 12 ghost slots places it there and syncs
 * the change to the server (Create's {@code GhostItemSubmitPacket}).
 * <p>
 * This is a concrete re-implementation of Create's {@code GhostIngredientHandler}
 * bound to {@code RecipeFilterScreen}: Create's version targets the generic
 * {@code AbstractSimiContainerScreen<T>} and is only registered for its own
 * {@code AbstractFilterScreen} subclasses, so a dedicated handler is needed
 * here.
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class RecipeFilterGhostIngredientHandler implements IGhostIngredientHandler<RecipeFilterScreen> {

	@Override
	public <I> List<Target<I>> getTargetsTyped(RecipeFilterScreen gui, ITypedIngredient<I> ingredient,
		boolean doStart) {
		List<Target<I>> targets = new LinkedList<>();
		if (ingredient.getType() == VanillaTypes.ITEM_STACK)
			for (int i = 36; i < gui.getMenu().slots.size(); i++)
				if (gui.getMenu().slots.get(i)
					.isActive())
					targets.add(new GhostTarget<>(gui, i - 36));
		return targets;
	}

	@Override
	public void onComplete() {}

	@Override
	public boolean shouldHighlightTargets() {
		return true;
	}

	private static class GhostTarget<I> implements Target<I> {

		private final Rect2i area;
		private final RecipeFilterScreen gui;
		private final int slotIndex;

		public GhostTarget(RecipeFilterScreen gui, int slotIndex) {
			this.gui = gui;
			this.slotIndex = slotIndex;
			Slot slot = gui.getMenu().slots.get(slotIndex + 36);
			this.area = new Rect2i(gui.getGuiLeft() + slot.x, gui.getGuiTop() + slot.y, 16, 16);
		}

		@Override
		public Rect2i getArea() {
			return area;
		}

		@Override
		public void accept(I ingredient) {
			ItemStack stack = ((ItemStack) ingredient).copy();
			stack.setCount(1);
			gui.getMenu().ghostInventory.setStackInSlot(slotIndex, stack);
			CatnipServices.NETWORK.sendToServer(new GhostItemSubmitPacket(stack, slotIndex));
		}
	}
}
