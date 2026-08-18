package net.Tetrachlorosilane.createcleargoal.content.bulkgauge;

import java.util.EnumMap;
import java.util.List;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.foundation.advancement.AdvancementBehaviour;
import com.simibubi.create.foundation.advancement.AllAdvancements;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Block entity for the bulk factory gauge. Mirrors Create's
 * {@link FactoryPanelBlockEntity} but builds {@link BulkFactoryPanelBehaviour}
 * panels and recognises its own block instead of Create's hardcoded
 * {@code AllBlocks.FACTORY_GAUGE} checks.
 */
public class BulkFactoryPanelBlockEntity extends FactoryPanelBlockEntity {

	public BulkFactoryPanelBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		// Note: no super call - it would create vanilla FactoryPanelBehaviours and
		// overwrite the panels map. The Create advancement is intentionally not
		// attached (it belongs to the vanilla factory gauge).
		panels = new EnumMap<>(FactoryPanelBlock.PanelSlot.class);
		redraw = true;
		for (FactoryPanelBlock.PanelSlot slot : FactoryPanelBlock.PanelSlot.values()) {
			BulkFactoryPanelBehaviour e = new BulkFactoryPanelBehaviour(this, slot);
			panels.put(slot, e);
			behaviours.add(e);
		}

		// Create's FactoryPanelBehaviour.tickRequests() calls
		// panelBE.advancements.awardPlayer(...) unconditionally, so the bulk gauge
		// must attach the same AdvancementBehaviour to avoid an NPE.
		behaviours.add(advancements = new AdvancementBehaviour(this, AllAdvancements.FACTORY_GAUGE));
	}

	@Override
	public void lazyTick() {
		super.lazyTick();
		if (level.isClientSide())
			return;

		if (getBlockState().getBlock() instanceof FactoryPanelBlock) {
			boolean shouldBeRestocker = AllBlocks.PACKAGER
				.has(level.getBlockState(worldPosition.relative(FactoryPanelBlock.connectedDirection(getBlockState())
					.getOpposite())));
			if (restocker == shouldBeRestocker)
				return;
			restocker = shouldBeRestocker;
			redraw = true;
			sendData();
		}
	}

	@Override
	public PackagerBlockEntity getRestockedPackager() {
		BlockState state = getBlockState();
		if (!restocker || !(state.getBlock() instanceof FactoryPanelBlock))
			return null;
		BlockPos packagerPos = worldPosition.relative(FactoryPanelBlock.connectedDirection(state)
			.getOpposite());
		if (!level.isLoaded(packagerPos))
			return null;
		net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(packagerPos);
		if (be == null || !(be instanceof PackagerBlockEntity pbe))
			return null;
		if (pbe instanceof com.simibubi.create.content.logistics.packager.repackager.RepackagerBlockEntity)
			return null;
		return pbe;
	}

	@Override
	public void destroy() {
		// Deliberately NOT calling FactoryPanelBlockEntity.destroy(): that method
		// drops the vanilla factory gauge (hardcoded AllBlocks.FACTORY_GAUGE).
		// Run the behaviour cleanup directly (SmartBlockEntity.destroy body) and
		// drop our own item instead.
		forEachBehaviour(BlockEntityBehaviour::destroy);
		int panelCount = activePanels();
		if (panelCount > 1)
			Block.popResource(level, worldPosition, AllBulkGauge.BULK_FACTORY_PANEL.toStack(panelCount - 1));
	}
}
