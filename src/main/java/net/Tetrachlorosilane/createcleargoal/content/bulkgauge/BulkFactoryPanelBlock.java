package net.Tetrachlorosilane.createcleargoal.content.bulkgauge;

import org.jetbrains.annotations.NotNull;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockItem;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Bulk factory gauge block. Functionally identical to Create's
 * {@link FactoryPanelBlock}, except it hosts
 * {@link BulkFactoryPanelBlockEntity} / {@link BulkFactoryPanelBehaviour} and
 * drops its own item. The methods below re-implement the handful of places
 * where Create hardcodes {@code AllBlocks.FACTORY_GAUGE} so the bulk variant
 * behaves correctly with its own block and item.
 */
public class BulkFactoryPanelBlock extends FactoryPanelBlock {

	public static final MapCodec<BulkFactoryPanelBlock> CODEC = simpleCodec(BulkFactoryPanelBlock::new);

	public BulkFactoryPanelBlock(Properties p_53182_) {
		super(p_53182_);
	}

	// Note: getBlockEntityClass() is intentionally inherited - IBE is bound to
	// FactoryPanelBlockEntity and Class<T> is invariant; getBlockEntityType()
	// below provides the actual bulk block entity type.

	@Override
	public BlockEntityType<? extends FactoryPanelBlockEntity> getBlockEntityType() {
		return AllBulkGauge.BULK_FACTORY_PANEL_BE.get();
	}

	@Override
	protected @NotNull MapCodec<? extends net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock> codec() {
		return CODEC;
	}

	@Override
	public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
		Level world = context.getLevel();
		BlockPos pos = context.getClickedPos();
		Player player = context.getPlayer();
		FactoryPanelBlock.PanelSlot slot = getTargetedSlot(pos, state, context.getClickLocation());

		if (!(world instanceof ServerLevel))
			return InteractionResult.SUCCESS;

		return onBlockEntityUse(world, pos, be -> {
			com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour behaviour = be.panels.get(slot);
			if (behaviour == null || !behaviour.isActive())
				return InteractionResult.SUCCESS;

			BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(world, pos, world.getBlockState(pos), player);
			NeoForge.EVENT_BUS.post(event);
			if (event.isCanceled())
				return InteractionResult.SUCCESS;

			if (!be.removePanel(slot))
				return InteractionResult.SUCCESS;

			if (!player.isCreative())
				player.getInventory()
					.placeItemBackInInventory(AllBulkGauge.BULK_FACTORY_PANEL_ITEM.toStack());

			com.simibubi.create.content.equipment.wrench.IWrenchable.playRemoveSound(world, pos);
			if (be.activePanels() == 0)
				world.destroyBlock(pos, false);

			return InteractionResult.SUCCESS;
		});
	}

	@Override
	protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
		Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hitResult) {
		if (player == null)
			return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
		if (level.isClientSide)
			return ItemInteractionResult.SUCCESS;
		if (!(stack.getItem() instanceof FactoryPanelBlockItem))
			return ItemInteractionResult.SUCCESS;
		Vec3 location = hitResult.getLocation();
		if (location == null)
			return ItemInteractionResult.SUCCESS;

		if (!com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockItem.isTuned(stack)) {
			com.simibubi.create.AllSoundEvents.DENY.playOnServer(level, pos);
			player.displayClientMessage(com.simibubi.create.foundation.utility.CreateLang
				.translate("factory_panel.tune_before_placing")
				.component(), true);
			return ItemInteractionResult.FAIL;
		}

		FactoryPanelBlock.PanelSlot newSlot = getTargetedSlot(pos, state, location);
		withBlockEntityDo(level, pos, fpbe -> {
			if (!fpbe.addPanel(newSlot,
				com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBlockItem
					.networkFromStack(com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockItem
						.fixCtrlCopiedStack(stack))))
				return;
			player.displayClientMessage(com.simibubi.create.foundation.utility.CreateLang
				.translateDirect("logistically_linked.connected"), true);
			level.playSound(null, pos, soundType.getPlaceSound(), SoundSource.BLOCKS);
			if (player.isCreative())
				return;
			stack.shrink(1);
			if (stack.isEmpty())
				player.setItemInHand(hand, ItemStack.EMPTY);
		});
		return ItemInteractionResult.SUCCESS;
	}

	@Override
	public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest,
		net.minecraft.world.level.material.FluidState fluid) {
		if (tryDestroySubPanelFirst(state, level, pos, player))
			return false;
		return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
	}

	private boolean tryDestroySubPanelFirst(BlockState state, Level level, BlockPos pos, Player player) {
		double range = player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.BLOCK_INTERACTION_RANGE) + 1;
		HitResult hitResult = player.pick(range, 1, false);
		Vec3 location = hitResult.getLocation();
		FactoryPanelBlock.PanelSlot destroyedSlot = getTargetedSlot(pos, state, location);
		return InteractionResult.SUCCESS == onBlockEntityUse(level, pos, fpbe -> {
			if (fpbe.activePanels() < 2)
				return InteractionResult.FAIL;
			if (!fpbe.removePanel(destroyedSlot))
				return InteractionResult.FAIL;
			if (!player.isCreative())
				popResource(level, pos, AllBulkGauge.BULK_FACTORY_PANEL_ITEM.toStack());
			return InteractionResult.SUCCESS;
		});
	}

	@Override
	public boolean canBeReplaced(BlockState pState, BlockPlaceContext pUseContext) {
		if (!(pUseContext.getItemInHand()
			.getItem() instanceof FactoryPanelBlockItem))
			return false;
		Vec3 location = pUseContext.getClickLocation();

		BlockPos pos = pUseContext.getClickedPos();
		FactoryPanelBlock.PanelSlot slot = getTargetedSlot(pos, pState, location);
		FactoryPanelBlockEntity blockEntity = getBlockEntity(pUseContext.getLevel(), pos);

		if (blockEntity == null)
			return false;
		return !blockEntity.panels.get(slot)
			.isActive();
	}
}
