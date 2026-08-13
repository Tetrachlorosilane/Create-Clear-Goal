package net.Tetrachlorosilane.createcleargoal.compat.rei;

import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.screen.ScreenRegistry;
import me.shedaniel.rei.api.client.registry.transfer.TransferHandlerRegistry;
import me.shedaniel.rei.forge.REIPluginClient;

/**
 * REI client plugin for the recipe filter: registers a transfer handler so
 * REI's transfer button imports the shown recipe into the held filter, plus a
 * drag &amp; drop visitor for the filter's 12 ghost slots.
 * <p>
 * The class is only loaded by REI's own {@code @REIPluginClient} scanner, so
 * it is safe to ship with REI absent.
 */
@REIPluginClient
public class RecipeFilterReiPlugin implements REIClientPlugin {

	@Override
	public void registerTransferHandlers(TransferHandlerRegistry registry) {
		registry.register(new RecipeFilterImportTransferHandler());
	}

	@Override
	public void registerScreens(ScreenRegistry registry) {
		registry.registerDraggableStackVisitor(new RecipeFilterGhostSlotVisitor());
	}
}
