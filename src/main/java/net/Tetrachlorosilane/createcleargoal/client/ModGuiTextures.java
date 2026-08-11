package net.Tetrachlorosilane.createcleargoal.client;

import net.Tetrachlorosilane.createcleargoal.Createcleargoal;

import net.createmod.catnip.gui.element.ScreenElement;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Texture regions for this mod's GUIs (mirrors Create's AllGuiTextures).
 * <p>
 * Layout of {@code textures/gui/recipe_filter.png} (256x256 atlas):
 * the filter panel occupies 0,0 .. 223,153; the slot frames are part of it.
 */
public enum ModGuiTextures implements ScreenElement {

	RECIPE_FILTER_BG("recipe_filter", 0, 0, 223, 153);

	public static final int ATLAS_SIZE = 256;

	public final ResourceLocation location;
	public final int startX;
	public final int startY;
	public final int width;
	public final int height;

	ModGuiTextures(String fileName, int startX, int startY, int width, int height) {
		this.location = ResourceLocation.fromNamespaceAndPath(Createcleargoal.MODID, "textures/gui/" + fileName + ".png");
		this.startX = startX;
		this.startY = startY;
		this.width = width;
		this.height = height;
	}

	@Override
	public void render(GuiGraphics graphics, int x, int y) {
		graphics.blit(location, x, y, startX, startY, width, height, ATLAS_SIZE, ATLAS_SIZE);
	}

	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}
}
