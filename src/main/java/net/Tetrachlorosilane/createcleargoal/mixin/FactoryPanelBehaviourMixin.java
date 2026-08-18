package net.Tetrachlorosilane.createcleargoal.mixin;

import java.util.UUID;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import com.simibubi.create.content.logistics.packagerLink.RequestPromise;
import com.simibubi.create.content.logistics.packagerLink.RequestPromiseQueue;
import com.tterrag.registrate.util.entry.BlockEntry;

import net.Tetrachlorosilane.createcleargoal.content.bulkgauge.BulkFactoryPanelBehaviour;
import net.Tetrachlorosilane.createcleargoal.content.bulkgauge.BulkFactoryPanelBlockEntity;
import net.Tetrachlorosilane.createcleargoal.content.productreturn.ProductReturnStationManager;

import net.minecraft.world.level.block.state.BlockState;

/**
 * Makes Create's {@link FactoryPanelBehaviour#moveTo} (panel relocation) work
 * for the bulk gauge. The method hardcodes the vanilla gauge block
 * ({@code AllBlocks.FACTORY_GAUGE.has(...)}) and re-creates a vanilla
 * {@link FactoryPanelBehaviour} at the old slot; both are redirected here so a
 * bulk panel relocates onto bulk gauges and keeps a bulk behaviour.
 * <p>
 * This approach avoids the need for a cross-package accessor: the private
 * {@code moveToSlot} stays inside Create and is invoked from its own method.
 */
@Mixin(FactoryPanelBehaviour.class)
public abstract class FactoryPanelBehaviourMixin {

	@Shadow
	public UUID network;

	@Shadow
	public String recipeAddress;

	@Redirect(method = "moveTo",
		at = @At(value = "INVOKE",
			target = "Lcom/tterrag/registrate/util/entry/BlockEntry;has(Lnet/minecraft/world/level/block/state/BlockState;)Z"))
	private boolean createcleargoal$anyGaugeBlock(BlockEntry<?> entry, BlockState state) {
		return state.getBlock() instanceof FactoryPanelBlock;
	}

	@Redirect(method = "moveTo",
		at = @At(value = "NEW", target = "com/simibubi/create/content/logistics/factoryBoard/FactoryPanelBehaviour"))
	private FactoryPanelBehaviour createcleargoal$bulkBehaviour(FactoryPanelBlockEntity be,
		FactoryPanelBlock.PanelSlot slot) {
		return be instanceof BulkFactoryPanelBlockEntity ? new BulkFactoryPanelBehaviour(be, slot)
			: new FactoryPanelBehaviour(be, slot);
	}

	/**
	 * When a factory gauge adds an output promise to the network queue, also
	 * record it in the Product Return Station side-table together with the
	 * address the request was sent to. This is what lets the Product Return
	 * Station filter promises by its "原料输入端地址".
	 */
	@Redirect(method = "tickRequests",
		at = @At(value = "INVOKE",
			target = "Lcom/simibubi/create/content/logistics/packagerLink/RequestPromiseQueue;add(Lcom/simibubi/create/content/logistics/packagerLink/RequestPromise;)V"))
	private void createcleargoal$recordAddressPromise(RequestPromiseQueue queue, RequestPromise promise) {
		queue.add(promise);
		ProductReturnStationManager.bindQueue(network, queue);
		ProductReturnStationManager.addPromise(network, recipeAddress, promise.promisedStack.stack,
			promise.promisedStack.count);
	}
}
