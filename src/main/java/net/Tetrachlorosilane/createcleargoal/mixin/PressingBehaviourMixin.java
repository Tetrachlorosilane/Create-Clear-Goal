package net.Tetrachlorosilane.createcleargoal.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.simibubi.create.content.kinetics.press.PressingBehaviour;

import net.minecraft.world.item.ItemStack;

/**
 * Defensive fix for a Create particle bug: {@code PressingBehaviour} stores
 * <i>references</i> to the input inventory's ItemStacks in {@code particleItems}
 * (collected when {@code applyBasinRecipe} fails to consume them). If a later
 * batch consumes those stacks (split mutates the same instances to count 0),
 * the emptied stacks are serialised into the sync packet and the client
 * crashes in {@code spawnParticles -> makeCompactingParticleEffect} with
 * "Empty stacks are not allowed". Dropping empty stacks before writing and
 * before spawning keeps both sides safe.
 */
@Mixin(PressingBehaviour.class)
public class PressingBehaviourMixin {

	@Inject(method = "write", at = @At("HEAD"))
	private void createcleargoal$dropEmptyParticleStacksBeforeWrite(CallbackInfo ci) {
		PressingBehaviour self = (PressingBehaviour) (Object) this;
		self.particleItems.removeIf(ItemStack::isEmpty);
	}

	@Inject(method = "spawnParticles", at = @At("HEAD"))
	private void createcleargoal$dropEmptyParticleStacksBeforeSpawn(CallbackInfo ci) {
		PressingBehaviour self = (PressingBehaviour) (Object) this;
		self.particleItems.removeIf(ItemStack::isEmpty);
	}
}
