package net.Tetrachlorosilane.createcleargoal.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

@Mixin(Block.class)
public interface BlockAccessor {

	@Invoker("registerDefaultState")
	void createcleargoal$registerDefaultState(BlockState state);
}
