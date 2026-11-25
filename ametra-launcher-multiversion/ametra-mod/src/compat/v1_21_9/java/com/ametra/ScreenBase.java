package com.ametra;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** Base de AmetraScreen: aísla la firma de mouseScrolled, que cambia entre versiones de Minecraft. */
public abstract class ScreenBase extends Screen {
    protected ScreenBase(Text title) {
        super(title);
    }

    /** Rueda del ratón (misma firma en todas las versiones). */
    protected abstract boolean onScroll(double mx, double my, double hAmount, double vAmount);

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return onScroll(mouseX, mouseY, horizontalAmount, verticalAmount);
    }
}
