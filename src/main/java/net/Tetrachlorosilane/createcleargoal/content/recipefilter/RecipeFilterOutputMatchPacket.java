package net.Tetrachlorosilane.createcleargoal.content.recipefilter;

import net.Tetrachlorosilane.createcleargoal.ModPackets;

import io.netty.buffer.ByteBuf;
import net.createmod.catnip.net.base.BasePacketPayload;
import net.createmod.catnip.net.base.ServerboundPacketPayload;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;

/** Client -> server: change the selected entry's output-match mode (exact / contains). */
public record RecipeFilterOutputMatchPacket(OutputMatchMode mode) implements ServerboundPacketPayload {

	public static final StreamCodec<ByteBuf, RecipeFilterOutputMatchPacket> STREAM_CODEC =
		ByteBufCodecs.STRING_UTF8.map(
			s -> new RecipeFilterOutputMatchPacket(OutputMatchMode.byName(s, OutputMatchMode.EXACT)),
			p -> p.mode().getSerializedName());

	@Override
	public void handle(ServerPlayer player) {
		if (player.containerMenu instanceof RecipeFilterMenu menu)
			menu.setOutputMatch(mode());
	}

	@Override
	public BasePacketPayload.PacketTypeProvider getTypeProvider() {
		return ModPackets.SET_OUTPUT_MATCH;
	}
}
