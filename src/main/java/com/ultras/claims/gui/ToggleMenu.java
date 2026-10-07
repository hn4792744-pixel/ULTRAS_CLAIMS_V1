package com.ultras.claims.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** A compact list of ON/OFF switches (member permissions, notifications, sounds, messages ...). */
public final class ToggleMenu extends Menu {
    /** One row. {@code state} null = a plain button without a switch. */
    public record Option(Component name, List<Component> lore, Boolean state, Consumer<ClickType> click) {
    }

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
    private final Component title;
    private final Supplier<List<Option>> options;

    public ToggleMenu(GuiService gui, Player viewer, Component title, Supplier<List<Option>> options) {
        super(gui, viewer);
        this.title = title;
        this.options = options;
    }

    @Override
    protected int rows() {
        return 5;
    }

    @Override
    protected Component title() {
        return title;
    }

    @Override
    protected void build() {
        List<Option> list = options.get();
        for (int i = 0; i < list.size() && i < SLOTS.length; i++) {
            Option o = list.get(i);
            String icon = o.state() == null ? "option" : o.state() ? "toggle-on" : "toggle-off";
            set(SLOTS[i], gui.icon(icon, o.name(), o.lore()), c -> {
                o.click().accept(c);
                refresh();
            });
        }
        backButton(40);
    }
}
