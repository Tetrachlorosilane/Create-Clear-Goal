package net.Tetrachlorosilane.createcleargoal.content.bulkgauge;

import java.util.List;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelConnection;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelSupportBehaviour;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBehaviour.ValueSettings;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import com.simibubi.create.foundation.utility.CreateLang;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Behaviour for the bulk factory gauge. Identical to Create's
 * {@link FactoryPanelBehaviour} (storage monitoring, packager restocking,
 * network connections, redstone output, item icon / status bulb rendering)
 * except for the target amount UI: the value settings board carries four
 * slider rows instead of two, all in stacks - 0-32 (linear), 32-256 (split at
 * 64 and 128), 256-2048 (split at 512 and 1024) and 2048-16384 (split at 4096
 * and 8192), with linear interpolation inside each segment.
 * <p>
 * The stored state is unchanged: {@code count} holds the raw target in stacks
 * ({@code upTo} stays false, so demand = count * maxStackSize) and the row is
 * derived on read.
 * <p>
 * Note: relocation ({@code moveTo}) is intentionally inherited from Create.
 * {@code FactoryPanelBehaviour.moveTo} hardcodes the vanilla gauge block and
 * behaviour, but {@code FactoryPanelBehaviourMixin} redirects those two spots
 * so the bulk variant works; that mixin lives outside this package's reach
 * (no reverse reference from regular code into the mixin package).
 */
public class BulkFactoryPanelBehaviour extends FactoryPanelBehaviour {

	public static final BehaviourType<BulkFactoryPanelBehaviour> TOP_LEFT = new BehaviourType<>();
	public static final BehaviourType<BulkFactoryPanelBehaviour> TOP_RIGHT = new BehaviourType<>();
	public static final BehaviourType<BulkFactoryPanelBehaviour> BOTTOM_LEFT = new BehaviourType<>();
	public static final BehaviourType<BulkFactoryPanelBehaviour> BOTTOM_RIGHT = new BehaviourType<>();

	/** Row 0: 0..32 stacks, linear scale. */
	private static final int ROW_0 = 0;
	/** Row 1: 32..256 stacks, split at 64 and 128. */
	private static final int ROW_1 = 1;
	/** Row 2: 256..2048 stacks, split at 512 and 1024. */
	private static final int ROW_2 = 2;
	/** Row 3: 2048..16384 stacks, split at 4096 and 8192. */
	private static final int ROW_3 = 3;

	private static final int ROW_0_MAX = 32;
	private static final int ROW_1_MAX = 256;
	private static final int ROW_2_MAX = 2048;
	private static final int ROW_3_MAX = 16384;

	/**
	 * Slider geometry, shared with {@link BulkValueSettingsScreen} (single source
	 * of truth, no duplicated constants). Row 0 is linear; rows 1-3 are split at
	 * power-of-two segment boundaries ({@code 2^log2Base} up to {@code 2^(log2Base+segments)})
	 * with linear interpolation inside each segment.
	 */
	public static final int[][] ROW_RANGES = { { 0, ROW_0_MAX }, { ROW_0_MAX, ROW_1_MAX },
		{ ROW_1_MAX, ROW_2_MAX }, { ROW_2_MAX, ROW_3_MAX } };
	public static final int[] ROW_SEGMENT_BASE_LOG2 = { 0, 5, 8, 11 }; // 2^5=32, 2^8=256, 2^11=2048
	public static final int[] ROW_SEGMENTS = { 1, 3, 3, 3 };

	public BulkFactoryPanelBehaviour(FactoryPanelBlockEntity be, FactoryPanelBlock.PanelSlot slot) {
		super(be, slot);
	}

	@Override
	public BehaviourType<?> getType() {
		return getTypeForSlot(slot);
	}

	public static BehaviourType<?> getTypeForSlot(FactoryPanelBlock.PanelSlot slot) {
		return switch (slot) {
			case BOTTOM_LEFT -> BOTTOM_LEFT;
			case TOP_LEFT -> TOP_LEFT;
			case TOP_RIGHT -> TOP_RIGHT;
			case BOTTOM_RIGHT -> BOTTOM_RIGHT;
		};
	}

	/** Whether the given behaviour type belongs to a bulk gauge panel slot. */
	public static boolean isBulkType(BehaviourType<?> type) {
		return type == TOP_LEFT || type == TOP_RIGHT || type == BOTTOM_LEFT || type == BOTTOM_RIGHT;
	}

	// --- 4-row target amount board ---

	@Override
	public ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult) {
		return new ValueSettingsBoard(
			CreateLang.translate("createcleargoal.bulk_factory_gauge.target_amount").component(),
			ROW_3_MAX, 1,
			List.of(
				CreateLang.translate("schedule.condition.threshold.stacks").component(),
				CreateLang.translate("schedule.condition.threshold.stacks").component(),
				CreateLang.translate("schedule.condition.threshold.stacks").component(),
				CreateLang.translate("schedule.condition.threshold.stacks").component()),
			new ValueSettingsFormatter(this::formatValue));
	}

	@Override
	public ValueSettings getValueSettings() {
		if (count <= ROW_0_MAX)
			return new ValueSettings(ROW_0, Math.max(0, count));
		if (count <= ROW_1_MAX)
			return new ValueSettings(ROW_1, count);
		if (count <= ROW_2_MAX)
			return new ValueSettings(ROW_2, count);
		return new ValueSettings(ROW_3, Math.min(count, ROW_3_MAX));
	}

	@Override
	public void setValueSettings(Player player, ValueSettings settings, boolean ctrlDown) {
		if (getValueSettings().equals(settings) && count == settings.value())
			return;
		// All four rows are stacks (unlike the vanilla gauge, which uses row 0 for
		// items); upTo stays false so demand is count * maxStackSize. Clamp on the
		// server as well so a forged packet cannot set an absurd target.
		count = Math.max(0, Math.min(ROW_3_MAX, settings.value()));
		upTo = false;
		panelBE().redraw = true;
		blockEntity.setChanged();
		blockEntity.sendData();
		playFeedbackSound(this);
		resetTimerSlightly();
		if (!getWorld().isClientSide)
			notifyRedstoneLinks();
	}

	/**
	 * Create's {@code FactoryPanelBehaviour.notifyRedstoneOutputs} is declared
	 * private, so this replicates it through the public connection/link API to
	 * let redstone links learn about the new target amount.
	 */
	private void notifyRedstoneLinks() {
		for (FactoryPanelConnection connection : targetedByLinks.values()) {
			if (!getWorld().isLoaded(connection.from.pos()))
				return;
			FactoryPanelSupportBehaviour linkAt = linkAt(getWorld(), connection);
			if (linkAt == null || linkAt.isOutput())
				return;
			linkAt.notifyLink();
		}
	}

	@Override
	public int getAmount() {
		// Defensive cap: the slider only goes up to ROW_3_MAX, but old saves or a
		// forged packet could leave a larger count in NBT. Keep demand sane.
		return Math.max(0, Math.min(count, ROW_3_MAX));
	}

	@Override
	public MutableComponent formatValue(ValueSettings value) {
		if (value.value() == 0)
			return CreateLang.translateDirect("gui.factory_panel.inactive");
		return Component.literal(value.value() + "\u25A4");
	}

	@Override
	public ItemRequirement getRequiredItems() {
		return isActive()
			? new ItemRequirement(ItemRequirement.ItemUseType.CONSUME, AllBulkGauge.BULK_FACTORY_PANEL.get().asItem())
			: ItemRequirement.NONE;
	}
}
