package net.Tetrachlorosilane.createcleargoal.content.recipefilter;

import net.Tetrachlorosilane.createcleargoal.ModPackets;

import io.netty.buffer.ByteBuf;
import net.createmod.catnip.net.base.BasePacketPayload;
import net.createmod.catnip.net.base.ServerboundPacketPayload;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;

/** Client -> server: delete the currently selected entry. */
public record RecipeFilterDeletePacket() implements ServerboundPacketPayload {

	public static final StreamCodec<ByteBuf, RecipeFilterDeletePacket> STREAM_CODEC =
		StreamCodec.unit(new RecipeFilterDeletePacket());

	@Override
	public void handle(ServerPlayer player) {
		if (player.containerMenu instanceof RecipeFilterMenu menu)
			menu.deleteSelectedEntry();
	}

	@Override
	public BasePacketPayload.PacketTypeProvider getTypeProvider() {
		return ModPackets.DELETE_ENTRY;
	}
}
