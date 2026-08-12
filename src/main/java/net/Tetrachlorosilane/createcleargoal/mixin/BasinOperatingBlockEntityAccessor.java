package net.Tetrachlorosilane.createcleargoal.mixin;

import java.util.Optional;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.basin.BasinOperatingBlockEntity;

/**
 * Accessor for {@link BasinOperatingBlockEntity#getBasin()}. The method is
 * protected and declared in the parent class, so it cannot be {@code @Shadow}ed
 * from a mixin targeting a subclass - Mixin only resolves shadows against
 * members declared in the target class itself (it does not walk the hierarchy).
 */
@Mixin(BasinOperatingBlockEntity.class)
public interface BasinOperatingBlockEntityAccessor {

	@Invoker("getBasin")
	Optional<BasinBlockEntity> createcleargoal$getBasin();
}
