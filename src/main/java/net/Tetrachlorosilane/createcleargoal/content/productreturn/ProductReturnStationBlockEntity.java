package net.Tetrachlorosilane.createcleargoal.content.productreturn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.mutable.MutableBoolean;
import org.jetbrains.annotations.Nullable;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packager.PackagingRequest;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Block entity for the Product Return Station.
 * <p>
 * The station keeps its own independent promise queue ({@link ProductReturnQueue}).
 * It is populated only after the station is placed (and only for the configured
 * input address); changing the input address clears the queue and counts as a
 * re-placement. The station's sends do NOT modify Create's original promise queue.
 */
public class ProductReturnStationBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {

	public LogisticallyLinkedBehaviour behaviour;
	public String inputAddress = "";
	public String outputAddress = "";

	/**
	 * How long promises may live before being cleared, in minutes.
	 * -1 = never expire, 0 = 30 seconds, otherwise N minutes (factory-gauge style).
	 */
	public int promiseClearingInterval = -1;

	/** Last total promised count sent to the client for the GUI indicator. */
	public int lastReportedPromises;

	/** True when another station with the same network+input address has the same redstone priority. */
	public boolean conflict;

	/** Redstone signal strength; higher = lower priority (Stock Link style). */
	public int redstonePower;

	/** Whether the client should receive a promise-count sync on the next lazy tick. */
	boolean syncPromises;

	/** Total amount already packaged but not yet considered fully completed. */
	int pendingCount;

	/** Whether a package created by this station is still waiting to be pulled out. */
	boolean awaitingPackage;

	/** Independent, per-station promise queue. */
	public final ProductReturnQueue queue = new ProductReturnQueue();

	public ProductReturnStationBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		setLazyTickRate(20);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		behaviours.add(behaviour = new LogisticallyLinkedBehaviour(this, false));
	}

	@Override
	public void initialize() {
		super.initialize();
		if (!level.isClientSide) {
			ProductReturnStationManager.register(this);
			PackagerBlockEntity packager = getPackager();
			if (packager != null)
				packager.recheckIfLinksPresent();
		}
	}

	@Override
	public void invalidate() {
		super.invalidate();
		if (!level.isClientSide)
			ProductReturnStationManager.unregister(this);
		// Do not touch the attached packager here: invalidate also fires on chunk
		// unload, and mutating the packager state during save/unload can keep the
		// world from finishing its save. Actual block removal is handled by
		// ProductReturnStationBlock.onRemove.
	}

	@Nullable
	public PackagerBlockEntity getPackager() {
		BlockState state = getBlockState();
		if (!(state.getBlock() instanceof ProductReturnStationBlock))
			return null;
		BlockPos attached = worldPosition.relative(ProductReturnStationBlock.getConnectedDirection(state)
			.getOpposite());
		if (level.getBlockEntity(attached) instanceof PackagerBlockEntity packager)
			return packager;
		return null;
	}

	public void setRedstonePower(int power) {
		if (redstonePower == power)
			return;
		redstonePower = power;
		setChanged();
		notifyUpdate();
		if (!level.isClientSide)
			ProductReturnStationManager.onRedstonePowerChanged(this);
	}

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		if (!conflict)
			return false;
		tooltip.add(Component.literal("    ")
			.append(Component.translatable("createcleargoal.product_return_station.conflict_warning")
				.withStyle(ChatFormatting.YELLOW)));
		return true;
	}

	private int getPromiseExpiryTimeInTicks() {
		if (promiseClearingInterval == -1)
			return -1;
		if (promiseClearingInterval == 0)
			return 20 * 30;
		return promiseClearingInterval * 20 * 60;
	}

	/** Number of package slots needed to hold {@code count} of an item. */
	private static int slotsFor(int count, int maxStackSize) {
		return (count + maxStackSize - 1) / maxStackSize;
	}

	// --- independent queue API (called from the manager) ---

	public void addPromise(ItemStack item, int count, String outputAddress) {
		queue.add(item, count, outputAddress == null ? "" : outputAddress);
		lastReportedPromises = queue.getTotalCount() + pendingCount;
		syncPromises = false;
		setChanged();
		notifyUpdate();
	}

	public void onPromiseCancelled(ItemStack item) {
		if (queue.getTotal(item) > 0) {
			queue.clear(item);
			lastReportedPromises = queue.getTotalCount() + pendingCount;
			syncPromises = false;
			setChanged();
			notifyUpdate();
		}
	}

	@Override
	public void lazyTick() {
		super.lazyTick();
		if (level.isClientSide)
			return;

		PackagerBlockEntity packager = getPackager();

		// The promise batch is only fully completed once the package we created has
		// actually been pulled out of the packager (heldBox becomes empty again).
		// If the packager is not loaded yet, keep waiting instead of clearing early.
		if (awaitingPackage && packager != null && packager.heldBox.isEmpty()) {
			awaitingPackage = false;
			pendingCount = 0;
			lastReportedPromises = queue.getTotalCount();
			syncPromises = true;
		}

		// Age promises once per lazy tick (20 game ticks) and clear any that have
		// exceeded the configured lifetime. This keeps per-tick cost low and avoids
		// marking the chunk dirty every game tick.
		queue.tick(lazyTickRate);
		if (queue.removeExpired(getPromiseExpiryTimeInTicks())) {
			lastReportedPromises = queue.getTotalCount() + pendingCount;
			syncPromises = true;
		}

		// Throttle promise-count syncs caused by expiry to at most once per lazy tick.
		if (syncPromises) {
			syncPromises = false;
			notifyUpdate();
		}

		if (behaviour == null || behaviour.freqId == null)
			return;
		if (inputAddress.isBlank() || outputAddress.isBlank())
			return;

		if (packager == null)
			return;
		if (queue.isEmpty())
			return;

		// Never send while the packager is still working on another package. If we
		// did, the new box would only sit in queuedExitingPackages and the promise
		// would be marked complete before the package is actually output.
		if (!packager.heldBox.isEmpty() || !packager.queuedExitingPackages.isEmpty() || packager.animationTicks != 0)
			return;

		// Build a single package from as many queued promises as fit. Multiple
		// item types / promises are batched together, but never beyond one
		// package's capacity (soft-coded via PackageItem.SLOTS and item stack size).
		// Promises with different resolved output addresses are processed in
		// separate lazy ticks (one package per output address).
		InventorySummary available = packager.getAvailableItems();
		List<AddressPromise> promises = queue.flatten();

		// Pick the first output address that currently has enough stock, so one
		// unavailable output cannot starve other outputs forever.
		String targetOutput = null;
		for (AddressPromise promise : promises) {
			if (available.getCountOf(promise.item()) >= promise.count()) {
				targetOutput = promise.outputAddress();
				break;
			}
		}
		if (targetOutput == null)
			return;

		Map<ItemKey, Integer> batchCounts = new HashMap<>();
		int slotsUsed = 0;
		for (AddressPromise promise : promises) {
			if (!targetOutput.equals(promise.outputAddress()))
				continue;
			ItemStack item = promise.item();
			ItemKey key = new ItemKey(item);
			int current = batchCounts.getOrDefault(key, 0);
			int newCount = current + promise.count();
			int oldSlots = slotsFor(current, item.getMaxStackSize());
			int newSlots = slotsFor(newCount, item.getMaxStackSize());
			if (slotsUsed + (newSlots - oldSlots) > PackageItem.SLOTS)
				break;
			if (available.getCountOf(item) < newCount)
				continue;
			batchCounts.put(key, newCount);
			slotsUsed += newSlots - oldSlots;
		}

		if (batchCounts.isEmpty())
			return;

		List<PackagingRequest> requests = new ArrayList<>();
		Map<ItemKey, Integer> originalCounts = new HashMap<>();
		for (Map.Entry<ItemKey, Integer> entry : batchCounts.entrySet()) {
			ItemStack item = entry.getKey()
				.stack();
			int count = entry.getValue();
			originalCounts.put(entry.getKey(), count);
			requests.add(PackagingRequest.create(item, count, targetOutput, 0, new MutableBoolean(true), 0, 0, null));
		}

		// attemptToSend mutates the request list (removes fulfilled entries), so
		// keep a separate list to inspect every request afterwards.
		List<PackagingRequest> sentRequests = new ArrayList<>(requests);
		packager.attemptToSend(requests);

		int totalPackaged = 0;
		for (PackagingRequest request : sentRequests) {
			ItemKey key = new ItemKey(request.item());
			int original = originalCounts.getOrDefault(key, 0);
			int packagedAmount = original - request.getCount();
			if (packagedAmount <= 0)
				continue;
			// Only the station's own queue is reduced; Create's original promise
			// system is intentionally left untouched here.
			queue.remove(request.item(), packagedAmount);
			totalPackaged += packagedAmount;
		}

		if (totalPackaged <= 0)
			return;

		// Keep the packaged amount visible in the promise count until the package
		// has actually been pulled out of the packager.
		pendingCount += totalPackaged;
		awaitingPackage = true;
		lastReportedPromises = queue.getTotalCount() + pendingCount;
		syncPromises = false;
		setChanged();
		notifyUpdate();
	}

	@Override
	protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.write(tag, registries, clientPacket);
		tag.putString("InputAddress", inputAddress);
		tag.putString("OutputAddress", outputAddress);
		tag.putBoolean("Conflict", conflict);
		tag.putInt("RedstonePower", redstonePower);
		tag.putInt("PromiseClearingInterval", promiseClearingInterval);
		if (clientPacket)
			tag.putInt("TotalPromised", lastReportedPromises);
		if (!clientPacket) {
			tag.putInt("PendingCount", pendingCount);
			tag.putBoolean("AwaitingPackage", awaitingPackage);
			ListTag list = new ListTag();
			for (AddressPromise promise : queue.flatten()) {
				CompoundTag entry = new CompoundTag();
				entry.put("Item", promise.item().saveOptional(registries));
				entry.putInt("Count", promise.count());
				entry.putString("OutputAddress", promise.outputAddress());
				entry.putInt("TicksExisted", promise.ticksExisted());
				list.add(entry);
			}
			tag.put("ReturnPromises", list);
		}
	}

	@Override
	protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
		super.read(tag, registries, clientPacket);
		inputAddress = tag.getString("InputAddress");
		outputAddress = tag.getString("OutputAddress");
		conflict = tag.getBoolean("Conflict");
		redstonePower = tag.getInt("RedstonePower");
		promiseClearingInterval = tag.contains("PromiseClearingInterval")
			? Math.max(-1, Math.min(31, tag.getInt("PromiseClearingInterval")))
			: -1;
		if (clientPacket) {
			lastReportedPromises = tag.getInt("TotalPromised");
		} else {
			List<AddressPromise> saved = new ArrayList<>();
			ListTag list = tag.getList("ReturnPromises", Tag.TAG_COMPOUND);
			for (int i = 0; i < list.size(); i++) {
				CompoundTag entry = list.getCompound(i);
				ItemStack item = ItemStack.parseOptional(registries, entry.getCompound("Item"));
				if (!item.isEmpty())
					saved.add(new AddressPromise(item, entry.getInt("Count"), entry.getString("OutputAddress"),
						entry.getInt("TicksExisted")));
			}
			queue.load(saved);
			pendingCount = Math.max(0, tag.getInt("PendingCount"));
			awaitingPackage = pendingCount > 0 && tag.getBoolean("AwaitingPackage");
			lastReportedPromises = queue.getTotalCount() + pendingCount;
		}
	}
}
