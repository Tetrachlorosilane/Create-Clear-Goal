package net.Tetrachlorosilane.createcleargoal;

import com.mojang.logging.LogUtils;
import net.Tetrachlorosilane.createcleargoal.content.recipefilter.RecipeFilterItem;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(Createcleargoal.MODID)
public class Createcleargoal {
	// Define mod id in a common place for everything to reference
	public static final String MODID = "createcleargoal";
	// Directly reference a slf4j logger
	private static final Logger LOGGER = LogUtils.getLogger();
	// Deferred Register for items
	public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
	// Deferred Register for creative mode tabs
	public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
		DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

	// The recipe filter item: records whole recipes so processing machines can lock onto them
	public static final DeferredItem<RecipeFilterItem> RECIPE_FILTER =
		ITEMS.register("recipe_filter", () -> new RecipeFilterItem(new Item.Properties().stacksTo(1)));

	// Creative tab for this mod's items
	public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CREATIVE_TAB =
		CREATIVE_MODE_TABS.register("main", () -> CreativeModeTab.builder()
			.title(Component.translatable("itemGroup.createcleargoal"))
			.icon(() -> RECIPE_FILTER.get().getDefaultInstance())
			.displayItems((parameters, output) -> output.accept(RECIPE_FILTER.get()))
			.build());

	// The constructor for the mod class is the first code that is run when your mod is loaded.
	// FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
	public Createcleargoal(IEventBus modEventBus, ModContainer modContainer) {
		// Register the commonSetup method for modloading
		modEventBus.addListener(this::commonSetup);

		// Register the Deferred Registers to the mod event bus
		ITEMS.register(modEventBus);
		CREATIVE_MODE_TABS.register(modEventBus);
		AllDataComponents.DATA_COMPONENT_TYPES.register(modEventBus);
		AllMenuTypes.MENUS.register(modEventBus);

		// Register ourselves for server and other game events we are interested in.
		NeoForge.EVENT_BUS.register(this);

		// Register our mod's ModConfigSpec so that FML can create and load the config file for us
		modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
	}

	private void commonSetup(final FMLCommonSetupEvent event) {
		LOGGER.info("HELLO FROM COMMON SETUP");
		ModPackets.register();

		if (Config.logDirtBlock) LOGGER.info("DIRT BLOCK >> {}", BuiltInRegistries.BLOCK.getKey(Blocks.DIRT));

		LOGGER.info(Config.magicNumberIntroduction + Config.magicNumber);

		Config.items.forEach((item) -> LOGGER.info("ITEM >> {}", item.toString()));
	}

	// You can use SubscribeEvent and let the Event Bus discover methods to call
	@SubscribeEvent
	public void onServerStarting(ServerStartingEvent event) {
		// Do something when the server starts
		LOGGER.info("HELLO from server starting");
	}

	// You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
	@EventBusSubscriber(modid = MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
	public static class ClientModEvents {
		@SubscribeEvent
		public static void onClientSetup(FMLClientSetupEvent event) {
			// Some client setup code
			LOGGER.info("HELLO FROM CLIENT SETUP");
			LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
		}

		@SubscribeEvent
		public static void registerScreens(RegisterMenuScreensEvent event) {
			event.register(AllMenuTypes.RECIPE_FILTER.get(),
				net.Tetrachlorosilane.createcleargoal.client.RecipeFilterScreen::new);
		}
	}
}
