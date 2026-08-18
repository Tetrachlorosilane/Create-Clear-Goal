package net.Tetrachlorosilane.createcleargoal.content.productreturn;

import net.Tetrachlorosilane.createcleargoal.ModPackets;

import io.netty.buffer.ByteBuf;
import net.createmod.catnip.net.base.BasePacketPayload;
import net.createmod.catnip.net.base.ServerboundPacketPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;

/**
 * Client -> server: update the Product Return Station's input/output addresses
 * and promise expiry setting.
 */
public record ProductReturnStationConfigurePacket(BlockPos pos, String inputAddress,
	String outputAddress, int promiseClearingInterval) implements ServerboundPacketPayload {

	public static final StreamCodec<ByteBuf, ProductReturnStationConfigurePacket> STREAM_CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC, ProductReturnStationConfigurePacket::pos,
		ByteBufCodecs.STRING_UTF8, ProductReturnStationConfigurePacket::inputAddress,
		ByteBufCodecs.STRING_UTF8, ProductReturnStationConfigurePacket::outputAddress,
		ByteBufCodecs.VAR_INT, ProductReturnStationConfigurePacket::promiseClearingInterval,
		ProductReturnStationConfigurePacket::new);

	@Override
	public void handle(ServerPlayer player) {
		if (player.level().getBlockEntity(pos) instanceof ProductReturnStationBlockEntity be) {
			String oldInput = be.inputAddress;
			be.inputAddress = inputAddress == null ? "" : inputAddress.trim();
			be.outputAddress = outputAddress == null ? "" : outputAddress.trim();
			be.promiseClearingInterval = Math.max(-1, Math.min(31, promiseClearingInterval));
			// Changing the input address is treated as a re-placement: the old
			// queue is discarded and the station re-registers under the new address.
			if (!oldInput.equals(be.inputAddress))
				ProductReturnStationManager.onAddressChanged(be, oldInput);
			be.syncPromises = false;
			be.setChanged();
			be.notifyUpdate();
		}
	}

	@Override
	public BasePacketPayload.PacketTypeProvider getTypeProvider() {
		return ModPackets.CONFIGURE_PRODUCT_RETURN_STATION;
	}
}
