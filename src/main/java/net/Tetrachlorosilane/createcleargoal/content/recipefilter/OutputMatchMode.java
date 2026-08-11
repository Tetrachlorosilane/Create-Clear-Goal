package net.Tetrachlorosilane.createcleargoal.content.recipefilter;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

/**
 * Per-entry output matching rule: how a candidate recipe's outputs are
 * compared against the outputs recorded in this entry.
 */
public enum OutputMatchMode implements StringRepresentable {
	/** The candidate recipe's outputs must equal the recorded outputs exactly. */
	EXACT("exact"),
	/** The candidate recipe's outputs must contain all recorded outputs. */
	CONTAINS("contains");

	private final String serializedName;

	OutputMatchMode(String serializedName) {
		this.serializedName = serializedName;
	}

	public static final Codec<OutputMatchMode> CODEC = StringRepresentable.fromEnum(OutputMatchMode::values);

	/** Lookup by serialized name, used by network packets; falls back if unknown. */
	public static OutputMatchMode byName(String name, OutputMatchMode fallback) {
		for (OutputMatchMode mode : values())
			if (mode.getSerializedName().equals(name))
				return mode;
		return fallback;
	}

	@Override
	public String getSerializedName() {
		return serializedName;
	}
}
