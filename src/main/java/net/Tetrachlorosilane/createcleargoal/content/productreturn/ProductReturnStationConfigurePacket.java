package net.Tetrachlorosilane.createcleargoal.content.productreturn;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import net.Tetrachlorosilane.createcleargoal.ModPackets;

import io.netty.buffer.ByteBuf;
import net.createmod.catnip.net.base.BasePacketPayload;
import net.createmod.catnip.net.base.ServerboundPacketPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client -> server: update the Product Return Station's input/output addresses
 * and promise expiry setting.
 */
public record ProductReturnStationConfigurePacket(BlockPos pos, String inputAddress,
	String outputAddress, int promiseClearingInterval) implements ServerboundPacketPayload {


	private static final Logger LOGGER = LoggerFactory.getLogger(ProductReturnStationConfigurePacket.class);
	public static final StreamCodec<ByteBuf, ProductReturnStationConfigurePacket> STREAM_CODEC = StreamCodec.composite(
		BlockPos.STREAM_CODEC, ProductReturnStationConfigurePacket::pos,
		ByteBufCodecs.STRING_UTF8, ProductReturnStationConfigurePacket::inputAddress,
		ByteBufCodecs.STRING_UTF8, ProductReturnStationConfigurePacket::outputAddress,
		ByteBufCodecs.VAR_INT, ProductReturnStationConfigurePacket::promiseClearingInterval,
		ProductReturnStationConfigurePacket::new);

	@Override
	public void handle(ServerPlayer player) {
		// Do not force-load distant chunks just to handle a client packet.
		if (!player.level().isLoaded(pos))
			return;
		if (player.blockPosition().distSqr(pos) > 6 * 6)
			return;
		if (!player.level().mayInteract(player, pos))
			return;

		String newInput = inputAddress == null ? "" : inputAddress.trim();
		String newOutput = outputAddress == null ? "" : outputAddress.trim();
		if (newInput.length() > ProductReturnStationManager.MAX_INPUT_ADDRESS_LENGTH)
			return;
		if (newOutput.length() > ProductReturnStationManager.MAX_OUTPUT_TEMPLATE_LENGTH)
			return;
		if (promiseClearingInterval < -1 || promiseClearingInterval > 31)
			return;

		if (AddressRule.isRegex(newInput)) {
			try {
				Pattern.compile(newInput.substring(AddressRule.REGEX_PREFIX.length()));
			} catch (PatternSyntaxException e) {
				// Reject invalid Java regexes transactionally: keep the old
				// configuration and do not clear the existing promise queue.
				LOGGER.warn("Rejected invalid Product Return Station regex '{}' from {}: {} (at {})",
					newInput, player.getGameProfile().getName(), e.getDescription(), e.getIndex());
				return;
			}
		}

		if (player.level().getBlockEntity(pos) instanceof ProductReturnStationBlockEntity be) {
			String oldInput = be.inputAddress;
			be.inputAddress = newInput;
			be.outputAddress = newOutput;
			be.promiseClearingInterval = promiseClearingInterval;
			be.rebuildAddressRule();
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
