package net.Tetrachlorosilane.createcleargoal.content.productreturn;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import com.simibubi.create.content.logistics.packagerLink.RequestPromiseQueue;

import net.minecraft.core.GlobalPos;
import net.minecraft.world.item.ItemStack;

/**
 * Server-side registry of active Product Return Stations and the bridge between
 * Create's original promise system and the stations' independent queues.
 * <p>
 * Cross-influence is limited to:
 * <ul>
 *   <li>factory gauge promise creation -> add to matching station queues</li>
 *   <li>promise completion / cancellation -> remove from station queues if present</li>
 * </ul>
 * The station's own sends do NOT modify Create's original promise queue.
 */
public final class ProductReturnStationManager {

	private static final Map<GlobalPos, ProductReturnStationBlockEntity> STATIONS = new HashMap<>();
	private static final Map<UUID, Map<String, Set<GlobalPos>>> BY_NETWORK_ADDRESS = new HashMap<>();
	private static final WeakHashMap<RequestPromiseQueue, UUID> QUEUE_NETWORKS = new WeakHashMap<>();

	private ProductReturnStationManager() {
	}

	public static void register(ProductReturnStationBlockEntity be) {
		GlobalPos pos = GlobalPos.of(be.getLevel().dimension(), be.getBlockPos());
		STATIONS.put(pos, be);
		if (be.behaviour != null && be.behaviour.freqId != null && !be.inputAddress.isBlank())
			BY_NETWORK_ADDRESS.computeIfAbsent(be.behaviour.freqId, $ -> new HashMap<>())
				.computeIfAbsent(be.inputAddress, $ -> new HashSet<>())
				.add(pos);
	}

	public static void unregister(ProductReturnStationBlockEntity be) {
		GlobalPos pos = GlobalPos.of(be.getLevel().dimension(), be.getBlockPos());
		STATIONS.remove(pos);
		if (be.behaviour != null && be.behaviour.freqId != null) {
			Map<String, Set<GlobalPos>> byAddress = BY_NETWORK_ADDRESS.get(be.behaviour.freqId);
			if (byAddress != null) {
				for (Set<GlobalPos> set : byAddress.values())
					set.remove(pos);
				byAddress.entrySet()
					.removeIf(e -> e.getValue()
						.isEmpty());
				if (byAddress.isEmpty())
					BY_NETWORK_ADDRESS.remove(be.behaviour.freqId);
			}
		}
	}

	public static void onAddressChanged(ProductReturnStationBlockEntity be, String oldAddress) {
		GlobalPos pos = GlobalPos.of(be.getLevel().dimension(), be.getBlockPos());
		STATIONS.remove(pos);
		if (be.behaviour != null && be.behaviour.freqId != null)
			removeFromAddressMap(be.behaviour.freqId, oldAddress, pos);
		be.queue.clearAll();
		be.pendingCount = 0;
		be.awaitingPackage = false;
		be.lastReportedPromises = 0;
		be.syncPromises = false;
		be.setChanged();
		be.notifyUpdate();
		register(be);
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

	/** Called when a factory gauge adds a promise to Create's network queue. */
	public static void addPromise(UUID network, String address, ItemStack item, int count) {
		Map<String, Set<GlobalPos>> byAddress = BY_NETWORK_ADDRESS.get(network);
		if (byAddress == null)
			return;
		Set<GlobalPos> positions = byAddress.get(address);
		if (positions == null)
			return;
		for (GlobalPos pos : positions) {
			ProductReturnStationBlockEntity be = STATIONS.get(pos);
			if (be != null)
				be.addPromise(item, count);
		}
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

	public static Set<GlobalPos> getStations(UUID network) {
		Map<String, Set<GlobalPos>> byAddress = BY_NETWORK_ADDRESS.get(network);
		if (byAddress == null)
			return Collections.emptySet();
		Set<GlobalPos> result = new HashSet<>();
		for (Set<GlobalPos> set : byAddress.values())
			result.addAll(set);
		return result;
	}
}
