package net.Tetrachlorosilane.createcleargoal.content.productreturn;

import net.minecraft.world.item.ItemStack;

/**
 * An address-aware promise recorded by the Product Return Station mod.
 * <p>
 * Create's own {@code RequestPromise} is only network+item based; this side
 * class adds the destination address that a factory gauge sent its request to,
 * plus the age of the promise so expired entries can be cleared automatically.
 */
public class AddressPromise {

	public final ItemStack item;
	public int count;
	public final String address;
	public int ticksExisted;

	public AddressPromise(ItemStack item, int count, String address) {
		this(item, count, address, 0);
	}

	public AddressPromise(ItemStack item, int count, String address, int ticksExisted) {
		this.item = item;
		this.count = count;
		this.address = address;
		this.ticksExisted = ticksExisted;
	}

	public ItemStack item() {
		return item;
	}

	public int count() {
		return count;
	}

	public String address() {
		return address;
	}

	public int ticksExisted() {
		return ticksExisted;
	}

	public void tick() {
		ticksExisted++;
	}
}
