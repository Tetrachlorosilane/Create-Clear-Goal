package net.Tetrachlorosilane.createcleargoal.content.recipefilter;

/**
 * Mixin-injected interface: implemented by {@code BasinOperatingBlockEntity}
 * via the mixin in the {@code ..mixin} package so other mixins can notify the
 * operator that its basin's filter slot content changed.
 * <p>
 * Deliberately lives outside the mixin package: the mixin framework forbids
 * referencing classes inside a declared mixin package directly.
 */
public interface FilterChangedMarker {

	/** Marks the operator so the next recipe continuity check is broken. */
	void createcleargoal$markFilterChanged();
}
