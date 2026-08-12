package net.Tetrachlorosilane.createcleargoal.content.recipefilter;

import java.util.Optional;

import net.Tetrachlorosilane.createcleargoal.ModPackets;

import io.netty.buffer.ByteBuf;
import net.createmod.catnip.net.base.BasePacketPayload;
import net.createmod.catnip.net.base.ServerboundPacketPayload;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

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
		Optional<Recipe<?>> recipe = player.level()
			.getRecipeManager()
			.byKey(recipeId)
			.map(RecipeHolder::value);
		if (recipe.isEmpty())
			return;

		RecipeFilterEntry entry = RecipeFilterItem.fromRecipe(recipeId, recipe.get(), player.level().registryAccess());
		if (player.containerMenu instanceof RecipeFilterMenu menu) {
			// through the open menu so the GUI list, slots and templates stay in sync
			menu.importEntry(entry);
			return;
		}
		ItemStack held = player.getMainHandItem();
		if (held.getItem() instanceof RecipeFilterItem)
			RecipeFilterItem.addEntry(held, entry);
	}

	@Override
	public BasePacketPayload.PacketTypeProvider getTypeProvider() {
		return ModPackets.IMPORT_RECIPE;
	}
}
