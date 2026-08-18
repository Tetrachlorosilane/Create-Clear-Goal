package net.Tetrachlorosilane.createcleargoal.client;

import java.util.List;

import com.simibubi.create.content.logistics.AddressEditBox;
import com.simibubi.create.content.logistics.box.PackageStyles;
import com.simibubi.create.content.trains.station.NoShadowFontWrapper;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;
import com.simibubi.create.foundation.gui.widget.ScrollInput;

import net.Tetrachlorosilane.createcleargoal.content.productreturn.ProductReturnStationBlockEntity;
import net.Tetrachlorosilane.createcleargoal.content.productreturn.ProductReturnStationConfigurePacket;

import net.createmod.catnip.gui.AbstractSimiScreen;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Configuration GUI for the Product Return Station.
 * <p>
 * Uses the factory-gauge style {@code produce_return.png} panel and Create's
 * {@link AddressEditBox} / {@link ScrollInput} widgets.
 */
public class ProductReturnStationScreen extends AbstractSimiScreen {

	private final ProductReturnStationBlockEntity blockEntity;
	private AddressEditBox inputField;
	private AddressEditBox outputField;
	private ScrollInput promiseExpiration;

	public ProductReturnStationScreen(ProductReturnStationBlockEntity blockEntity) {
		this.blockEntity = blockEntity;
	}

	public static void open(ProductReturnStationBlockEntity blockEntity) {
		net.createmod.catnip.gui.ScreenOpener.open(new ProductReturnStationScreen(blockEntity));
	}

	@Override
	protected void init() {
		setWindowSize(ModGuiTextures.PRODUCT_RETURN_BG.getWidth(), ModGuiTextures.PRODUCT_RETURN_BG.getHeight());
		super.init();
		clearWidgets();

		int x = guiLeft;
		int y = guiTop;

		// Text boxes: top-left at (35,29) and (35,65), 108x10 like factory gauge.
		// The original bottom-left spec was (35,38)/(35,74), plus a 1px downward nudge.
		inputField = new AddressEditBox(this, new NoShadowFontWrapper(font), x + 35, y + 29, 108, 10, false);
		inputField.setValue(blockEntity.inputAddress);
		inputField.setTextColor(0x555555);
		inputField.setTooltip(Tooltip.create(
			Component.translatable("createcleargoal.product_return_station.input_address")));
		addRenderableWidget(inputField);

		outputField = new AddressEditBox(this, new NoShadowFontWrapper(font), x + 35, y + 65, 108, 10, false);
		outputField.setValue(blockEntity.outputAddress);
		outputField.setTextColor(0x555555);
		outputField.setTooltip(Tooltip.create(
			Component.translatable("createcleargoal.product_return_station.output_address")));
		addRenderableWidget(outputField);

		// Promise expiry scroll box: top-left at (97,95), 28x16 like factory gauge.
		promiseExpiration = new ScrollInput(x + 97, y + 95, 28, 16).withRange(-1, 31)
			.titled(Component.translatable("createcleargoal.product_return_station.promises_expire_title"));
		promiseExpiration.setState(blockEntity.promiseClearingInterval);
		addRenderableWidget(promiseExpiration);

		IconButton confirm = new IconButton(x + ModGuiTextures.PRODUCT_RETURN_BG.getWidth() - 33,
			y + ModGuiTextures.PRODUCT_RETURN_BG.getHeight() - 25, AllIcons.I_CONFIRM);
		confirm.setToolTip(Component.translatable("createcleargoal.product_return_station.save_and_close"));
		confirm.withCallback(() -> {
			CatnipServices.NETWORK.sendToServer(new ProductReturnStationConfigurePacket(blockEntity.getBlockPos(),
				inputField.getValue(), outputField.getValue(), promiseExpiration.getState()));
			onClose();
		});
		addRenderableWidget(confirm);
	}

	@Override
	public void tick() {
		super.tick();
		if (inputField != null)
			inputField.tick();
		if (outputField != null)
			outputField.tick();
		if (promiseExpiration != null) {
			int state = promiseExpiration.getState();
			promiseExpiration.titled(Component.translatable(
				state == -1 ? "createcleargoal.product_return_station.promises_do_not_expire"
					: "createcleargoal.product_return_station.promises_expire_title"));
		}
	}

	@Override
	protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
		int x = guiLeft;
		int y = guiTop;

		ModGuiTextures.PRODUCT_RETURN_BG.render(graphics, x, y);

		// Title, centered at the top like other Create GUIs.
		Component title = blockEntity.getBlockState()
			.getBlock()
			.getName();
		graphics.drawString(font, title,
			x + ModGuiTextures.PRODUCT_RETURN_BG.getWidth() / 2 - font.width(title) / 2, y + 4, 0x3D3C48, false);

		// Show the selected expiry value inside the scroll box, like factory gauge.
		if (promiseExpiration != null) {
			int state = promiseExpiration.getState();
			graphics.drawString(font,
				Component.literal(state == -1 ? " /" : state == 0 ? "30s" : state + "m"),
				promiseExpiration.getX() + 3, promiseExpiration.getY() + 4, 0xffeeeeee, true);
		}

		// Promise count indicator, positioned relative to the scroll box like factory gauge.
		ItemStack asStack = PackageStyles.getDefaultBox();
		int itemX = x + 68;
		int itemY = y + 95;
		graphics.renderItem(asStack, itemX, itemY);
		int promised = blockEntity.lastReportedPromises;
		graphics.renderItemDecorations(font, asStack, itemX, itemY, promised + "");

		if (mouseX >= itemX && mouseX < itemX + 16 && mouseY >= itemY && mouseY < itemY + 16) {
			List<Component> promiseTip;
			if (promised == 0) {
				promiseTip = List.of(
					Component.translatable("createcleargoal.product_return_station.no_open_promises")
						.withStyle(style -> style.withColor(ScrollInput.HEADER_RGB.getRGB())),
					Component.translatable("createcleargoal.product_return_station.no_open_promises_tip")
						.withStyle(ChatFormatting.GRAY));
			} else {
				promiseTip = List.of(
					Component.translatable("createcleargoal.product_return_station.promised_items")
						.withStyle(style -> style.withColor(ScrollInput.HEADER_RGB.getRGB())),
					Component.translatable("createcleargoal.product_return_station.promise_total", promised)
						.withStyle(ChatFormatting.GRAY));
			}
			graphics.renderComponentTooltip(font, promiseTip, mouseX, mouseY);
		}
	}
}
