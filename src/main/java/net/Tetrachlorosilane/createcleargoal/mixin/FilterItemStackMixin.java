package net.Tetrachlorosilane.createcleargoal.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.content.logistics.filter.FilterItemStack;

import net.Tetrachlorosilane.createcleargoal.content.recipefilter.RecipeFilterItem;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

/**
 * Make Create's {@link FilterItemStack#of(ItemStack)} always use the recipe
 * filter's own wrapper, even when the item has no data components yet.
 * <p>
 * Create's default implementation only calls {@code makeStackWrapper()} when
 * {@code isComponentsPatchEmpty()} is false. A freshly obtained, never-edited
 * RecipeFilter has an empty component patch, so it would fall back to a plain
 * {@link FilterItemStack} and be tested as an ordinary item filter (the
 * recorded-recipe logic in {@code RecipeFilterHelper} is bypassed, and the
 * machine can reject every candidate). This mixin short-circuits that path.
 */
@Mixin(FilterItemStack.class)
public abstract class FilterItemStackMixin {

	@Inject(
		method = "of(Lnet/minecraft/world/item/ItemStack;)Lcom/simibubi/create/content/logistics/filter/FilterItemStack;",
		at = @At("HEAD"),
		cancellable = true
	)
	private static void createcleargoal$alwaysWrapRecipeFilter(ItemStack filter,
		CallbackInfoReturnable<FilterItemStack> cir) {
		if (filter.getItem() instanceof RecipeFilterItem item) {
			// Keep the same component trimming Create does for ordinary filters.
			filter.remove(DataComponents.ENCHANTMENTS);
			filter.remove(DataComponents.ATTRIBUTE_MODIFIERS);
			cir.setReturnValue(item.makeStackWrapper(filter));
		}
	}
}
