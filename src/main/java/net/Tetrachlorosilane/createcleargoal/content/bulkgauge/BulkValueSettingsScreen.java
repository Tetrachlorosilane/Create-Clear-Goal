package net.Tetrachlorosilane.createcleargoal.content.bulkgauge;

import java.util.function.Consumer;

import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.Window;
import com.simibubi.create.AllKeys;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBehaviour.ValueSettings;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueHandler;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.utility.CreateLang;

import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.createmod.catnip.gui.UIRenderHelper;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec2;

/**
 * Four-row target-amount slider for the bulk factory gauge, replacing Create's
 * {@code ValueSettingsScreen} for bulk panels. All rows are in stacks:
 * <ul>
 * <li>0: 0..32 stacks - linear scale</li>
 * <li>1: 32..256 stacks - segments (32/64/128/256)</li>
 * <li>2: 256..2048 stacks - segments (256/512/1024/2048)</li>
 * <li>3: 2048..16384 stacks - segments (2048/4096/8192/16384)</li>
 * </ul>
 * Segmented rows interpolate linearly inside each power-of-two segment, so the
 * overall feel is coarse-to-fine while individual values stay exact integers
 * (no fractions). Row geometry lives on {@link BulkFactoryPanelBehaviour} and
 * the bar width is computed from it (segment count * segment pixels +
 * milestones), mirroring Create's ValueSettingsScreen sizing.
 * <p>
 * This is a self-contained copy of Create's {@code ValueSettingsScreen} (that
 * class keeps all of its state private and its column geometry is linear and
 * shared across rows), with the coordinate mapping and milestone rendering
 * replaced by the segmented mapping above.
 */
public class BulkValueSettingsScreen extends AbstractSimiScreen {

	/**
	 * Rows: [min, max] stacks. Row 0 is linear; rows 1-3 are segmented at
	 * power-of-two boundaries (32/64/128/256, 256/512/1024/2048,
	 * 2048/4096/8192/16384) with linear interpolation inside each segment.
	 * Geometry data lives on {@link BulkFactoryPanelBehaviour} so the board and
	 * the screen share one source of truth.
	 */

	/**
	 * Pixels per power-of-two segment. Sized so the whole bar matches the
	 * vanilla factory gauge slider: Create's ValueSettingsScreen computes
	 * {@code valueBarWidth = (maxValue+1)*scale + 1 + milestoneCount*milestoneSize},
	 * which is ~247px for the gauge's 0-100 board; with 3 segments + 4
	 * milestones this gives 3*76 + 4*4 = 244px. Raise SEGMENT_WIDTH to lengthen
	 * the bar.
	 */
	private static final int SEGMENT_WIDTH = 76;
	/** Width of Create's VALUE_SETTINGS_MILESTONE texture. */
	private static final int MILESTONE_SIZE = 4;
	private static final int MAX_SEGMENTS =
		java.util.Arrays.stream(BulkFactoryPanelBehaviour.ROW_SEGMENTS)
			.max()
			.orElse(1);
	/**
	 * Bar width, computed like Create's ValueSettingsScreen (value area +
	 * milestone markers) but sized by segments: every row spans the widest row's
	 * segment count, so all four bars line up.
	 */
	private static final int VALUE_BAR_WIDTH =
		MAX_SEGMENTS * SEGMENT_WIDTH + (MAX_SEGMENTS + 1) * MILESTONE_SIZE;

	private int ticksOpen;
	private ValueSettingsBoard board;
	private int maxLabelWidth;
	private BlockPos pos;
	private ValueSettings initialSettings;
	private ValueSettings lastHovered = new ValueSettings(-1, -1);
	private Consumer<ValueSettings> onHover;
	private int soundCoolDown;
	private int netId;

	public BulkValueSettingsScreen(BlockPos pos, ValueSettingsBoard board, ValueSettings valueSettings,
		Consumer<ValueSettings> onHover, int netId) {
		this.pos = pos;
		this.board = board;
		this.initialSettings = valueSettings;
		this.onHover = onHover;
		this.netId = netId;
	}

	// --- segmented scale helpers ---

	/**
	 * Snap radius in pixels around each power-of-two segment boundary: values
	 * there get a guaranteed clickable area. Without this, the boundary value's
	 * hit column is half a grid cell wide on each side (< 1px on the dense
	 * high rows), so 64/128/256/... could never be picked with integer mouse
	 * coordinates.
	 */
	private static final double SNAP_PIXELS = 2.0;

	/** Normalised position in [0, 1] of a value on its row's bar. */
	private static double fractionOf(int row, int value) {
		int[] range = BulkFactoryPanelBehaviour.ROW_RANGES[row];
		int lo = range[0];
		int hi = range[1];
		value = Mth.clamp(value, lo, hi); // below-range values (e.g. count 0) sit at the left edge
		if (BulkFactoryPanelBehaviour.ROW_SEGMENTS[row] == 1)
			return (double) (value - lo) / (hi - lo);
		int segments = BulkFactoryPanelBehaviour.ROW_SEGMENTS[row];
		int baseLog2 = BulkFactoryPanelBehaviour.ROW_SEGMENT_BASE_LOG2[row];
		int segment = Mth.clamp(Integer.numberOfTrailingZeros(Integer.highestOneBit(value)) - baseLog2, 0, segments - 1);
		long segStart = 1L << (baseLog2 + segment);
		long segEnd = 1L << (baseLog2 + segment + 1);
		double t = (double) (value - segStart) / (segEnd - segStart);
		return (segment + t) / segments;
	}

	/** Value at a normalised position, rounded to an integer and clamped to the row range. */
	private static int valueAt(int row, double fraction) {
		int[] range = BulkFactoryPanelBehaviour.ROW_RANGES[row];
		int lo = range[0];
		int hi = range[1];
		double snap = SNAP_PIXELS / VALUE_BAR_WIDTH;

		if (BulkFactoryPanelBehaviour.ROW_SEGMENTS[row] == 1) {
			// linear row: snap to both ends (0 and the row maximum)
			if (fraction <= snap)
				return lo;
			if (fraction >= 1 - snap)
				return hi;
			return (int) Math.round(lo + fraction * (hi - lo));
		}

		int segments = BulkFactoryPanelBehaviour.ROW_SEGMENTS[row];
		int baseLog2 = BulkFactoryPanelBehaviour.ROW_SEGMENT_BASE_LOG2[row];

		// snap to power-of-two segment boundaries (2^baseLog2 .. 2^(baseLog2+segments))
		for (int boundary = 0; boundary <= segments; boundary++) {
			double boundaryFrac = (double) boundary / segments;
			if (Math.abs(fraction - boundaryFrac) <= snap)
				return (int) (1L << (baseLog2 + boundary));
		}

		int segment = Mth.clamp((int) Math.floor(fraction * segments), 0, segments - 1);
		double t = Mth.clamp(fraction * segments - segment, 0, 1);
		long segStart = 1L << (baseLog2 + segment);
		long segEnd = 1L << (baseLog2 + segment + 1);
		long value = Math.round(segStart + t * (segEnd - segStart));
		return (int) Mth.clamp(value, lo, hi);
	}

	@Override
	protected void init() {
		maxLabelWidth = 0;
		for (Component component : board.rows())
			maxLabelWidth = Math.max(maxLabelWidth, font.width(component));

		int width = (maxLabelWidth + 14) + (VALUE_BAR_WIDTH + 10);
		int height = board.rows()
			.size() * 11;

		setWindowSize(width, height);
		super.init();

		Vec2 coordinateOfValue = getCoordinateOfValue(initialSettings.row(), initialSettings.value());
		setCursor(coordinateOfValue);
	}

	private void setCursor(Vec2 coordinateOfValue) {
		double guiScale = minecraft.getWindow()
			.getGuiScale();
		GLFW.glfwSetCursorPos(minecraft.getWindow()
			.getWindow(), coordinateOfValue.x * guiScale, coordinateOfValue.y * guiScale);
	}

	/** X position where the value bar starts, shared by hit-testing and rendering. */
	private double getBarStart() {
		return guiLeft + maxLabelWidth + 14 + 4 + 1;
	}

	private ValueSettings getClosestCoordinate(int mouseX, int mouseY) {
		int rows = board.rows()
			.size();
		int row = 0;
		double bestDiff = Double.MAX_VALUE;
		for (int r = 0; r < rows; r++) {
			double diff = Math.abs((guiTop + (r + .5f) * 11 - .5f) - mouseY);
			if (diff < bestDiff) {
				bestDiff = diff;
				row = r;
			}
		}

		double barStart = getBarStart();
		double fraction = Mth.clamp((mouseX - barStart) / VALUE_BAR_WIDTH, 0, 1);
		int value = valueAt(row, fraction);
		return new ValueSettings(row, value);
	}

	private Vec2 getCoordinateOfValue(int row, int value) {
		double barStart = getBarStart();
		float xOut = (float) (barStart + fractionOf(row, value) * VALUE_BAR_WIDTH);
		float yOut = guiTop + (row + .5f) * 11 - .5f;
		return new Vec2(xOut, yOut);
	}

	@Override
	protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
		int x = guiLeft;
		int y = guiTop;
		int rows = board.rows()
			.size();

		Component title = board.title();
		Component tip = CreateLang.translateDirect("gui.value_settings.release_to_confirm", Component.keybind("key.use"));
		double fadeIn = Math.pow(Mth.clamp((ticksOpen + partialTicks) / 4.0, 0, 1), 1);

		int fattestLabel = Math.max(font.width(tip), font.width(title));

		int fatTipOffset = Math.max(0, fattestLabel + 10 - (windowWidth + 13)) / 2;
		int bgWidth = Math.max((windowWidth + 13), fattestLabel + 10);
		int fadeInWidth = (int) (bgWidth * fadeIn);
		int fadeInStart = (bgWidth - fadeInWidth) / 2 - fatTipOffset;
		int additionalHeight = 33;

		int zLevel = 0;
		UIRenderHelper.drawStretched(graphics, x - 11 + fadeInStart, y - 17, fadeInWidth,
			windowHeight + additionalHeight, zLevel, AllGuiTextures.VALUE_SETTINGS_OUTER_BG);
		UIRenderHelper.drawStretched(graphics, x - 10 + fadeInStart, y - 18, fadeInWidth - 2, 1, zLevel,
			AllGuiTextures.VALUE_SETTINGS_OUTER_BG);
		UIRenderHelper.drawStretched(graphics, x - 10 + fadeInStart, y - 17 + windowHeight + additionalHeight, zLevel,
			fadeInWidth - 2, 1, AllGuiTextures.VALUE_SETTINGS_OUTER_BG);

		if (fadeInWidth > fattestLabel) {
			int textX = x - 11 - fatTipOffset + bgWidth / 2;
			graphics.drawString(font, title, textX - font.width(title) / 2, y - 14, 0xdddddd, false);
			graphics.drawString(font, tip, textX - font.width(tip) / 2, y + windowHeight + additionalHeight - 27,
				0xdddddd, false);
		}

		// brass frame widened to the window's right edge (window width is
		// maxLabelWidth+14 + W+10, so the frame is W+10 wide)
		renderBrassFrame(graphics, x + maxLabelWidth + 12, y - 3, VALUE_BAR_WIDTH + 17, rows * 11 + 5);
		// bar background exactly fills the brass frame's interior: the frame
		// border is 4px (BRASS_FRAME corner textures), so the interior runs from
		// x+18 with width W+2 - flush on both sides, no gap, no overlap
		UIRenderHelper.drawStretched(graphics, x + maxLabelWidth + 15, y, VALUE_BAR_WIDTH + 11, rows * 11 - 1, zLevel,
			AllGuiTextures.VALUE_SETTINGS_BAR_BG);

		int originalY = y;
		for (int row = 0; row < rows; row++) {
			Component component = board.rows()
				.get(row);
			// bar sits centred on the background, 1px inset on each side
			int valueBarX = (int) getBarStart();

			UIRenderHelper.drawCropped(graphics, x - 4, y, maxLabelWidth + 8, 11, zLevel,
				AllGuiTextures.VALUE_SETTINGS_LABEL_BG);
			for (int w = 0; w < VALUE_BAR_WIDTH; w += AllGuiTextures.VALUE_SETTINGS_BAR.getWidth() - 1)
				UIRenderHelper.drawCropped(graphics, valueBarX + w, y + 1,
					Math.min(AllGuiTextures.VALUE_SETTINGS_BAR.getWidth() - 1, VALUE_BAR_WIDTH - w), 8, zLevel,
					AllGuiTextures.VALUE_SETTINGS_BAR);
			graphics.drawString(font, component, x, y + 1, 0x442000, false);

			// milestones: linear row gets evenly spaced ticks, segmented rows get
			// one tick per power-of-two segment boundary; each tick is centred on
			// its value position so the outermost ones never hang off the bar
			int[] range = BulkFactoryPanelBehaviour.ROW_RANGES[row];
			if (BulkFactoryPanelBehaviour.ROW_SEGMENTS[row] == 1) {
				int step = Math.max(1, (range[1] - range[0]) / 4);
				for (int v = range[0]; v <= range[1]; v += step) {
					int milestoneX = valueBarX + (int) (fractionOf(row, v) * VALUE_BAR_WIDTH) - MILESTONE_SIZE / 2;
					AllGuiTextures.VALUE_SETTINGS_MILESTONE.render(graphics, milestoneX, y + 1);
				}
			} else {
				int segments = BulkFactoryPanelBehaviour.ROW_SEGMENTS[row];
				for (int segment = 0; segment <= segments; segment++) {
					int milestoneX = valueBarX
						+ (int) ((double) segment / segments * VALUE_BAR_WIDTH) - MILESTONE_SIZE / 2;
					AllGuiTextures.VALUE_SETTINGS_MILESTONE.render(graphics, milestoneX, y + 1);
				}
			}

			y += 11;
		}

		renderBrassFrame(graphics, x - 7, originalY - 3, maxLabelWidth + 14, rows * 11 + 5);

		if (ticksOpen < 1)
			return;

		ValueSettings closest = getClosestCoordinate(mouseX, mouseY);

		if (!closest.equals(lastHovered)) {
			onHover.accept(closest);
			if (soundCoolDown == 0) {
				float pitch = (float) fractionOf(closest.row(), closest.value());
				pitch = Mth.lerp(pitch, 1.15f, 1.5f);
				minecraft.getSoundManager()
					.play(SimpleSoundInstance.forUI(AllSoundEvents.SCROLL_VALUE.getMainEvent(), pitch, 0.25F));
				ScrollValueHandler.wrenchCog.bump(3, -(closest.value() - lastHovered.value()) * 10);
				soundCoolDown = 1;
			}
		}
		lastHovered = closest;

		Vec2 coordinate = getCoordinateOfValue(closest.row(), closest.value());
		Component cursorText = board.formatter()
			.format(closest);

		int cursorWidth = (font.width(cursorText) / 2) * 2 + 3;
		// The cursor texture's visual snap point sits 1px left of the coordinate;
		// shift it right so it lines up with the background milestone/tick.
		int cursorX = ((int) (coordinate.x)) - cursorWidth / 2 + 1;
		int cursorY = ((int) (coordinate.y)) - 7;

		AllGuiTextures.VALUE_SETTINGS_CURSOR_LEFT.render(graphics, cursorX - 3, cursorY);
		UIRenderHelper.drawCropped(graphics, cursorX, cursorY, cursorWidth, 14, zLevel,
			AllGuiTextures.VALUE_SETTINGS_CURSOR);
		AllGuiTextures.VALUE_SETTINGS_CURSOR_RIGHT.render(graphics, cursorX + cursorWidth, cursorY);

		graphics.drawString(font, cursorText, cursorX + 2, cursorY + 3, 0x442000, false);
	}

	protected void renderBrassFrame(GuiGraphics graphics, int x, int y, int w, int h) {
		AllGuiTextures.BRASS_FRAME_TL.render(graphics, x, y);
		AllGuiTextures.BRASS_FRAME_TR.render(graphics, x + w - 4, y);
		AllGuiTextures.BRASS_FRAME_BL.render(graphics, x, y + h - 4);
		AllGuiTextures.BRASS_FRAME_BR.render(graphics, x + w - 4, y + h - 4);
		int zLevel = 0;

		if (h > 8) {
			UIRenderHelper.drawStretched(graphics, x, y + 4, 3, h - 8, zLevel, AllGuiTextures.BRASS_FRAME_LEFT);
			UIRenderHelper.drawStretched(graphics, x + w - 3, y + 4, 3, h - 8, zLevel, AllGuiTextures.BRASS_FRAME_RIGHT);
		}

		if (w > 8) {
			UIRenderHelper.drawCropped(graphics, x + 4, y, w - 8, 3, zLevel, AllGuiTextures.BRASS_FRAME_TOP);
			UIRenderHelper.drawCropped(graphics, x + 4, y + h - 3, w - 8, 3, zLevel, AllGuiTextures.BRASS_FRAME_BOTTOM);
		}
	}

	@Override
	public void renderBackground(@NotNull GuiGraphics graphics, int pMouseX, int pMouseY, float pPartialTick) {
		int a = ((int) (0x50 * Math.min(1, (ticksOpen + AnimationTickHolder.getPartialTicks()) / 20f))) << 24;
		graphics.fillGradient(0, 0, this.width, this.height, 0x101010 | a, 0x101010 | a);
	}

	@Override
	public void tick() {
		ticksOpen++;
		if (soundCoolDown > 0)
			soundCoolDown--;
		super.tick();
	}

	@Override
	public boolean mouseScrolled(double pMouseX, double pMouseY, double pScrollX, double pScrollY) {
		ValueSettings closest = getClosestCoordinate((int) pMouseX, (int) pMouseY);
		int[] range = BulkFactoryPanelBehaviour.ROW_RANGES[closest.row()];
		int step = hasShiftDown() ? Math.max(1, (range[1] - range[0]) / 8) : 1;
		int value = Mth.clamp(closest.value() + (int) Math.signum(pScrollY) * step, range[0], range[1]);
		if (value == closest.value())
			return false;
		setCursor(getCoordinateOfValue(closest.row(), value));
		return true;
	}

	@Override
	public boolean keyReleased(int pKeyCode, int pScanCode, int pModifiers) {
		if (minecraft.options.keyUse.matches(pKeyCode, pScanCode)) {
			Window window = minecraft.getWindow();
			double x = minecraft.mouseHandler.xpos() * window.getGuiScaledWidth() / window.getScreenWidth();
			double y = minecraft.mouseHandler.ypos() * window.getGuiScaledHeight() / window.getScreenHeight();
			saveAndClose(x, y);
			return true;
		}
		return super.keyReleased(pKeyCode, pScanCode, pModifiers);
	}

	@Override
	public boolean mouseReleased(double pMouseX, double pMouseY, int pButton) {
		if (minecraft.options.keyUse.matchesMouse(pButton)) {
			saveAndClose(pMouseX, pMouseY);
			return true;
		}
		return super.mouseReleased(pMouseX, pMouseY, pButton);
	}

	protected void saveAndClose(double pMouseX, double pMouseY) {
		ValueSettings closest = getClosestCoordinate((int) pMouseX, (int) pMouseY);
		CatnipServices.NETWORK.sendToServer(new com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsPacket(
			pos, closest.row(), closest.value(), null, null, net.minecraft.core.Direction.UP, AllKeys.ctrlDown(), netId));
		onClose();
	}
}
