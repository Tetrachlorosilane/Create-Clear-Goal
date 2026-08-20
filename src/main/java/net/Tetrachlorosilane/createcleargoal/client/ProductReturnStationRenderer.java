package net.Tetrachlorosilane.createcleargoal.client;

import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;

import net.Tetrachlorosilane.createcleargoal.content.productreturn.ProductReturnStationBlockEntity;

import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/**
 * Renders the Product Return Station. The conflict warning is shown through
 * Create's goggle overlay (IHaveGoggleInformation) instead of a hover nameplate.
 */
public class ProductReturnStationRenderer extends SmartBlockEntityRenderer<ProductReturnStationBlockEntity> {

	public ProductReturnStationRenderer(BlockEntityRendererProvider.Context context) {
		super(context);
	}
}
