package net.Tetrachlorosilane.createcleargoal;

import java.util.List;

import net.Tetrachlorosilane.createcleargoal.content.recipefilter.FilterMode;
import net.Tetrachlorosilane.createcleargoal.content.recipefilter.RecipeFilterEntry;

import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class AllDataComponents {
	public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENT_TYPES =
		DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, Createcleargoal.MODID);

	public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<RecipeFilterEntry>>> RECIPE_FILTER_ENTRIES =
		DATA_COMPONENT_TYPES.register("recipe_filter_entries",
			() -> DataComponentType.<List<RecipeFilterEntry>>builder()
				.persistent(RecipeFilterEntry.LIST_CODEC)
				.build());

	public static final DeferredHolder<DataComponentType<?>, DataComponentType<FilterMode>> RECIPE_FILTER_MODE =
		DATA_COMPONENT_TYPES.register("recipe_filter_mode",
			() -> DataComponentType.<FilterMode>builder()
				.persistent(FilterMode.CODEC)
				.build());
}
