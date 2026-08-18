package net.Tetrachlorosilane.createcleargoal.content.productreturn;

import net.Tetrachlorosilane.createcleargoal.ModPackets;

import io.netty.buffer.ByteBuf;
import net.createmod.catnip.net.base.BasePacketPayload;
import net.createmod.catnip.net.base.ClientboundPacketPayload;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Server -> client: ask the client to open the Product Return Station GUI,
 * including the current GUI fields so it is correct even if the block-entity
 * sync is not up to date.
 */
public record ProductReturnStationOpenScreenPacket(BlockPos pos, String inputAddress, String outputAddress,
	int promiseClearingInterval, int totalPromised) implements ClientboundPacketPayload {

	public static final StreamCodec<ByteBuf, ProductReturnStationOpenScreenPacket> STREAM_CODEC =
		StreamCodec.composite(
			BlockPos.STREAM_CODEC, ProductReturnStationOpenScreenPacket::pos,
			ByteBufCodecs.STRING_UTF8, ProductReturnStationOpenScreenPacket::inputAddress,
			ByteBufCodecs.STRING_UTF8, ProductReturnStationOpenScreenPacket::outputAddress,
			ByteBufCodecs.VAR_INT, ProductReturnStationOpenScreenPacket::promiseClearingInterval,
			ByteBufCodecs.VAR_INT, ProductReturnStationOpenScreenPacket::totalPromised,
			ProductReturnStationOpenScreenPacket::new);

	@Override
	public BasePacketPayload.PacketTypeProvider getTypeProvider() {
		return ModPackets.OPEN_PRODUCT_RETURN_STATION;
	}

	@Override
	@OnlyIn(Dist.CLIENT)
	public void handle(LocalPlayer player) {
		if (player.level()
			.getBlockEntity(pos) instanceof ProductReturnStationBlockEntity be) {
			be.inputAddress = inputAddress;
			be.outputAddress = outputAddress;
			be.promiseClearingInterval = promiseClearingInterval;
			be.lastReportedPromises = totalPromised;
			net.Tetrachlorosilane.createcleargoal.client.ProductReturnStationScreen.open(be);
		}
	}
}
