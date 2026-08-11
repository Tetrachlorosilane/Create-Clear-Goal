package net.Tetrachlorosilane.createcleargoal.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;

import net.minecraft.world.item.ItemStack;

/**
 * Hooks {@link FilteringBehaviour#setFilter(ItemStack)} (the single entry
 * point for placing/removing a filter item) so that a basin operator whose
 * filter changed can be notified: Create's {@code continueWithPreviousRecipe}
 * would otherwise keep the machine on the old recipe until the continuous
 * processing flow breaks.
 */
@Mixin(FilteringBehaviour.class)
public class FilteringBehaviourMixin {

	@Inject(method = "setFilter(Lnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"))
	private void createcleargoal$onFilterSet(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		FilteringBehaviour self = (FilteringBehaviour) (Object) this;
		if (self.blockEntity instanceof FilterChangedMarker marker)
			marker.createcleargoal$markFilterChanged();
	}
}
