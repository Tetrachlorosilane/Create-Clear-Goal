package net.Tetrachlorosilane.createcleargoal.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.tterrag.registrate.util.entry.BlockEntry;

import net.Tetrachlorosilane.createcleargoal.content.productreturn.AllProductReturnStation;
import net.Tetrachlorosilane.createcleargoal.content.productreturn.ProductReturnStationBlock;
import net.Tetrachlorosilane.createcleargoal.content.productreturn.ProductReturnStationBlockEntity;

import net.createmod.catnip.data.Iterate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Lets {@link PackagerBlockEntity#submitNewArrivals} recognise the bulk factory
 * gauge as a promise source. Create's packager only checks the vanilla
 * {@code AllBlocks.FACTORY_GAUGE} when collecting {@code restockerPromises}, so
 * promises made by a bulk gauge would never be reduced by
 * {@code RequestPromiseQueue.itemEnteredSystem} when packages arrive. That made
 * the "promised" count accumulate up to the (very large) bulk demand, e.g. over
 * 200000 on the upper rows.
 */
@Mixin(PackagerBlockEntity.class)
public abstract class PackagerBlockEntityMixin {

	@Redirect(method = "submitNewArrivals",
		at = @At(value = "INVOKE",
			target = "Lcom/tterrag/registrate/util/entry/BlockEntry;has(Lnet/minecraft/world/level/block/state/BlockState;)Z"))
	private boolean createcleargoal$anyFactoryGauge(BlockEntry<?> entry, BlockState state) {
		// Only broaden the vanilla factory-gauge check; the stock-link check in
		// the same method must keep its original behaviour.
		if (entry == AllBlocks.FACTORY_GAUGE)
			return state.getBlock() instanceof FactoryPanelBlock;
		return entry.has(state);
	}

	@Shadow
	private BlockPos getLinkPos() {
		return null;
	}

	/**
	 * Only the LINKED-state check in {@code recheckIfLinksPresent} should treat a
	 * Product Return Station as a link (to show the yellow return phase). The
	 * real {@code getLinkPos()} must stay vanilla so the packager is NOT treated
	 * as a network member; otherwise promises would be cleared on arrival before
	 * the return station gets a chance to package them.
	 */
	@Redirect(method = "recheckIfLinksPresent",
		at = @At(value = "INVOKE",
			target = "Lcom/simibubi/create/content/logistics/packager/PackagerBlockEntity;getLinkPos()Lnet/minecraft/core/BlockPos;"))
	private BlockPos createcleargoal$linkPosForLinkedState(PackagerBlockEntity self) {
		BlockPos vanilla = getLinkPos();
		if (vanilla != null)
			return vanilla;
		for (Direction d : Iterate.directions) {
			BlockPos adjacent = self.getBlockPos()
				.relative(d);
			BlockState adjacentState = self.getLevel()
				.getBlockState(adjacent);
			if (!AllProductReturnStation.isProductReturnStation(adjacentState))
				continue;
			// Prefer the station's own attachment lookup; fall back to the same
			// direction check Create uses for Stock Links so this also works while
			// the station's block entity is still initialising.
			if (ProductReturnStationBlock.getConnectedDirection(adjacentState) == d)
				return adjacent;
			if (self.getLevel()
				.getBlockEntity(adjacent) instanceof ProductReturnStationBlockEntity be
				&& be.getPackager() == self)
				return adjacent;
		}
		return null;
	}

	/**
	 * Sets the extra {@code return} state on the Packager so its model can use
	 * the yellow return textures instead of the green Stock Link linked texture.
	 */
	@Inject(method = "recheckIfLinksPresent", at = @At("RETURN"))
	private void createcleargoal$updateReturnState(CallbackInfo ci) {
		PackagerBlockEntity self = (PackagerBlockEntity) (Object) this;
		if (self.getLevel() == null || self.getLevel().isClientSide)
			return;
		BlockState state = self.getBlockState();
		if (!state.hasProperty(ProductReturnStationBlock.RETURN))
			return;
		boolean hasReturn = false;
		for (Direction d : Iterate.directions) {
			BlockPos adjacent = self.getBlockPos()
				.relative(d);
			BlockState adjacentState = self.getLevel()
				.getBlockState(adjacent);
			if (!AllProductReturnStation.isProductReturnStation(adjacentState))
				continue;
			if (ProductReturnStationBlock.getConnectedDirection(adjacentState) == d) {
				hasReturn = true;
				break;
			}
			if (self.getLevel()
				.getBlockEntity(adjacent) instanceof ProductReturnStationBlockEntity be
				&& be.getPackager() == self) {
				hasReturn = true;
				break;
			}
		}
		if (state.getValue(ProductReturnStationBlock.RETURN) != hasReturn)
			self.getLevel()
				.setBlockAndUpdate(self.getBlockPos(), state.setValue(ProductReturnStationBlock.RETURN, hasReturn));
	}
}
