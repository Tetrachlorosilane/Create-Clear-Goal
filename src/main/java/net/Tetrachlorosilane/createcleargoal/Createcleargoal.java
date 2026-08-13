package net.Tetrachlorosilane.createcleargoal;

import net.Tetrachlorosilane.createcleargoal.content.recipefilter.RecipeFilterItem;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(Createcleargoal.MODID)
public class Createcleargoal {
	public static final String MODID = "createcleargoal";

	public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
	public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
		DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

	// The recipe filter item: records whole recipes so processing machines can lock onto them
	public static final DeferredItem<RecipeFilterItem> RECIPE_FILTER =
		ITEMS.register("recipe_filter", () -> new RecipeFilterItem(new Item.Properties().stacksTo(1)));

	public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CREATIVE_TAB =
		CREATIVE_MODE_TABS.register("main", () -> CreativeModeTab.builder()
			.title(Component.translatable("itemGroup.createcleargoal"))
			.icon(() -> RECIPE_FILTER.get().getDefaultInstance())
			.displayItems((parameters, output) -> output.accept(RECIPE_FILTER.get()))
			.build());

	public Createcleargoal(IEventBus modEventBus, ModContainer modContainer) {
		modEventBus.addListener(this::commonSetup);

		ITEMS.register(modEventBus);
		CREATIVE_MODE_TABS.register(modEventBus);
		AllDataComponents.DATA_COMPONENT_TYPES.register(modEventBus);
		AllMenuTypes.MENUS.register(modEventBus);
	}

	private void commonSetup(final FMLCommonSetupEvent event) {
		ModPackets.register();
	}

	@EventBusSubscriber(modid = MODID, value = Dist.CLIENT)
	public static class ClientModEvents {
		@SubscribeEvent
		public static void registerScreens(RegisterMenuScreensEvent event) {
			event.register(AllMenuTypes.RECIPE_FILTER.get(),
				net.Tetrachlorosilane.createcleargoal.client.RecipeFilterScreen::new);
		}
	}
}
