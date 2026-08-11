package net.Tetrachlorosilane.createcleargoal.mixin;

import java.util.List;
import java.util.Optional;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.basin.BasinOperatingBlockEntity;

import net.Tetrachlorosilane.createcleargoal.content.recipefilter.FilterChangedMarker;
import net.Tetrachlorosilane.createcleargoal.content.recipefilter.RecipeFilterHelper;

import net.minecraft.world.item.crafting.Recipe;

/**
 * Hooks {@link BasinOperatingBlockEntity#getMatchingRecipes()} so that a
 * recipe filter on the basin below can lock the machine onto a recorded
 * recipe, and {@link BasinOperatingBlockEntity#continueWithPreviousRecipe()}
 * so a filter change takes effect immediately instead of waiting for the
 * continuous processing flow to break.
 */
@Mixin(BasinOperatingBlockEntity.class)
public abstract class BasinOperatingBlockEntityMixin implements FilterChangedMarker {

	/** Set when the basin's filter slot content changes; breaks recipe continuity. */
	@Unique
	private boolean createcleargoal$filterChanged = false;

	/** Called by FilteringBehaviourMixin when the filter item is placed/removed. */
	@Override
	@Unique
	public void createcleargoal$markFilterChanged() {
		createcleargoal$filterChanged = true;
	}

	@Shadow
	protected abstract Optional<BasinBlockEntity> getBasin();

	@Inject(method = "getMatchingRecipes", at = @At("RETURN"), cancellable = true)
	private void createcleargoal$lockRecipes(CallbackInfoReturnable<List<Recipe<?>>> cir) {
		Optional<BasinBlockEntity> basin = getBasin();
		if (basin.isEmpty())
			return;
		List<Recipe<?>> locked = RecipeFilterHelper.tryLockBasin(basin.get(), cir.getReturnValue());
		if (locked != null)
			cir.setReturnValue(locked);
	}

	/**
	 * While the machine is processing continuously it reuses the previous
	 * recipe without re-selecting; a filter change must break that so the new
	 * filter applies on the next selection.
	 */
	@Inject(method = "continueWithPreviousRecipe", at = @At("HEAD"), cancellable = true)
	private void createcleargoal$breakContinuityOnFilterChange(CallbackInfoReturnable<Boolean> cir) {
		if (createcleargoal$filterChanged) {
			createcleargoal$filterChanged = false;
			cir.setReturnValue(false);
		}
	}
}
