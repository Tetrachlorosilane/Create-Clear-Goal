package net.Tetrachlorosilane.createcleargoal.mixin;

/**
 * Mixin-injected interface: implemented by {@code BasinOperatingBlockEntity}
 * via {@link BasinOperatingBlockEntityMixin} so other mixins can notify the
 * operator that its basin's filter slot content changed.
 */
public interface FilterChangedMarker {

	/** Marks the operator so the next recipe continuity check is broken. */
	void createcleargoal$markFilterChanged();
}
