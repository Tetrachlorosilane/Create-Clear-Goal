package net.Tetrachlorosilane.createcleargoal;

import java.util.Optional;

import net.Tetrachlorosilane.createcleargoal.content.bulkgauge.AllBulkGauge;
import net.Tetrachlorosilane.createcleargoal.content.productreturn.AllProductReturnStation;
import net.Tetrachlorosilane.createcleargoal.content.recipefilter.RecipeFilterItem;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(Createcleargoal.MODID)
public class Createcleargoal {
	public static final String MODID = "createcleargoal";

	/** The mod event bus, kept for static registrars that need it at construction time. */
	public static IEventBus MOD_EVENT_BUS;

	public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
	public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
		DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

	// The recipe filter item: records whole recipes so processing machines can lock onto them
	public static final DeferredItem<RecipeFilterItem> RECIPE_FILTER =
		ITEMS.register("recipe_filter", () -> new RecipeFilterItem(new Item.Properties().stacksTo(1)));

	static {
		CREATIVE_MODE_TABS.register("main", () -> CreativeModeTab.builder()
			.title(Component.translatable("itemGroup.createcleargoal"))
			.icon(() -> RECIPE_FILTER.get().getDefaultInstance())
			.displayItems((parameters, output) -> {
				output.accept(RECIPE_FILTER.get());
				output.accept(AllBulkGauge.BULK_FACTORY_PANEL_ITEM.get());
				output.accept(AllProductReturnStation.PRODUCT_RETURN_STATION_ITEM.get());
			})
			.build());
	}

	public Createcleargoal(IEventBus modEventBus) {
		MOD_EVENT_BUS = modEventBus;
		modEventBus.addListener(this::commonSetup);

		ITEMS.register(modEventBus);
		CREATIVE_MODE_TABS.register(modEventBus);
		AllDataComponents.DATA_COMPONENT_TYPES.register(modEventBus);
		AllMenuTypes.MENUS.register(modEventBus);
		AllBulkGauge.register();
		AllProductReturnStation.register();
	}

	private void commonSetup(final FMLCommonSetupEvent event) {
		ModPackets.register();
	}

	@EventBusSubscriber(modid = MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
	public static class ClientModEvents {
		@SubscribeEvent
		public static void registerScreens(RegisterMenuScreensEvent event) {
			event.register(AllMenuTypes.RECIPE_FILTER.get(),
				net.Tetrachlorosilane.createcleargoal.client.RecipeFilterScreen::new);
		}

		@SubscribeEvent
		public static void addPackFinders(AddPackFindersEvent event) {
			if (event.getPackType() != PackType.CLIENT_RESOURCES)
				return;
			var modFileInfo = ModList.get().getModFileById(Createcleargoal.MODID);
			if (modFileInfo == null)
				return;
			var modFile = modFileInfo.getFile();
			event.addRepositorySource(consumer -> {
				PackLocationInfo locationInfo = new PackLocationInfo(
					net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Createcleargoal.MODID,
						"createcleargoal_return")
						.toString(),
					Component.literal("Create-ClearGoal Return State"), PackSource.BUILT_IN, Optional.empty());
				PathPackResources.PathResourcesSupplier resourcesSupplier =
					new PathPackResources.PathResourcesSupplier(modFile.findResource("resourcepacks/createcleargoal_return"));
				PackSelectionConfig selectionConfig = new PackSelectionConfig(true, Pack.Position.TOP, true);
				Pack pack = Pack.readMetaAndCreate(locationInfo, resourcesSupplier, PackType.CLIENT_RESOURCES,
					selectionConfig);
				if (pack != null)
					consumer.accept(pack);
			});
		}
	}
}
