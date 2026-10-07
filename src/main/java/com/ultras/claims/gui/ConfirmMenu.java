package com.ultras.claims.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.List;

/** Confirm / Cancel. */
public final class ConfirmMenu extends Menu {
    private final Component title;
    private final List<Component> lore;
    private final Runnable onConfirm;

    public ConfirmMenu(GuiService gui, Player viewer, Component title, List<Component> lore, Runnable onConfirm) {
        super(gui, viewer);
        this.title = title;
        this.lore = lore;
        this.onConfirm = onConfirm;
    }

    @Override
    protected int rows() {
        return 3;
    }

    @Override
    protected Component title() {
        return title;
    }

    @Override
    protected void build() {
        set(13, gui.icon("info", title, lore));
        set(11, gui.button("confirm", lang), c -> {
            viewer.closeInventory();
            onConfirm.run();
        });
        set(15, gui.button("cancel", lang), c -> {
            if (back != null) {
                back.get().open();
            } else {
                viewer.closeInventory();
            }
        });
    }
}
