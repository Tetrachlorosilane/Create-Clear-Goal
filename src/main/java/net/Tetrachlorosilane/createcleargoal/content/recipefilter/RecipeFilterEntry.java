package net.Tetrachlorosilane.createcleargoal.content.recipefilter;

import java.util.ArrayList;
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
 *   <li>{@code inputAlternatives} / {@code fluidInputAlternatives} - the full
 *       candidate lists captured from each Ingredient / SizedFluidIngredient at
 *       import time. They let LOCK keep matching tags and multi-candidate
 *       ingredients even after the original recipe is removed.</li>
 *   <li>{@code outputMatch} - exact vs contains output matching (per-entry property)</li>
 * </ul>
 */
public record RecipeFilterEntry(String name, Optional<ResourceLocation> recipeId, List<ItemStack> inputs,
	List<ItemStack> outputs, OutputMatchMode outputMatch, List<FluidStack> fluidInputs, List<FluidStack> fluidOutputs,
	List<List<ItemStack>> inputAlternatives, List<List<FluidStack>> fluidInputAlternatives) {

	public static final Codec<RecipeFilterEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.STRING.optionalFieldOf("name", "").forGetter(RecipeFilterEntry::name),
		ResourceLocation.CODEC.optionalFieldOf("recipeId").forGetter(RecipeFilterEntry::recipeId),
		ItemStack.OPTIONAL_CODEC.listOf().fieldOf("inputs").forGetter(RecipeFilterEntry::inputs),
		ItemStack.OPTIONAL_CODEC.listOf().fieldOf("outputs").forGetter(RecipeFilterEntry::outputs),
		OutputMatchMode.CODEC.optionalFieldOf("outputMatch", OutputMatchMode.EXACT).forGetter(RecipeFilterEntry::outputMatch),
		FluidStack.OPTIONAL_CODEC.listOf().optionalFieldOf("fluidInputs", List.of()).forGetter(RecipeFilterEntry::fluidInputs),
		FluidStack.OPTIONAL_CODEC.listOf().optionalFieldOf("fluidOutputs", List.of()).forGetter(RecipeFilterEntry::fluidOutputs),
		ItemStack.OPTIONAL_CODEC.listOf().listOf().optionalFieldOf("inputAlternatives", List.of()).forGetter(RecipeFilterEntry::inputAlternatives),
		FluidStack.OPTIONAL_CODEC.listOf().listOf().optionalFieldOf("fluidInputAlternatives", List.of()).forGetter(RecipeFilterEntry::fluidInputAlternatives)
	).apply(i, RecipeFilterEntry::new));

	public static final Codec<List<RecipeFilterEntry>> LIST_CODEC = CODEC.listOf();

	/** Compatibility constructor used by manual/legacy code; builds single-candidate alternatives. */
	public RecipeFilterEntry(String name, Optional<ResourceLocation> recipeId, List<ItemStack> inputs,
		List<ItemStack> outputs, OutputMatchMode outputMatch, List<FluidStack> fluidInputs, List<FluidStack> fluidOutputs) {
		this(name, recipeId, inputs, outputs, outputMatch, fluidInputs, fluidOutputs,
			toSingleAlternatives(inputs), toSingleFluidAlternatives(fluidInputs));
	}

	public static RecipeFilterEntry ofRecipe(String name, ResourceLocation recipeId, List<ItemStack> inputs,
		List<ItemStack> outputs, List<FluidStack> fluidInputs, List<FluidStack> fluidOutputs,
		List<List<ItemStack>> inputAlternatives, List<List<FluidStack>> fluidInputAlternatives) {
		return new RecipeFilterEntry(name, Optional.of(recipeId), inputs, outputs, OutputMatchMode.EXACT, fluidInputs,
			fluidOutputs, inputAlternatives, fluidInputAlternatives);
	}

	/** An empty placeholder entry shown as "new recipe" in the list. */
	public static RecipeFilterEntry empty() {
		return new RecipeFilterEntry("", Optional.empty(), List.of(), List.of(), OutputMatchMode.EXACT, List.of(), List.of(),
			List.of(), List.of());
	}

	public RecipeFilterEntry withName(String newName) {
		return new RecipeFilterEntry(newName, recipeId, inputs, outputs, outputMatch, fluidInputs, fluidOutputs,
			inputAlternatives, fluidInputAlternatives);
	}

	public RecipeFilterEntry withContents(List<ItemStack> newInputs, List<ItemStack> newOutputs,
		List<FluidStack> newFluidInputs, List<FluidStack> newFluidOutputs) {
		return new RecipeFilterEntry(name, recipeId, newInputs, newOutputs, outputMatch, newFluidInputs, newFluidOutputs,
			mergeAlternatives(inputs, inputAlternatives, newInputs),
			mergeFluidAlternatives(fluidInputs, fluidInputAlternatives, newFluidInputs));
	}

	public RecipeFilterEntry withOutputMatch(OutputMatchMode mode) {
		return new RecipeFilterEntry(name, recipeId, inputs, outputs, mode, fluidInputs, fluidOutputs,
			inputAlternatives, fluidInputAlternatives);
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
			&& fluidStacksEqual(fluidOutputs, other.fluidOutputs)
			&& itemAlternativeListsEqual(inputAlternatives, other.inputAlternatives)
			&& fluidAlternativeListsEqual(fluidInputAlternatives, other.fluidInputAlternatives);
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
		result = 31 * result + itemAlternativeListsHash(inputAlternatives);
		result = 31 * result + fluidAlternativeListsHash(fluidInputAlternatives);
		return result;
	}

	// --- alternative-list helpers ---

	private static List<List<ItemStack>> toSingleAlternatives(List<ItemStack> inputs) {
		List<List<ItemStack>> result = new ArrayList<>();
		for (ItemStack stack : inputs)
			result.add(stack.isEmpty() ? List.of() : List.of(stack.copy()));
		return result;
	}

	private static List<List<FluidStack>> toSingleFluidAlternatives(List<FluidStack> inputs) {
		List<List<FluidStack>> result = new ArrayList<>();
		for (FluidStack stack : inputs)
			result.add(stack.isEmpty() ? List.of() : List.of(stack.copy()));
		return result;
	}

	private static List<List<ItemStack>> mergeAlternatives(List<ItemStack> oldInputs,
		List<List<ItemStack>> oldAlternatives, List<ItemStack> newInputs) {
		List<List<ItemStack>> result = new ArrayList<>();
		for (int i = 0; i < newInputs.size(); i++) {
			ItemStack newStack = newInputs.get(i);
			if (newStack.isEmpty()) {
				result.add(List.of());
				continue;
			}
			if (i < oldInputs.size() && i < oldAlternatives.size()
				&& ItemStack.isSameItemSameComponents(oldInputs.get(i), newStack)
				&& !oldAlternatives.get(i).isEmpty()) {
				result.add(oldAlternatives.get(i));
			} else {
				result.add(List.of(newStack.copy()));
			}
		}
		return result;
	}

	private static List<List<FluidStack>> mergeFluidAlternatives(List<FluidStack> oldInputs,
		List<List<FluidStack>> oldAlternatives, List<FluidStack> newInputs) {
		List<List<FluidStack>> result = new ArrayList<>();
		for (int i = 0; i < newInputs.size(); i++) {
			FluidStack newStack = newInputs.get(i);
			if (newStack.isEmpty()) {
				result.add(List.of());
				continue;
			}
			if (i < oldInputs.size() && i < oldAlternatives.size()
				&& FluidStack.isSameFluidSameComponents(oldInputs.get(i), newStack)
				&& !oldAlternatives.get(i).isEmpty()) {
				result.add(oldAlternatives.get(i));
			} else {
				result.add(List.of(newStack.copy()));
			}
		}
		return result;
	}

	private static boolean itemStacksEqual(List<ItemStack> a, List<ItemStack> b) {
		if (a.size() != b.size())
			return false;
		for (int i = 0; i < a.size(); i++)
			if (!ItemStack.isSameItemSameComponents(a.get(i), b.get(i)))
				return false;
		return true;
	}

	private static int itemStacksHash(List<ItemStack> stacks) {
		int result = 0;
		for (ItemStack stack : stacks)
			result = 31 * result + ItemStack.hashItemAndComponents(stack);
		return result;
	}

	private static boolean fluidStacksEqual(List<FluidStack> a, List<FluidStack> b) {
		if (a.size() != b.size())
			return false;
		for (int i = 0; i < a.size(); i++)
			if (!FluidStack.isSameFluidSameComponents(a.get(i), b.get(i)))
				return false;
		return true;
	}

	private static int fluidStacksHash(List<FluidStack> stacks) {
		int result = 0;
		for (FluidStack stack : stacks)
			result = 31 * result + FluidStack.hashFluidAndComponents(stack);
		return result;
	}

	private static boolean itemAlternativeListsEqual(List<List<ItemStack>> a, List<List<ItemStack>> b) {
		if (a.size() != b.size())
			return false;
		for (int i = 0; i < a.size(); i++)
			if (!itemStacksEqual(a.get(i), b.get(i)))
				return false;
		return true;
	}

	private static int itemAlternativeListsHash(List<List<ItemStack>> lists) {
		int result = 0;
		for (List<ItemStack> list : lists)
			result = 31 * result + itemStacksHash(list);
		return result;
	}

	private static boolean fluidAlternativeListsEqual(List<List<FluidStack>> a, List<List<FluidStack>> b) {
		if (a.size() != b.size())
			return false;
		for (int i = 0; i < a.size(); i++)
			if (!fluidStacksEqual(a.get(i), b.get(i)))
				return false;
		return true;
	}

	private static int fluidAlternativeListsHash(List<List<FluidStack>> lists) {
		int result = 0;
		for (List<FluidStack> list : lists)
			result = 31 * result + fluidStacksHash(list);
		return result;
	}
}
