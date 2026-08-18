package net.Tetrachlorosilane.createcleargoal.content.productreturn;

import org.jetbrains.annotations.NotNull;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.AllShapes;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.logistics.packager.PackagerBlock;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.block.ProperWaterloggedBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Product Return Station: a face-attached block that is placed on a Packager and
 * makes it enter "return phase" (rendered as the packager's linked/yellow state).
 * <p>
 * The model/art is currently reused from Create's Stock Link; custom art is still
 * needed.
 */
public class ProductReturnStationBlock extends FaceAttachedHorizontalDirectionalBlock
	implements IBE<ProductReturnStationBlockEntity>, ProperWaterloggedBlock, IWrenchable {

	public static final MapCodec<ProductReturnStationBlock> CODEC = simpleCodec(ProductReturnStationBlock::new);

	public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

	/**
	 * Added to Create's Packager block by mixin to distinguish the Product
	 * Return Station's "return phase" from a normal Stock Link linked state.
	 */
	public static final BooleanProperty RETURN = BooleanProperty.create("return");

	public ProductReturnStationBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(POWERED, false)
			.setValue(WATERLOGGED, false));
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockPos pos = context.getClickedPos();
		BlockState placed = super.getStateForPlacement(context);
		if (placed == null)
			return null;
		if (placed.getValue(FACE) == AttachFace.CEILING)
			placed = placed.setValue(FACING, placed.getValue(FACING)
				.getOpposite());
		return withWater(placed.setValue(POWERED, false), context);
	}

	public static Direction getConnectedDirection(BlockState state) {
		return FaceAttachedHorizontalDirectionalBlock.getConnectedDirection(state);
	}

	@Override
	public boolean canSurvive(BlockState pState, LevelReader pLevel, BlockPos pPos) {
		BlockPos attached = pPos.relative(getConnectedDirection(pState)
			.getOpposite());
		// The block entity may briefly be absent while the packager's block state is
		// being updated; the block itself is enough to keep the station attached.
		return pLevel.getBlockEntity(attached) instanceof PackagerBlockEntity
			|| pLevel.getBlockState(attached)
				.getBlock() instanceof PackagerBlock;
	}

	@Override
	public FluidState getFluidState(BlockState pState) {
		return fluidState(pState);
	}

	@Override
	public BlockState updateShape(BlockState pState, Direction pDirection, BlockState pNeighborState,
		LevelAccessor pLevel, BlockPos pPos, BlockPos pNeighborPos) {
		updateWater(pLevel, pState, pPos);
		return pState;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
		BlockHitResult hitResult) {
		if (!level.isClientSide && player instanceof net.minecraft.server.level.ServerPlayer sp) {
			if (level.getBlockEntity(pos) instanceof ProductReturnStationBlockEntity be) {
				net.createmod.catnip.platform.CatnipServices.NETWORK.sendToClient(sp,
					new ProductReturnStationOpenScreenPacket(pos, be.inputAddress, be.outputAddress,
						be.promiseClearingInterval, be.lastReportedPromises));
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
		return AllShapes.STOCK_LINK.get(getConnectedDirection(pState));
	}

	@Override
	protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder.add(POWERED, WATERLOGGED, FACE, FACING));
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
		return false;
	}

	@Override
	public Class<ProductReturnStationBlockEntity> getBlockEntityClass() {
		return ProductReturnStationBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends ProductReturnStationBlockEntity> getBlockEntityType() {
		return AllProductReturnStation.PRODUCT_RETURN_STATION_BE.get();
	}

	@Override
	public void onRemove(BlockState pState, Level pLevel, BlockPos pPos, BlockState pNewState, boolean pMovedByPiston) {
		IBE.onRemove(pState, pLevel, pPos, pNewState);
		if (!pLevel.isClientSide) {
			// Let the attached packager re-evaluate its linked/return state.
			BlockPos attached = pPos.relative(getConnectedDirection(pState)
				.getOpposite());
			if (pLevel.getBlockEntity(attached) instanceof PackagerBlockEntity packager)
				packager.recheckIfLinksPresent();
		}
	}

	@Override
	protected @NotNull MapCodec<? extends FaceAttachedHorizontalDirectionalBlock> codec() {
		return CODEC;
	}
}
