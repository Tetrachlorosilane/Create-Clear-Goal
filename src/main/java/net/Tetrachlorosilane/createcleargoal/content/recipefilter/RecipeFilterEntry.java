package net.Tetrachlorosilane.createcleargoal.content.recipefilter;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * A single recipe entry stored in a RecipeFilterItem.
 * <p>
 * Each entry records:
 * <ul>
 *   <li>{@code name} - display name (imported recipes default to their first
 *       output's name; manual recipes are named by the player)</li>
 *   <li>{@code recipeId} - optional reference to the source recipe (JEI import)</li>
 *   <li>{@code inputs} / {@code outputs} - ItemStack snapshots (3x3 + 3 in the GUI)</li>
 *   <li>{@code outputMatch} - exact vs contains output matching (per-entry property)</li>
 * </ul>
 */
public record RecipeFilterEntry(String name, Optional<ResourceLocation> recipeId, List<ItemStack> inputs,
	List<ItemStack> outputs, OutputMatchMode outputMatch) {

	public static final Codec<RecipeFilterEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.STRING.optionalFieldOf("name", "").forGetter(RecipeFilterEntry::name),
		ResourceLocation.CODEC.optionalFieldOf("recipeId").forGetter(RecipeFilterEntry::recipeId),
		ItemStack.OPTIONAL_CODEC.listOf().fieldOf("inputs").forGetter(RecipeFilterEntry::inputs),
		ItemStack.OPTIONAL_CODEC.listOf().fieldOf("outputs").forGetter(RecipeFilterEntry::outputs),
		OutputMatchMode.CODEC.optionalFieldOf("outputMatch", OutputMatchMode.EXACT).forGetter(RecipeFilterEntry::outputMatch)
	).apply(i, RecipeFilterEntry::new));

	public static final Codec<List<RecipeFilterEntry>> LIST_CODEC = CODEC.listOf();

	public static RecipeFilterEntry ofRecipe(String name, ResourceLocation recipeId, List<ItemStack> inputs,
		List<ItemStack> outputs) {
		return new RecipeFilterEntry(name, Optional.of(recipeId), inputs, outputs, OutputMatchMode.EXACT);
	}

	/** An empty placeholder entry shown as "new recipe" in the list. */
	public static RecipeFilterEntry empty() {
		return new RecipeFilterEntry("", Optional.empty(), List.of(), List.of(), OutputMatchMode.EXACT);
	}

	public RecipeFilterEntry withName(String newName) {
		return new RecipeFilterEntry(newName, recipeId, inputs, outputs, outputMatch);
	}

	public RecipeFilterEntry withContents(List<ItemStack> newInputs, List<ItemStack> newOutputs) {
		return new RecipeFilterEntry(name, recipeId, newInputs, newOutputs, outputMatch);
	}

	public RecipeFilterEntry withOutputMatch(OutputMatchMode mode) {
		return new RecipeFilterEntry(name, recipeId, inputs, outputs, mode);
	}

	public List<ItemStack> nonEmptyInputs() {
		return inputs.stream().filter(s -> !s.isEmpty()).toList();
	}

	public List<ItemStack> nonEmptyOutputs() {
		return outputs.stream().filter(s -> !s.isEmpty()).toList();
	}

	/** True when nothing is recorded: no name, no inputs and no outputs. */
	public boolean isEmpty() {
		return (name == null || name.isBlank()) && nonEmptyInputs().isEmpty() && nonEmptyOutputs().isEmpty();
	}

	/** Default display name: player name, or first output's hover name for imports. */
	public String displayName() {
		if (name != null && !name.isBlank())
			return name;
		List<ItemStack> outs = nonEmptyOutputs();
		if (!outs.isEmpty())
			return outs.get(0).getHoverName().getString();
		return "";
	}
}
