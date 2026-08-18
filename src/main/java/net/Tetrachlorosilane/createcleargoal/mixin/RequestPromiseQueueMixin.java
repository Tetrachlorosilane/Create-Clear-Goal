package net.Tetrachlorosilane.createcleargoal.mixin;

import java.util.UUID;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.simibubi.create.content.logistics.packagerLink.RequestPromiseQueue;

import net.Tetrachlorosilane.createcleargoal.content.productreturn.ProductReturnStationManager;

import net.minecraft.world.item.ItemStack;

/**
 * Bridges Create's original promise queue back into the Product Return Station
 * independent queues when promises are manually cancelled.
 * <p>
 * Promise completion is intentionally NOT bridged here: a Product Return
 * Station's promise is only considered fulfilled after its attached packager
 * actually packages the items, not when items merely enter the system.
 */
@Mixin(RequestPromiseQueue.class)
public abstract class RequestPromiseQueueMixin {

	@Inject(method = "forceClear", at = @At("RETURN"))
	private void createcleargoal$onPromiseCancelled(ItemStack stack, CallbackInfo ci) {
		RequestPromiseQueue self = (RequestPromiseQueue) (Object) this;
		UUID network = ProductReturnStationManager.getNetwork(self);
		if (network != null)
			ProductReturnStationManager.onPromiseCancelled(network, stack);
	}
}
