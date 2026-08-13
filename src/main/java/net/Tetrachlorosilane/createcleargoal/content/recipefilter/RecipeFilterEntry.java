package net.Tetrachlorosilane.createcleargoal.content.recipefilter;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.fluids.FluidStack;

/**
 * A single recipe entry stored in a RecipeFilterItem.
 * <p>
 * Each entry records:
 * <ul>
 *   <li>{@code name} - display name (imported recipes default to their first
 *       output's name; manual recipes are named by the player)</li>
 *   <li>{@code recipeId} - optional reference to the source recipe (JEI import)</li>
 *   <li>{@code inputs} / {@code outputs} - ItemStack snapshots (3x3 + 3 in the GUI)</li>
 *   <li>{@code fluidInputs} / {@code fluidOutputs} - FluidStack snapshots for
 *       imported basin recipes (the GUI remains item-only for manual editing)</li>
 *   <li>{@code outputMatch} - exact vs contains output matching (per-entry property)</li>
 * </ul>
 */
public record RecipeFilterEntry(String name, Optional<ResourceLocation> recipeId, List<ItemStack> inputs,
	List<ItemStack> outputs, OutputMatchMode outputMatch, List<FluidStack> fluidInputs, List<FluidStack> fluidOutputs) {

	public static final Codec<RecipeFilterEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.STRING.optionalFieldOf("name", "").forGetter(RecipeFilterEntry::name),
		ResourceLocation.CODEC.optionalFieldOf("recipeId").forGetter(RecipeFilterEntry::recipeId),
		ItemStack.OPTIONAL_CODEC.listOf().fieldOf("inputs").forGetter(RecipeFilterEntry::inputs),
		ItemStack.OPTIONAL_CODEC.listOf().fieldOf("outputs").forGetter(RecipeFilterEntry::outputs),
		OutputMatchMode.CODEC.optionalFieldOf("outputMatch", OutputMatchMode.EXACT).forGetter(RecipeFilterEntry::outputMatch),
		FluidStack.OPTIONAL_CODEC.listOf().optionalFieldOf("fluidInputs", List.of()).forGetter(RecipeFilterEntry::fluidInputs),
		FluidStack.OPTIONAL_CODEC.listOf().optionalFieldOf("fluidOutputs", List.of()).forGetter(RecipeFilterEntry::fluidOutputs)
	).apply(i, RecipeFilterEntry::new));

	public static final Codec<List<RecipeFilterEntry>> LIST_CODEC = CODEC.listOf();

	public static RecipeFilterEntry ofRecipe(String name, ResourceLocation recipeId, List<ItemStack> inputs,
		List<ItemStack> outputs, List<FluidStack> fluidInputs, List<FluidStack> fluidOutputs) {
		return new RecipeFilterEntry(name, Optional.of(recipeId), inputs, outputs, OutputMatchMode.EXACT, fluidInputs,
			fluidOutputs);
	}

	/** An empty placeholder entry shown as "new recipe" in the list. */
	public static RecipeFilterEntry empty() {
		return new RecipeFilterEntry("", Optional.empty(), List.of(), List.of(), OutputMatchMode.EXACT, List.of(), List.of());
	}

	public RecipeFilterEntry withName(String newName) {
		return new RecipeFilterEntry(newName, recipeId, inputs, outputs, outputMatch, fluidInputs, fluidOutputs);
	}

	public RecipeFilterEntry withContents(List<ItemStack> newInputs, List<ItemStack> newOutputs,
		List<FluidStack> newFluidInputs, List<FluidStack> newFluidOutputs) {
		return new RecipeFilterEntry(name, recipeId, newInputs, newOutputs, outputMatch, newFluidInputs, newFluidOutputs);
	}

	public RecipeFilterEntry withOutputMatch(OutputMatchMode mode) {
		return new RecipeFilterEntry(name, recipeId, inputs, outputs, mode, fluidInputs, fluidOutputs);
	}

	public List<ItemStack> nonEmptyInputs() {
		return inputs.stream().filter(s -> !s.isEmpty()).toList();
	}

	public List<ItemStack> nonEmptyOutputs() {
		return outputs.stream().filter(s -> !s.isEmpty()).toList();
	}

	public List<FluidStack> nonEmptyFluidInputs() {
		return fluidInputs.stream().filter(s -> !s.isEmpty()).toList();
	}

	public List<FluidStack> nonEmptyFluidOutputs() {
		return fluidOutputs.stream().filter(s -> !s.isEmpty()).toList();
	}

	/** True when nothing is recorded: no name, no item/fluid inputs and no item/fluid outputs. */
	public boolean isEmpty() {
		return (name == null || name.isBlank()) && nonEmptyInputs().isEmpty() && nonEmptyOutputs().isEmpty()
			&& nonEmptyFluidInputs().isEmpty() && nonEmptyFluidOutputs().isEmpty();
	}

	/** Default display name: player name, or first item/fluid output's hover name for imports. */
	public String displayName() {
		if (name != null && !name.isBlank())
			return name;
		List<ItemStack> outs = nonEmptyOutputs();
		if (!outs.isEmpty())
			return outs.get(0).getHoverName().getString();
		List<FluidStack> fluidOuts = nonEmptyFluidOutputs();
		if (!fluidOuts.isEmpty())
			return fluidOuts.get(0).getHoverName().getString();
		return "";
	}

	/**
	 * ItemStack and FluidStack do not override {@code equals}; the record's
	 * generated equality would compare the recorded input/output lists by
	 * object identity. Two separately deserialised copies of the same entry
	 * (for example a recipe filter nested in a list filter that is sent from
	 * the server, then re-read on the client) would therefore never be "equal",
	 * which makes {@code ItemStack.isSameItemSameComponents} fail and Create's
	 * held-item screen close itself right after opening. Compare items and
	 * fluids by value instead.
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!(obj instanceof RecipeFilterEntry other))
			return false;
		return Objects.equals(name, other.name)
			&& Objects.equals(recipeId, other.recipeId)
			&& outputMatch == other.outputMatch
			&& itemStacksEqual(inputs, other.inputs)
			&& itemStacksEqual(outputs, other.outputs)
			&& fluidStacksEqual(fluidInputs, other.fluidInputs)
			&& fluidStacksEqual(fluidOutputs, other.fluidOutputs);
	}

	@Override
	public int hashCode() {
		int result = Objects.hashCode(name);
		result = 31 * result + Objects.hashCode(recipeId);
		result = 31 * result + outputMatch.hashCode();
		result = 31 * result + itemStacksHash(inputs);
		result = 31 * result + itemStacksHash(outputs);
		result = 31 * result + fluidStacksHash(fluidInputs);
		result = 31 * result + fluidStacksHash(fluidOutputs);
		return result;
	}

	private static boolean itemStacksEqual(List<ItemStack> a, List<ItemStack> b) {
		if (a.size() != b.size())
			return false;
		for (int i = 0; i < a.size(); i++)
			if (!ItemStack.matches(a.get(i), b.get(i)))
				return false;
		return true;
	}

	private static int itemStacksHash(List<ItemStack> stacks) {
		int result = 0;
		for (ItemStack stack : stacks) {
			int hash = 31 + stack.getItem().hashCode();
			hash = 31 * hash + stack.getComponents().hashCode();
			result = 31 * result + hash;
		}
		return result;
	}

	private static boolean fluidStacksEqual(List<FluidStack> a, List<FluidStack> b) {
		if (a.size() != b.size())
			return false;
		for (int i = 0; i < a.size(); i++)
			if (!FluidStack.matches(a.get(i), b.get(i)))
				return false;
		return true;
	}

	private static int fluidStacksHash(List<FluidStack> stacks) {
		int result = 0;
		for (FluidStack stack : stacks)
			result = 31 * result + FluidStack.hashFluidAndComponents(stack);
		return result;
	}
}
