package net.Tetrachlorosilane.createcleargoal.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelConnection;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelEffectPacket;

import net.Tetrachlorosilane.createcleargoal.content.bulkgauge.BulkFactoryPanelBlock;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Create's {@link FactoryPanelEffectPacket#handle} bails out unless the source
 * position holds the vanilla {@code AllBlocks.FACTORY_GAUGE}, so the panel
 * connection feedback (bulb flash / success marker) would never show for the
 * bulk gauge. When the source is a bulk panel, run the equivalent effect logic
 * instead; the vanilla block keeps Create's original path.
 */
@Mixin(FactoryPanelEffectPacket.class)
public abstract class FactoryPanelEffectPacketMixin {

	@Inject(method = "handle", at = @At("HEAD"), cancellable = true)
	private void createcleargoal$handleBulkEffect(LocalPlayer player, CallbackInfo ci) {
		FactoryPanelEffectPacket self = (FactoryPanelEffectPacket) (Object) this;
		ClientLevel level = Minecraft.getInstance().level;
		BlockState blockState = level.getBlockState(self.fromPos()
			.pos());
		if (!(blockState.getBlock() instanceof BulkFactoryPanelBlock))
			return; // vanilla block: keep Create's original handling

		FactoryPanelBehaviour panelBehaviour = FactoryPanelBehaviour.at(level, self.toPos());
		if (panelBehaviour != null) {
			panelBehaviour.bulb.setValue(1);
			FactoryPanelConnection connection = panelBehaviour.targetedBy.get(self.fromPos());
			if (connection != null)
				connection.success = self.success();
		}
		ci.cancel();
	}
}
