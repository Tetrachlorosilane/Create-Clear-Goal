package net.Tetrachlorosilane.createcleargoal.content.recipefilter;

import net.Tetrachlorosilane.createcleargoal.ModPackets;

import io.netty.buffer.ByteBuf;
import net.createmod.catnip.net.base.BasePacketPayload;
import net.createmod.catnip.net.base.ServerboundPacketPayload;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;

/** Client -> server: change the filter-level mode (block / allow-only / lock). */
public record RecipeFilterModePacket(FilterMode mode) implements ServerboundPacketPayload {

	public static final StreamCodec<ByteBuf, RecipeFilterModePacket> STREAM_CODEC = ByteBufCodecs.STRING_UTF8.map(
		s -> new RecipeFilterModePacket(FilterMode.byName(s, FilterMode.LOCK)),
		p -> p.mode().getSerializedName());

	@Override
	public void handle(ServerPlayer player) {
		// persist through the menu: writes the edited item's component server-side
		if (player.containerMenu instanceof RecipeFilterMenu menu)
			menu.setMode(mode());
	}

	@Override
	public BasePacketPayload.PacketTypeProvider getTypeProvider() {
		return ModPackets.SET_MODE;
	}
}
