package net.Tetrachlorosilane.createcleargoal.mixin;

import java.util.List;
import java.util.Optional;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.content.kinetics.mixer.MechanicalMixerBlockEntity;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.basin.BasinOperatingBlockEntity;

import net.Tetrachlorosilane.createcleargoal.content.recipefilter.RecipeFilterHelper;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;

/**
 * Hooks {@link BasinOperatingBlockEntity} for the recipe filter:
 * <ul>
 *   <li>{@code getMatchingRecipes} (RETURN) - lock the candidate list onto a
 *       recorded recipe (see {@link RecipeFilterHelper}).</li>
 *   <li>{@code matchBasinRecipe} (HEAD) - press, mixer and any future basin
 *       operator gate "continue with the previous recipe" on
 *       {@code matchBasinRecipe(currentRecipe)}. We compare the filter item
 *       currently in the basin's filter slot against the one recorded at the
 *       last successful re-selection; any change (placed, removed or edited)
 *       fails that check once so the machine breaks continuity and re-selects
 *       with the new filter. State comparison makes this independent of the
 *       filter item's NBT events.</li>
 *   <li>{@code updateBasin} (HEAD/RETURN) - tracks re-selections: the filter
 *       snapshot is refreshed only after a re-selection that actually ran
 *       (entered while not running), and the candidate loop never breaks
 *       continuity because it runs inside a re-selection.</li>
 * </ul>
 */
@Mixin(BasinOperatingBlockEntity.class)
public abstract class BasinOperatingBlockEntityMixin {

	/** Filter item seen at the last successful re-selection. */
	@Unique
	private ItemStack createcleargoal$lastFilter = ItemStack.EMPTY;
	/** True while {@code updateBasin} is executing a re-selection. */
	@Unique
	private boolean createcleargoal$inReselect = false;
	/** Whether the machine was running when the current re-selection started. */
	@Unique
	private boolean createcleargoal$wasRunning = false;

	@Shadow
	protected abstract Optional<BasinBlockEntity> getBasin();

	@Shadow
	protected abstract boolean isRunning();

	@Shadow
	protected Recipe<?> currentRecipe;

	@Unique
	private ItemStack createcleargoal$currentFilter() {
		Optional<BasinBlockEntity> basin = getBasin();
		if (basin.isEmpty())
			return ItemStack.EMPTY;
		com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour filtering = basin.get()
			.getFilter();
		if (filtering == null)
			return ItemStack.EMPTY;
		ItemStack filter = filtering.getFilter();
		return filter == null ? ItemStack.EMPTY : filter;
	}

	@Inject(method = "getMatchingRecipes", at = @At("RETURN"), cancellable = true)
	private void createcleargoal$lockRecipes(CallbackInfoReturnable<List<Recipe<?>>> cir) {
		// MechanicalMixerBlockEntity overrides getMatchingRecipes() and appends
		// potion-mixing recipes after super returns. Locking here would hand the
		// override an immutable list to mutate (crash) and let the appended recipes
		// bypass BLOCK/ALLOW_ONLY; the dedicated mixer mixin locks the final list.
		if ((Object) this instanceof MechanicalMixerBlockEntity)
			return;
		Optional<BasinBlockEntity> basin = getBasin();
		if (basin.isEmpty())
			return;
		List<Recipe<?>> locked = RecipeFilterHelper.tryLockBasin(basin.get(), cir.getReturnValue());
		if (locked != null)
			cir.setReturnValue(locked);
	}

	@Inject(method = "updateBasin", at = @At("HEAD"))
	private void createcleargoal$enterReselect(CallbackInfoReturnable<Boolean> cir) {
		createcleargoal$inReselect = true;
		createcleargoal$wasRunning = isRunning();
	}

	@Inject(method = "updateBasin", at = @At("RETURN"))
	private void createcleargoal$exitReselect(CallbackInfoReturnable<Boolean> cir) {
		// only a re-selection that actually ran (was not skipped by isRunning)
		// picked a recipe with the current filter, so refresh the snapshot there
		if (!createcleargoal$wasRunning)
			createcleargoal$lastFilter = createcleargoal$currentFilter();
		createcleargoal$inReselect = false;
	}

	/**
	 * Breaks recipe continuity while the filter slot content differs from the
	 * last re-selection. The candidate loop (inside a re-selection) is excluded
	 * via {@link #createcleargoal$inReselect}; only the "continue with the
	 * previous recipe" check ({@code matchBasinRecipe(currentRecipe)}) is
	 * affected. Not consuming any state here: the snapshot refreshes at the
	 * next successful re-selection.
	 */
	@Inject(method = "matchBasinRecipe", at = @At("HEAD"), cancellable = true)
	private void createcleargoal$breakContinuityOnFilterChange(Recipe<?> recipe, CallbackInfoReturnable<Boolean> cir) {
		if (createcleargoal$inReselect || recipe != currentRecipe)
			return;
		if (!ItemStack.isSameItemSameComponents(createcleargoal$lastFilter, createcleargoal$currentFilter()))
			cir.setReturnValue(false);
	}
}
