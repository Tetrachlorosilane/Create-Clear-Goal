package net.Tetrachlorosilane.createcleargoal.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsClient;

import net.Tetrachlorosilane.createcleargoal.content.bulkgauge.BulkFactoryPanelBehaviour;
import net.Tetrachlorosilane.createcleargoal.content.bulkgauge.BulkValueSettingsScreen;

import net.createmod.catnip.gui.ScreenOpener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;

/**
 * When the held-right-click value-settings interaction targets a bulk gauge
 * panel, open the 4-row {@link BulkValueSettingsScreen} instead of Create's
 * vanilla {@code ValueSettingsScreen}. The vanilla screen's geometry is linear
 * with a single shared max value, which cannot represent the four row ranges.
 */
@Mixin(ValueSettingsClient.class)
public abstract class ValueSettingsClientMixin {

	@Shadow
	public BlockPos interactHeldPos;
	@Shadow
	public BehaviourType<?> interactHeldBehaviour;

	@Redirect(method = "tick", at = @At(value = "INVOKE",
		target = "Lnet/createmod/catnip/gui/ScreenOpener;open(Lnet/minecraft/client/gui/screens/Screen;)V"))
	private void createcleargoal$openBulkSliderOrVanilla(Screen screen) {
		if (BulkFactoryPanelBehaviour.isBulkType(interactHeldBehaviour)) {
			Minecraft mc = Minecraft.getInstance();
			BlockEntityBehaviour behaviour = BlockEntityBehaviour.get(mc.level, interactHeldPos, interactHeldBehaviour);
			if (behaviour instanceof BulkFactoryPanelBehaviour bulk) {
				ScreenOpener.open(new BulkValueSettingsScreen(interactHeldPos, bulk.createBoard(null, null),
					bulk.getValueSettings(), bulk::newSettingHovered, bulk.netId()));
				return;
			}
		}
		ScreenOpener.open(screen);
	}
}
