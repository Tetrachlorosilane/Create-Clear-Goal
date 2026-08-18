package net.Tetrachlorosilane.createcleargoal.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;

import com.simibubi.create.content.logistics.packager.PackagerBlock;

import net.Tetrachlorosilane.createcleargoal.content.productreturn.ProductReturnStationBlock;
import net.Tetrachlorosilane.createcleargoal.content.productreturn.ProductReturnStationBlockItem;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Allows the Product Return Station item to be placed on a Packager. Create's
 * {@link PackagerBlock#useItemOn} only passes through its own known items
 * (wrench, factory gauge, stock link, frogport); our item must also be allowed
 * to reach the normal block-item placement path.
 */
@Mixin(PackagerBlock.class)
public abstract class PackagerBlockMixin {

	@Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
	private void createcleargoal$allowProductReturnStationPlacement(ItemStack stack, BlockState state, Level level,
		BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult,
		CallbackInfoReturnable<ItemInteractionResult> cir) {
		if (stack.getItem() instanceof ProductReturnStationBlockItem)
			cir.setReturnValue(ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION);
	}

	@Inject(method = "createBlockStateDefinition", at = @At("RETURN"))
	private void createcleargoal$addReturnProperty(Builder<Block, BlockState> builder, CallbackInfo ci) {
		builder.add(ProductReturnStationBlock.RETURN);
	}

	/**
	 * The extra {@code return} property must default to {@code false}. Without
	 * this, a newly placed Packager (which has no Product Return Station yet)
	 * would briefly use the yellow return model until its block entity rechecks
	 * and clears the property.
	 */
	@Inject(method = "<init>", at = @At("RETURN"))
	private void createcleargoal$defaultReturnFalse(CallbackInfo ci) {
		BlockState state = ((Block) (Object) this).defaultBlockState();
		Property<?> property = state.getBlock()
			.getStateDefinition()
			.getProperty("return");
		if (property instanceof BooleanProperty returnProperty && state.getValue(returnProperty))
			((BlockAccessor) (Object) this).createcleargoal$registerDefaultState(
				state.setValue(returnProperty, false));
	}
}
