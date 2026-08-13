package net.Tetrachlorosilane.createcleargoal.compat.rei;

import java.util.Optional;

import me.shedaniel.rei.api.client.registry.transfer.TransferHandler;

import net.Tetrachlorosilane.createcleargoal.client.RecipeFilterScreen;
import net.Tetrachlorosilane.createcleargoal.content.recipefilter.RecipeFilterEntry;
import net.Tetrachlorosilane.createcleargoal.content.recipefilter.RecipeFilterImportPacket;
import net.Tetrachlorosilane.createcleargoal.content.recipefilter.RecipeFilterItem;
import net.Tetrachlorosilane.createcleargoal.content.recipefilter.RecipeFilterMenu;

import net.createmod.catnip.platform.CatnipServices;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * REI transfer handler: the transfer button on a REI display imports the shown
 * recipe into the held filter, mirroring the JEI/EMI import. Only applies while
 * the recipe filter menu is open; the server re-resolves the recipe id.
 */
public class RecipeFilterImportTransferHandler implements TransferHandler {

	@Override
	public double getPriority() {
		// run before REI's default slot-filling handlers for this screen
		return 1000;
	}

	@Override
	public ApplicabilityResult checkApplicable(Context context) {
		if (!(context.getContainerScreen() instanceof RecipeFilterScreen))
			return ApplicabilityResult.createNotApplicable();
		return context.getDisplay().getDisplayLocation()
			.map(location -> ApplicabilityResult.createApplicable())
			.orElse(ApplicabilityResult.createNotApplicable());
	}

	@Override
	public Result handle(Context context) {
		if (!context.isActuallyCrafting())
			return Result.createNotApplicable();
		Optional<ResourceLocation> location = context.getDisplay().getDisplayLocation();
		if (location.isEmpty())
			return Result.createNotApplicable();
		ResourceLocation id = location.get();

		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null)
			return Result.createFailed(Component.translatable("recipe_filter.import_failed"));
		Recipe<?> recipe = mc.level.getRecipeManager()
			.byKey(id)
			.map(RecipeHolder::value)
			.orElse(null);
		if (recipe == null)
			return Result.createFailed(Component.translatable("recipe_filter.import_failed"));

		RecipeFilterScreen screen = (RecipeFilterScreen) context.getContainerScreen();
		// apply locally for instant feedback; the server re-imports authoritatively
		RecipeFilterEntry entry = RecipeFilterItem.fromRecipe(id, recipe, mc.level.registryAccess());
		RecipeFilterMenu menu = screen.getMenu();
		if (menu != null)
			menu.importEntry(entry);
		CatnipServices.NETWORK.sendToServer(new RecipeFilterImportPacket(id));
		return Result.createSuccessful();
	}
}
