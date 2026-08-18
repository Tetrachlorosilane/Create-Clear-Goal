package net.Tetrachlorosilane.createcleargoal.content.productreturn;

import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBlockItem;
import com.simibubi.create.foundation.utility.CreateLang;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;

/**
 * Item for the Product Return Station.
 * <p>
 * Like the Factory Gauge, it must be tuned to an existing network before it can
 * be placed; it is never allowed to create a new network by placing it.
 */
public class ProductReturnStationBlockItem extends LogisticallyLinkedBlockItem {

	public ProductReturnStationBlockItem(Block block, Properties properties) {
		super(block, properties);
	}

	@Override
	public InteractionResult place(BlockPlaceContext pContext) {
		ItemStack stack = pContext.getItemInHand();
		if (!isTuned(stack)) {
			AllSoundEvents.DENY.playOnServer(pContext.getLevel(), pContext.getClickedPos());
			if (pContext.getPlayer() != null) {
				pContext.getPlayer()
					.displayClientMessage(CreateLang.translate("factory_panel.tune_before_placing")
						.component(), true);
			}
			return InteractionResult.FAIL;
		}
		return super.place(pContext);
	}
}
