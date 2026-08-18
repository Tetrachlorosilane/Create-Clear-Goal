package net.Tetrachlorosilane.createcleargoal.content.bulkgauge;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockItem;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelModel;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelRenderer;

import net.Tetrachlorosilane.createcleargoal.Createcleargoal;

import net.createmod.catnip.registry.RegisteredObjectsHelper;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registration for the bulk factory gauge: block, block entity type and item,
 * plus client-side wiring (the panel model and the cutout render layer). The
 * block mirrors Create's {@code factory_gauge} properties; the item is Create's
 * {@link FactoryPanelBlockItem} so tuning/frequency behaviour is identical.
 */
public class AllBulkGauge {

	public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Createcleargoal.MODID);
	public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
		DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Createcleargoal.MODID);
	public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Createcleargoal.MODID);

	public static final DeferredBlock<BulkFactoryPanelBlock> BULK_FACTORY_PANEL =
		BLOCKS.register("bulk_factory_gauge", () -> new BulkFactoryPanelBlock(BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_ORANGE)
			.sound(SoundType.COPPER)
			.strength(5.0f, 6.0f)
			.requiresCorrectToolForDrops()
			.noOcclusion()
			.forceSolidOn()));

	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BulkFactoryPanelBlockEntity>> BULK_FACTORY_PANEL_BE =
		BLOCK_ENTITY_TYPES.register("bulk_factory_panel", AllBulkGauge::createPanelType);

	/**
	 * The block entity type factory, separated from the field initialiser so the
	 * registration lambda can reference {@link #BULK_FACTORY_PANEL_BE} without
	 * tripping javac's "self-reference in initializer" check (the reference is
	 * only evaluated lazily, when a block entity is actually created).
	 */
	private static BlockEntityType<BulkFactoryPanelBlockEntity> createPanelType() {
		return BlockEntityType.Builder
			.of((pos, state) -> new BulkFactoryPanelBlockEntity(BULK_FACTORY_PANEL_BE.get(), pos, state),
				BULK_FACTORY_PANEL.get())
			.build(null);
	}

	public static final DeferredItem<FactoryPanelBlockItem> BULK_FACTORY_PANEL_ITEM =
		ITEMS.register("bulk_factory_gauge", () -> new FactoryPanelBlockItem(BULK_FACTORY_PANEL.get(),
			new Item.Properties()));

	public static void register() {
		BLOCKS.register(Createcleargoal.MOD_EVENT_BUS);
		BLOCK_ENTITY_TYPES.register(Createcleargoal.MOD_EVENT_BUS);
		ITEMS.register(Createcleargoal.MOD_EVENT_BUS);
	}

	@EventBusSubscriber(modid = Createcleargoal.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
	public static class ClientEvents {

		@SubscribeEvent
		public static void onModelBake(ModelEvent.ModifyBakingResult event) {
			// Wrap every blockstate model of the bulk gauge in Create's
			// FactoryPanelModel (dynamic panel rendering). The item model
			// (variant "inventory") is intentionally left untouched - it keeps
			// its plain parent, since FactoryPanelModel renders no quads without
			// block model data (the item icon would go invisible).
			// Follows Create's own ModelSwapper pattern.
			java.util.Map<ModelResourceLocation, BakedModel> modelRegistry = event.getModels();
			ResourceLocation blockRl = RegisteredObjectsHelper.getKeyOrThrow(BULK_FACTORY_PANEL.get());
			BULK_FACTORY_PANEL.get()
				.getStateDefinition()
				.getPossibleStates()
				.forEach(state -> {
					ModelResourceLocation location = BlockModelShaper.stateToModelLocation(blockRl, state);
					modelRegistry.put(location, new FactoryPanelModel(modelRegistry.get(location)));
				});
		}

		@SubscribeEvent
		public static void onClientSetup(FMLClientSetupEvent event) {
			event.enqueueWork(() -> ItemBlockRenderTypes.setRenderLayer(BULK_FACTORY_PANEL.get(),
				RenderType.cutoutMipped()));
		}

		@SubscribeEvent
		public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
			// Create's FactoryPanelRenderer (extends SmartBlockEntityRenderer):
			// renders the status bulb, the connection paths, and - via
			// SmartBlockEntityRenderer -> FilteringRenderer - the recorded item
			// icon on the panel face.
			event.registerBlockEntityRenderer(BULK_FACTORY_PANEL_BE.get(), FactoryPanelRenderer::new);
		}
	}
}
