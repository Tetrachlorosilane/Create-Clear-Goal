package net.Tetrachlorosilane.createcleargoal.content.productreturn;

import net.Tetrachlorosilane.createcleargoal.Createcleargoal;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registration for the Product Return Station.
 * <p>
 * The block intentionally reuses the Stock Link model/textures for now; custom
 * art assets are still needed (see assets/createcleargoal/models/block/product_return_station*).
 */
public class AllProductReturnStation {

	public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Createcleargoal.MODID);
	public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
		DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Createcleargoal.MODID);
	public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Createcleargoal.MODID);

	public static final DeferredBlock<ProductReturnStationBlock> PRODUCT_RETURN_STATION =
		BLOCKS.register("product_return_station", () -> new ProductReturnStationBlock(BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_ORANGE)
			.sound(SoundType.COPPER)
			.strength(3.0f, 6.0f)
			.noOcclusion()
			.forceSolidOn()));

	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ProductReturnStationBlockEntity>> PRODUCT_RETURN_STATION_BE =
		BLOCK_ENTITY_TYPES.register("product_return_station", AllProductReturnStation::createBlockEntityType);

	private static BlockEntityType<ProductReturnStationBlockEntity> createBlockEntityType() {
		return BlockEntityType.Builder
			.of((pos, state) -> new ProductReturnStationBlockEntity(PRODUCT_RETURN_STATION_BE.get(), pos, state),
				PRODUCT_RETURN_STATION.get())
			.build(null);
	}

	public static final DeferredItem<ProductReturnStationBlockItem> PRODUCT_RETURN_STATION_ITEM =
		ITEMS.register("product_return_station", () -> new ProductReturnStationBlockItem(PRODUCT_RETURN_STATION.get(),
			new Item.Properties()));

	public static void register() {
		BLOCKS.register(Createcleargoal.MOD_EVENT_BUS);
		BLOCK_ENTITY_TYPES.register(Createcleargoal.MOD_EVENT_BUS);
		ITEMS.register(Createcleargoal.MOD_EVENT_BUS);
	}

	/** Convenience for mixins: matches any block that can act as a packager link/return station. */
	public static boolean isProductReturnStation(net.minecraft.world.level.block.state.BlockState state) {
		return state.getBlock() instanceof ProductReturnStationBlock;
	}
}
