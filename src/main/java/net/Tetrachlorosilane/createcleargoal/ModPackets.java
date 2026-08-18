package net.Tetrachlorosilane.createcleargoal;

import java.util.Locale;

import net.Tetrachlorosilane.createcleargoal.content.productreturn.ProductReturnStationConfigurePacket;
import net.Tetrachlorosilane.createcleargoal.content.productreturn.ProductReturnStationOpenScreenPacket;
import net.Tetrachlorosilane.createcleargoal.content.recipefilter.RecipeFilterClearPacket;
import net.Tetrachlorosilane.createcleargoal.content.recipefilter.RecipeFilterDeletePacket;
import net.Tetrachlorosilane.createcleargoal.content.recipefilter.RecipeFilterImportPacket;
import net.Tetrachlorosilane.createcleargoal.content.recipefilter.RecipeFilterModePacket;
import net.Tetrachlorosilane.createcleargoal.content.recipefilter.RecipeFilterNamePacket;
import net.Tetrachlorosilane.createcleargoal.content.recipefilter.RecipeFilterOutputMatchPacket;
import net.Tetrachlorosilane.createcleargoal.content.recipefilter.RecipeFilterSelectPacket;

import net.createmod.catnip.net.base.BasePacketPayload;
import net.createmod.catnip.net.base.CatnipPacketRegistry;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public enum ModPackets implements BasePacketPayload.PacketTypeProvider {
	// Client to Server
	IMPORT_RECIPE(RecipeFilterImportPacket.class, RecipeFilterImportPacket.STREAM_CODEC),
	SELECT_ENTRY(RecipeFilterSelectPacket.class, RecipeFilterSelectPacket.STREAM_CODEC),
	SET_NAME(RecipeFilterNamePacket.class, RecipeFilterNamePacket.STREAM_CODEC),
	SET_MODE(RecipeFilterModePacket.class, RecipeFilterModePacket.STREAM_CODEC),
	SET_OUTPUT_MATCH(RecipeFilterOutputMatchPacket.class, RecipeFilterOutputMatchPacket.STREAM_CODEC),
	DELETE_ENTRY(RecipeFilterDeletePacket.class, RecipeFilterDeletePacket.STREAM_CODEC),
	CLEAR_ENTRIES(RecipeFilterClearPacket.class, RecipeFilterClearPacket.STREAM_CODEC),
	CONFIGURE_PRODUCT_RETURN_STATION(ProductReturnStationConfigurePacket.class,
		ProductReturnStationConfigurePacket.STREAM_CODEC),
	OPEN_PRODUCT_RETURN_STATION(ProductReturnStationOpenScreenPacket.class,
		ProductReturnStationOpenScreenPacket.STREAM_CODEC);

	private final CatnipPacketRegistry.PacketType<?> type;

	<T extends BasePacketPayload> ModPackets(Class<T> clazz, StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
		String name = this.name().toLowerCase(Locale.ROOT);
		this.type = new CatnipPacketRegistry.PacketType<>(
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Createcleargoal.MODID, name)),
			clazz, codec
		);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends CustomPacketPayload> CustomPacketPayload.Type<T> getType() {
		return (CustomPacketPayload.Type<T>) this.type.type();
	}

	public static void register() {
		CatnipPacketRegistry packetRegistry = new CatnipPacketRegistry(Createcleargoal.MODID, "1.0");
		for (ModPackets packet : ModPackets.values())
			packetRegistry.registerPacket(packet.type);
		packetRegistry.registerAllPackets();
	}
}
