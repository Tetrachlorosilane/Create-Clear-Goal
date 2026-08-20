package net.Tetrachlorosilane.createcleargoal.content.productreturn;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import net.minecraft.world.item.ItemStack;

/**
 * Per-station promise queue for the Product Return Station.
 * <p>
 * Promises are kept independently from Create's {@code RequestPromiseQueue}.
 * They are grouped by product type, and within each product type the queue is
 * ordered by promise count ascending (smallest first), so cross operations can
 * always act on the top of the queue as requested. Each promise also tracks its
 * age so expired entries can be cleared, mirroring Create's factory gauge.
 */
public class ProductReturnQueue {

	private final Map<ItemKey, PriorityQueue<AddressPromise>> queues = new HashMap<>();

	private static final Comparator<AddressPromise> BY_COUNT_ASC =
		Comparator.comparingInt(AddressPromise::count);

	public void add(ItemStack item, int count) {
		add(new AddressPromise(item.copy(), count));
	}

	public void add(ItemStack item, int count, String outputAddress) {
		add(new AddressPromise(item.copy(), count, outputAddress));
	}

	public void add(ItemStack item, int count, String outputAddress, int ticksExisted) {
		add(new AddressPromise(item.copy(), count, outputAddress, ticksExisted));
	}

	private void add(AddressPromise promise) {
		if (promise.item().isEmpty() || promise.count() <= 0)
			return;
		queues.computeIfAbsent(new ItemKey(promise.item()), $ -> new PriorityQueue<>(BY_COUNT_ASC))
			.add(promise);
	}

	/**
	 * Removes up to {@code count} from the queue for this item, starting at the
	 * smallest promise and moving to larger ones if the top is insufficient.
	 * Returns how much was actually removed.
	 */
	public int remove(ItemStack item, int count) {
		ItemKey key = new ItemKey(item);
		PriorityQueue<AddressPromise> queue = queues.get(key);
		if (queue == null || count <= 0)
			return 0;
		int remaining = count;
		List<AddressPromise> leftovers = new ArrayList<>();
		while (remaining > 0 && !queue.isEmpty()) {
			AddressPromise promise = queue.poll();
			if (promise.count() <= remaining) {
				remaining -= promise.count();
			} else {
				leftovers.add(new AddressPromise(promise.item(), promise.count() - remaining, promise.outputAddress(),
					promise.ticksExisted()));
				remaining = 0;
			}
		}
		queue.addAll(leftovers);
		if (queue.isEmpty())
			queues.remove(key);
		return count - remaining;
	}

	/** Removes every promise for the given item. */
	public void clear(ItemStack item) {
		queues.remove(new ItemKey(item));
	}

	/** Total outstanding count for the item. */
	public int getTotal(ItemStack item) {
		PriorityQueue<AddressPromise> queue = queues.get(new ItemKey(item));
		if (queue == null)
			return 0;
		int total = 0;
		for (AddressPromise promise : queue)
			total += promise.count();
		return total;
	}

	public boolean isEmpty() {
		return queues.isEmpty();
	}

	/** Total promised item count across all queued promises. */
	public int getTotalCount() {
		int total = 0;
		for (PriorityQueue<AddressPromise> queue : queues.values())
			for (AddressPromise promise : queue)
				total += promise.count();
		return total;
	}

	public void clearAll() {
		queues.clear();
	}

	/** Advances the age of every queued promise by the given number of ticks. */
	public void tick(int amount) {
		for (PriorityQueue<AddressPromise> queue : queues.values())
			for (AddressPromise promise : queue)
				promise.ticksExisted += amount;
	}

	/**
	 * Removes promises that have lived for at least {@code expiryTicks}.
	 *
	 * @param expiryTicks -1 disables expiry, matching Create's factory gauge.
	 * @return true if any promise was removed
	 */
	public boolean removeExpired(int expiryTicks) {
		if (expiryTicks == -1)
			return false;
		boolean changed = false;
		Iterator<Map.Entry<ItemKey, PriorityQueue<AddressPromise>>> entryIterator =
			queues.entrySet()
				.iterator();
		while (entryIterator.hasNext()) {
			PriorityQueue<AddressPromise> queue = entryIterator.next()
				.getValue();
			int before = queue.size();
			queue.removeIf(promise -> promise.ticksExisted() >= expiryTicks);
			if (queue.size() != before)
				changed = true;
			if (queue.isEmpty())
				entryIterator.remove();
		}
		return changed;
	}

	/** Snapshot of all queued promises (for NBT saving). */
	public List<AddressPromise> flatten() {
		List<AddressPromise> result = new ArrayList<>();
		for (PriorityQueue<AddressPromise> queue : queues.values())
			result.addAll(queue);
		return result;
	}

	/** Replaces the queue contents from a saved snapshot. */
	public void load(List<AddressPromise> saved) {
		clearAll();
		for (AddressPromise promise : saved)
			add(promise.item(), promise.count(), promise.outputAddress(), promise.ticksExisted());
	}
}
