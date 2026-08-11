package net.Tetrachlorosilane.createcleargoal.content.recipefilter;

import net.Tetrachlorosilane.createcleargoal.ModPackets;

import io.netty.buffer.ByteBuf;
import net.createmod.catnip.net.base.BasePacketPayload;
import net.createmod.catnip.net.base.ServerboundPacketPayload;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;

/**
 * Client -> server: clear all configuration of the held recipe filter
 * (all entries + reset the filter mode), mirroring Create's
 * {@code IClearableMenu.sendClearPacket} pattern.
 */
public record RecipeFilterClearPacket() implements ServerboundPacketPayload {

	public static final StreamCodec<ByteBuf, RecipeFilterClearPacket> STREAM_CODEC =
		StreamCodec.unit(new RecipeFilterClearPacket());

	@Override
	public void handle(ServerPlayer player) {
		if (player.containerMenu instanceof RecipeFilterMenu menu)
			menu.clearContents();
	}

	@Override
	public BasePacketPayload.PacketTypeProvider getTypeProvider() {
		return ModPackets.CLEAR_ENTRIES;
	}
}
