package net.Tetrachlorosilane.createcleargoal.content.productreturn;

import java.util.Objects;

import net.minecraft.world.item.ItemStack;

/**
 * Value-based key for {@link ItemStack}s used in the Product Return Station
 * queues. Equality ignores count, matching Create's item matching semantics.
 */
public record ItemKey(ItemStack stack) {

	public ItemKey {
		stack = stack.copy();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!(obj instanceof ItemKey other))
			return false;
		return ItemStack.isSameItemSameComponents(stack, other.stack);
	}

	@Override
	public int hashCode() {
		return ItemStack.hashItemAndComponents(stack);
	}

	@Override
	public String toString() {
		return Objects.toString(stack);
	}
}
