package net.Tetrachlorosilane.createcleargoal.compat.emi;

import java.util.List;

import com.simibubi.create.foundation.gui.menu.GhostItemSubmitPacket;

import net.Tetrachlorosilane.createcleargoal.AllMenuTypes;
import net.Tetrachlorosilane.createcleargoal.client.RecipeFilterScreen;
import net.Tetrachlorosilane.createcleargoal.content.recipefilter.RecipeFilterMenu;

import net.createmod.catnip.platform.CatnipServices;

import dev.emi.emi.api.EmiDragDropHandler;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.stack.EmiStack;

import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * EMI plugin for the recipe filter: registers a recipe handler so the EMI
 * "fill recipe" button imports the shown recipe into the held filter, plus
 * drag &amp; drop from EMI's sidebar into the filter's 12 ghost slots.
 * <p>
 * The class is only loaded by EMI's own {@code @EmiEntrypoint} scanner, so it
 * is safe to ship with EMI absent.
 */
@EmiEntrypoint
public class RecipeFilterEmiPlugin implements EmiPlugin {

	@Override
	public void register(EmiRegistry registry) {
		registry.addRecipeHandler(AllMenuTypes.RECIPE_FILTER.get(), new RecipeFilterEmiRecipeHandler());

		// dragging an item from EMI's sidebar onto a ghost slot places it there
		registry.addDragDropHandler(RecipeFilterScreen.class, new EmiDragDropHandler.SlotBased<>(
			(screen, slot) -> isGhostSlot(slot),
			(screen, slot, ingredient) -> {
				List<EmiStack> stacks = ingredient.getEmiStacks();
				if (stacks.isEmpty())
					return;
				ItemStack stack = stacks.get(0).getItemStack();
				if (stack.isEmpty())
					return;
				ItemStack copy = stack.copy();
				copy.setCount(1);
				int slotIndex = slot.index - 36;
				screen.getMenu().ghostInventory.setStackInSlot(slotIndex, copy);
				CatnipServices.NETWORK.sendToServer(new GhostItemSubmitPacket(copy, slotIndex));
			}));
	}

	private static boolean isGhostSlot(Slot slot) {
		return slot.index >= 36 && slot.index < 36 + RecipeFilterMenu.TOTAL_SLOTS && slot.isActive();
	}
}
