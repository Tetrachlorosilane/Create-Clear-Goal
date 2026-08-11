package net.Tetrachlorosilane.createcleargoal.client;

import net.Tetrachlorosilane.createcleargoal.Createcleargoal;

import net.createmod.catnip.gui.element.ScreenElement;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Icons for the recipe filter GUI (mirrors Create's AllIcons).
 * <p>
 * Layout of {@code textures/gui/recipe_filter_icons.png} (256x256 atlas):
 * 16x16 icons on a 16+2 grid, single column starting at (0,0):
 * <pre>
 *   0,0   I_MODE_BLOCK
 *   0,18  I_MODE_ALLOW_ONLY
 *   0,36  I_MODE_LOCK
 *   0,54  I_MATCH_EXACT
 *   0,72  I_MATCH_CONTAINS
 *   0,90  I_DELETE
 * </pre>
 */
public class ModIcons implements ScreenElement {

	public static final ResourceLocation ICON_ATLAS =
		ResourceLocation.fromNamespaceAndPath(Createcleargoal.MODID, "textures/gui/recipe_filter_icons.png");
	public static final int ATLAS_SIZE = 256;
	public static final int ICON_SIZE = 16;

	private static final int X = 0;

	public static final ModIcons I_MODE_BLOCK = at(X, 0);
	public static final ModIcons I_MODE_ALLOW_ONLY = at(X, 18);
	public static final ModIcons I_MODE_LOCK = at(X, 36);
	public static final ModIcons I_MATCH_EXACT = at(X, 54);
	public static final ModIcons I_MATCH_CONTAINS = at(X, 72);
	public static final ModIcons I_DELETE = at(X, 90);

	private final int iconX;
	private final int iconY;

	private ModIcons(int iconX, int iconY) {
		this.iconX = iconX;
		this.iconY = iconY;
	}

	private static ModIcons at(int x, int y) {
		return new ModIcons(x, y);
	}

	@Override
	public void render(GuiGraphics graphics, int x, int y) {
		graphics.blit(ICON_ATLAS, x, y, iconX, iconY, ICON_SIZE, ICON_SIZE, ATLAS_SIZE, ATLAS_SIZE);
	}
}
