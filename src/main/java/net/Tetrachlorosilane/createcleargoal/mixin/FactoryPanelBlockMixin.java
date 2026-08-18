package net.Tetrachlorosilane.createcleargoal.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockItem;
import com.tterrag.registrate.util.entry.BlockEntry;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Lets the vanilla Create factory gauge accept the bulk gauge item as well, and
 * lets both gauge variants treat each other as compatible hosts when adding or
 * relocating panels.
 */
@Mixin(FactoryPanelBlock.class)
public abstract class FactoryPanelBlockMixin {

	@Redirect(method = "useItemOn",
		at = @At(value = "INVOKE",
			target = "Lcom/tterrag/registrate/util/entry/BlockEntry;isIn(Lnet/minecraft/world/item/ItemStack;)Z"))
	private boolean createcleargoal$anyFactoryPanelItemForUse(BlockEntry<?> entry, ItemStack stack) {
		return entry.isIn(stack) || stack.getItem() instanceof FactoryPanelBlockItem;
	}

	@Redirect(method = "canBeReplaced",
		at = @At(value = "INVOKE",
			target = "Lcom/tterrag/registrate/util/entry/BlockEntry;isIn(Lnet/minecraft/world/item/ItemStack;)Z"))
	private boolean createcleargoal$anyFactoryPanelItemForReplace(BlockEntry<?> entry, ItemStack stack) {
		return entry.isIn(stack) || stack.getItem() instanceof FactoryPanelBlockItem;
	}

	@Redirect(method = "getStateForPlacement",
		at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/world/level/block/Block;)Z"))
	private boolean createcleargoal$isCompatibleGauge(BlockState state, Block block) {
		return state.getBlock() instanceof FactoryPanelBlock;
	}
}
