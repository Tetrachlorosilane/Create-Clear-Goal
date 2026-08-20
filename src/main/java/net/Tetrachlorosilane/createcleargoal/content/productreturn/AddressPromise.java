package net.Tetrachlorosilane.createcleargoal.content.productreturn;

import net.minecraft.world.item.ItemStack;

/**
 * A promise recorded by the Product Return Station mod.
 * <p>
 * Create's own {@code RequestPromise} is only network+item based; this side
 * class adds the resolved output address and the age of the promise so expired
 * entries can be cleared automatically.
 */
public class AddressPromise {

	public final ItemStack item;
	public final int count;
	public final String outputAddress;
	public int ticksExisted;

	public AddressPromise(ItemStack item, int count) {
		this(item, count, "");
	}

	public AddressPromise(ItemStack item, int count, String outputAddress) {
		this(item, count, outputAddress, 0);
	}

	public AddressPromise(ItemStack item, int count, String outputAddress, int ticksExisted) {
		this.item = item;
		this.count = count;
		this.outputAddress = outputAddress;
		this.ticksExisted = ticksExisted;
	}

	public ItemStack item() {
		return item;
	}

	public int count() {
		return count;
	}

	public String outputAddress() {
		return outputAddress;
	}

	public int ticksExisted() {
		return ticksExisted;
	}
}
