package net.Tetrachlorosilane.createcleargoal.mixin;

import java.util.List;
import java.util.Optional;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.content.kinetics.mixer.MechanicalMixerBlockEntity;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;

import net.Tetrachlorosilane.createcleargoal.content.recipefilter.RecipeFilterHelper;

import net.minecraft.world.item.crafting.Recipe;

/**
 * Hooks {@link MechanicalMixerBlockEntity#getMatchingRecipes()} after its
 * override has appended potion-mixing recipes, so the recipe filter applies to
 * the final candidate list. The basin mixin deliberately skips mixers: locking
 * inside {@code super.getMatchingRecipes()} would return a list the override
 * then mutates (crash), and the appended potion recipes would bypass the
 * filter's BLOCK / ALLOW_ONLY / LOCK rules.
 */
@Mixin(MechanicalMixerBlockEntity.class)
public abstract class MechanicalMixerBlockEntityMixin {

	@Inject(method = "getMatchingRecipes", at = @At("RETURN"), cancellable = true)
	private void createcleargoal$lockRecipes(CallbackInfoReturnable<List<Recipe<?>>> cir) {
		// getBasin() is protected and declared in BasinOperatingBlockEntity, so it
		// cannot be @Shadowed here (shadows only resolve against members declared
		// in the target class); use the parent-targeted accessor instead.
		Optional<BasinBlockEntity> basin =
			((BasinOperatingBlockEntityAccessor) (Object) this).createcleargoal$getBasin();
		if (basin.isEmpty())
			return;
		List<Recipe<?>> locked = RecipeFilterHelper.tryLockBasin(basin.get(), cir.getReturnValue());
		if (locked != null)
			cir.setReturnValue(locked);
	}
}
