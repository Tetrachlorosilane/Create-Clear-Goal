package net.Tetrachlorosilane.createcleargoal.content.productreturn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import com.simibubi.create.content.logistics.packagerLink.RequestPromiseQueue;

import net.minecraft.core.GlobalPos;
import net.minecraft.world.item.ItemStack;

/**
 * Server-side registry of Product Return Stations and the bridge between
 * Create's original promise system and the stations' independent queues.
 * <p>
 * Multiple stations may listen to the same network + input address. When a
 * promise arrives, the station with the highest priority (lowest redstone
 * power, like Create's Stock Link) is chosen. If several stations have the
 * same priority, the choice is undefined; this is surfaced as a conflict
 * warning in the GUI and goggle overlay.
 */
public final class ProductReturnStationManager {

	/** Maximum length of the raw Java regex used as the input address. */
	public static final int MAX_INPUT_ADDRESS_LENGTH = 128;
	/** Maximum length of the output template (before capture-group expansion). */
	public static final int MAX_OUTPUT_TEMPLATE_LENGTH = 128;

	private static final Map<GlobalPos, ProductReturnStationBlockEntity> STATIONS = new HashMap<>();
	private static final Map<UUID, Map<String, Set<GlobalPos>>> BY_NETWORK_ADDRESS = new HashMap<>();
	private static final WeakHashMap<RequestPromiseQueue, UUID> QUEUE_NETWORKS = new WeakHashMap<>();



	private ProductReturnStationManager() {
	}

	public static void register(ProductReturnStationBlockEntity be) {
		GlobalPos pos = GlobalPos.of(be.getLevel().dimension(), be.getBlockPos());
		STATIONS.put(pos, be);
		if (be.behaviour == null || be.behaviour.freqId == null || be.inputAddress.isBlank()) {
			setConflict(be, false);
			return;
		}
		// Invalid regexes are not routable; the player must fix the config.
		if (be.invalidRegex || be.addressRule == null) {
			setConflict(be, false);
			return;
		}
		UUID network = be.behaviour.freqId;
		String address = be.inputAddress;
		BY_NETWORK_ADDRESS.computeIfAbsent(network, $ -> new HashMap<>())
			.computeIfAbsent(address, $ -> new HashSet<>())
			.add(pos);
		recomputeConflict(network, address);
	}

	public static void unregister(ProductReturnStationBlockEntity be) {
		GlobalPos pos = GlobalPos.of(be.getLevel().dimension(), be.getBlockPos());
		STATIONS.remove(pos);
		if (be.behaviour == null || be.behaviour.freqId == null || be.inputAddress.isBlank())
			return;
		UUID network = be.behaviour.freqId;
		String address = be.inputAddress;
		removeFromAddressMap(network, address, pos);
		recomputeConflict(network, address);
	}

	public static void onAddressChanged(ProductReturnStationBlockEntity be, String oldAddress) {
		be.rebuildAddressRule();
		GlobalPos pos = GlobalPos.of(be.getLevel().dimension(), be.getBlockPos());
		STATIONS.remove(pos);
		if (be.behaviour != null && be.behaviour.freqId != null) {
			removeFromAddressMap(be.behaviour.freqId, oldAddress, pos);
			recomputeConflict(be.behaviour.freqId, oldAddress);
		}
		be.queue.clearAll();
		be.clearPendingRemovals();
		be.pendingCount = 0;
		be.awaitingPackage = false;
		be.lastReportedPromises = 0;
		be.syncPromises = false;
		STATIONS.put(pos, be);
		if (be.behaviour != null && be.behaviour.freqId != null && !be.inputAddress.isBlank()
			&& !be.invalidRegex && be.addressRule != null) {
			UUID network = be.behaviour.freqId;
			String address = be.inputAddress;
			BY_NETWORK_ADDRESS.computeIfAbsent(network, $ -> new HashMap<>())
				.computeIfAbsent(address, $ -> new HashSet<>())
				.add(pos);
			recomputeConflict(network, address);
		} else {
			setConflict(be, false);
		}
		be.setChanged();
		be.notifyUpdate();
	}

	public static void onRedstonePowerChanged(ProductReturnStationBlockEntity be) {
		if (be.behaviour == null || be.behaviour.freqId == null || be.inputAddress.isBlank()) {
			setConflict(be, false);
			return;
		}
		recomputeConflict(be.behaviour.freqId, be.inputAddress);
	}

	private static void setConflict(ProductReturnStationBlockEntity be, boolean conflict) {
		if (be.conflict == conflict)
			return;
		be.conflict = conflict;
		be.setChanged();
		be.notifyUpdate();
	}

	private static void recomputeConflict(UUID network, String address) {
		Map<String, Set<GlobalPos>> byAddress = BY_NETWORK_ADDRESS.get(network);
		if (byAddress == null)
			return;
		Set<GlobalPos> positions = byAddress.get(address);
		if (positions == null)
			return;
		Map<Integer, Integer> powerCounts = new HashMap<>();
		for (GlobalPos pos : positions) {
			ProductReturnStationBlockEntity be = STATIONS.get(pos);
			if (be == null)
				continue;
			powerCounts.merge(be.redstonePower, 1, Integer::sum);
		}
		for (GlobalPos pos : positions) {
			ProductReturnStationBlockEntity be = STATIONS.get(pos);
			if (be == null)
				continue;
			boolean conflict = powerCounts.getOrDefault(be.redstonePower, 0) > 1;
			setConflict(be, conflict);
		}
	}

	private static void removeFromAddressMap(UUID network, String address, GlobalPos pos) {
		Map<String, Set<GlobalPos>> byAddress = BY_NETWORK_ADDRESS.get(network);
		if (byAddress == null)
			return;
		Set<GlobalPos> set = byAddress.get(address);
		if (set != null) {
			set.remove(pos);
			if (set.isEmpty())
				byAddress.remove(address);
		}
		if (byAddress.isEmpty())
			BY_NETWORK_ADDRESS.remove(network);
	}

	private record Candidate(ProductReturnStationBlockEntity station, String resolvedOutput) {
	}

	/** Called when a factory gauge adds a promise to Create's network queue. */
	public static void addPromise(UUID network, String address, ItemStack item, int count) {
		Map<String, Set<GlobalPos>> byAddress = BY_NETWORK_ADDRESS.get(network);
		if (byAddress == null)
			return;
		String matchAddress = address == null ? "" : address;
		List<Candidate> candidates = new ArrayList<>();
		for (Set<GlobalPos> positions : byAddress.values()) {
			for (GlobalPos pos : positions) {
				ProductReturnStationBlockEntity be = STATIONS.get(pos);
				if (be == null || be.invalidRegex || be.addressRule == null)
					continue;
				be.addressRule.resolve(matchAddress)
					.ifPresent(output -> candidates.add(new Candidate(be, output)));
			}
		}
		if (candidates.isEmpty())
			return;

		// Highest priority = lowest redstone power. If tied, the choice is
		// undefined (first candidate wins); the conflict warning tells the player.
		// The resolved output address was already computed once and is reused here.
		Candidate best = candidates.get(0);
		for (int i = 1; i < candidates.size(); i++) {
			Candidate candidate = candidates.get(i);
			if (candidate.station().redstonePower < best.station().redstonePower)
				best = candidate;
		}
		best.station().addPromise(item, count, best.resolvedOutput());
	}

	/** Called when Create's original promise is manually cleared. */
	public static void onPromiseCancelled(UUID network, ItemStack item) {
		Map<String, Set<GlobalPos>> byAddress = BY_NETWORK_ADDRESS.get(network);
		if (byAddress == null)
			return;
		for (Set<GlobalPos> positions : byAddress.values()) {
			for (GlobalPos pos : positions) {
				ProductReturnStationBlockEntity be = STATIONS.get(pos);
				if (be != null)
					be.onPromiseCancelled(item);
			}
		}
	}

	public static void bindQueue(UUID network, RequestPromiseQueue queue) {
		if (network != null && queue != null)
			QUEUE_NETWORKS.put(queue, network);
	}

	public static UUID getNetwork(RequestPromiseQueue queue) {
		return QUEUE_NETWORKS.get(queue);
	}
}
