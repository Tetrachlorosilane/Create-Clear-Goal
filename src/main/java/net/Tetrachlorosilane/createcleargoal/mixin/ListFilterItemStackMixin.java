package net.Tetrachlorosilane.createcleargoal.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.simibubi.create.content.logistics.filter.FilterItemStack;

import net.Tetrachlorosilane.createcleargoal.content.recipefilter.RecipeFilterItem;

import net.minecraft.world.item.ItemStack;

/**
 * Keeps nested recipe filters out of {@code ListFilterItemStack.containedItems}.
 * <p>
 * A {@code RecipeFilterItemStack} reports {@code test()} as always passing, so
 * leaving it in the list filter's item matcher would make an allow-list match
 * every item (and a deny-list match none), silently breaking the list filter's
 * own allow/deny rules. The recipe filter is instead discovered recursively by
 * {@code RecipeFilterHelper} and applied at the recipe-selection layer, so the
 * two filters keep their independent semantics when nested.
 */
@Mixin(FilterItemStack.ListFilterItemStack.class)
public abstract class ListFilterItemStackMixin {

	@Shadow
	public List<FilterItemStack> containedItems;

	@Inject(method = "<init>(Lnet/minecraft/world/item/ItemStack;)V", at = @At("RETURN"))
	private void createcleargoal$stripNestedRecipeFilters(ItemStack filter, CallbackInfo ci) {
		containedItems.removeIf(item -> item.item().getItem() instanceof RecipeFilterItem);
	}
}
