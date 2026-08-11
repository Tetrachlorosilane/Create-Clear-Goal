package net.Tetrachlorosilane.createcleargoal;

import net.Tetrachlorosilane.createcleargoal.content.recipefilter.RecipeFilterMenu;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class AllMenuTypes {
	public static final DeferredRegister<MenuType<?>> MENUS =
		DeferredRegister.create(Registries.MENU, Createcleargoal.MODID);

	public static final DeferredHolder<MenuType<?>, MenuType<RecipeFilterMenu>> RECIPE_FILTER =
		MENUS.register("recipe_filter", () -> IMenuTypeExtension.create(RecipeFilterMenu::new));
}
