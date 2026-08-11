package net.Tetrachlorosilane.createcleargoal.content.recipefilter;

import net.Tetrachlorosilane.createcleargoal.ModPackets;

import io.netty.buffer.ByteBuf;
import net.createmod.catnip.net.base.BasePacketPayload;
import net.createmod.catnip.net.base.ServerboundPacketPayload;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;

/** Client -> server: switch the menu's selected recipe entry. */
public record RecipeFilterSelectPacket(int index) implements ServerboundPacketPayload {

	public static final StreamCodec<ByteBuf, RecipeFilterSelectPacket> STREAM_CODEC =
		ByteBufCodecs.VAR_INT.map(RecipeFilterSelectPacket::new, RecipeFilterSelectPacket::index);

	@Override
	public void handle(ServerPlayer player) {
		if (player.containerMenu instanceof RecipeFilterMenu menu)
			menu.selectEntry(index());
	}

	@Override
	public BasePacketPayload.PacketTypeProvider getTypeProvider() {
		return ModPackets.SELECT_ENTRY;
	}
}
