package net.Tetrachlorosilane.createcleargoal.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.content.kinetics.saw.SawBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;

import net.Tetrachlorosilane.createcleargoal.content.recipefilter.RecipeFilterHelper;

import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * Hooks {@link SawBlockEntity#getRecipes()} so that a recipe filter in the
 * saw's filter slot can lock the saw onto a recorded recipe.
 */
@Mixin(SawBlockEntity.class)
public abstract class SawBlockEntityMixin {

	@Shadow
	private FilteringBehaviour filtering;

	@Inject(method = "getRecipes", at = @At("RETURN"), cancellable = true)
	private void createcleargoal$lockRecipes(CallbackInfoReturnable<List<RecipeHolder<? extends Recipe<?>>>> cir) {
		SawBlockEntity self = (SawBlockEntity) (Object) this;
		List<RecipeHolder<? extends Recipe<?>>> locked =
			RecipeFilterHelper.tryLockSaw(self, filtering, cir.getReturnValue());
		if (locked != null)
			cir.setReturnValue(locked);
	}
}
