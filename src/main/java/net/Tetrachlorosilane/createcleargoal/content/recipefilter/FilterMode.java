package net.Tetrachlorosilane.createcleargoal.content.recipefilter;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

/**
 * Filter-level behaviour: how the recorded recipes affect the machine.
 * This is a property of the filter item itself, shared by all entries.
 */
public enum FilterMode implements StringRepresentable {
	/** Never run the recorded recipes; everything else is allowed. */
	BLOCK("block"),
	/** Only run the recorded recipes; anything else is refused. */
	ALLOW_ONLY("allow_only"),
	/** Lock onto a recorded recipe when its inputs are present, otherwise run everything. */
	LOCK("lock");

	private final String serializedName;

	FilterMode(String serializedName) {
		this.serializedName = serializedName;
	}

	public static final Codec<FilterMode> CODEC = StringRepresentable.fromEnum(FilterMode::values);

	/** Lookup by serialized name, used by network packets; falls back if unknown. */
	public static FilterMode byName(String name, FilterMode fallback) {
		for (FilterMode mode : values())
			if (mode.getSerializedName().equals(name))
				return mode;
		return fallback;
	}

	@Override
	public String getSerializedName() {
		return serializedName;
	}
}
