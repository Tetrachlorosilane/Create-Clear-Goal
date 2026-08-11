package net.Tetrachlorosilane.createcleargoal.content.recipefilter;

import java.util.ArrayList;
import java.util.List;

import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;

import net.Tetrachlorosilane.createcleargoal.ModPackets;

import io.netty.buffer.ByteBuf;
import net.createmod.catnip.net.base.BasePacketPayload;
import net.createmod.catnip.net.base.ServerboundPacketPayload;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;

/**
 * Client -> server: import a recipe (from JEI) into the held recipe filter as
 * a new entry. The server resolves the recipe, snapshots its inputs/outputs
 * and appends the entry to the filter's component.
 */
public record RecipeFilterImportPacket(ResourceLocation recipeId) implements ServerboundPacketPayload {

	public static final StreamCodec<ByteBuf, RecipeFilterImportPacket> STREAM_CODEC = StreamCodec.of(
		(buf, packet) -> ResourceLocation.STREAM_CODEC.encode(buf, packet.recipeId()),
		buf -> new RecipeFilterImportPacket(ResourceLocation.STREAM_CODEC.decode(buf)));

	@Override
	public void handle(ServerPlayer player) {
		ItemStack held = player.getMainHandItem();
		if (!(held.getItem() instanceof RecipeFilterItem))
			return;

		player.level().getRecipeManager()
			.byKey(recipeId)
			.ifPresent(holder -> {
				Recipe<?> recipe = holder.value();
				List<ItemStack> inputs = new ArrayList<>();
				for (Ingredient ingredient : recipe.getIngredients()) {
					ItemStack[] items = ingredient.getItems();
					if (items.length > 0 && !items[0].isEmpty())
						inputs.add(items[0]);
					if (inputs.size() >= RecipeFilterMenu.INPUT_SLOTS)
						break;
				}
				List<ItemStack> outputs = new ArrayList<>();
				ItemStack result = recipe.getResultItem(player.level().registryAccess());
				if (!result.isEmpty())
					outputs.add(result);
				if (recipe instanceof ProcessingRecipe<?, ?> processing) {
					for (ProcessingOutput output : processing.getRollableResults()) {
						ItemStack stack = output.getStack();
						if (!stack.isEmpty() && outputs.stream().noneMatch(o -> ItemStack.isSameItem(o, stack)))
							outputs.add(stack);
					}
				}
				RecipeFilterItem.addEntry(held, RecipeFilterEntry.ofRecipe(
					outputs.isEmpty() ? "" : outputs.get(0).getHoverName().getString(), recipeId, inputs, outputs));
			});
	}

	@Override
	public BasePacketPayload.PacketTypeProvider getTypeProvider() {
		return ModPackets.IMPORT_RECIPE;
	}
}
