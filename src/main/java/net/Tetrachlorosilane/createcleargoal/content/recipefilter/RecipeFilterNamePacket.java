package net.Tetrachlorosilane.createcleargoal.content.recipefilter;

import net.Tetrachlorosilane.createcleargoal.ModPackets;

import io.netty.buffer.ByteBuf;
import net.createmod.catnip.net.base.BasePacketPayload;
import net.createmod.catnip.net.base.ServerboundPacketPayload;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;

/** Client -> server: rename the selected recipe entry. */
public record RecipeFilterNamePacket(int index, String name) implements ServerboundPacketPayload {

	public static final StreamCodec<ByteBuf, RecipeFilterNamePacket> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, RecipeFilterNamePacket::index,
		ByteBufCodecs.STRING_UTF8, RecipeFilterNamePacket::name,
		RecipeFilterNamePacket::new);

	@Override
	public void handle(ServerPlayer player) {
		if (player.containerMenu instanceof RecipeFilterMenu menu)
			menu.setName(index(), name());
	}

	@Override
	public BasePacketPayload.PacketTypeProvider getTypeProvider() {
		return ModPackets.SET_NAME;
	}
}
