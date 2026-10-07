package com.ultras.claims.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** A 6-row menu with a 7x4 grid, Previous/Next, "Page x/y", Search, Filter and Back. */
public abstract class PagedMenu<T> extends Menu {
    protected static final int PER_PAGE = 28;
    protected int page;
    protected String search = "";
    protected int filterIndex;

    protected PagedMenu(GuiService gui, Player viewer) {
        super(gui, viewer);
    }

    protected abstract List<T> all();

    protected abstract ItemStack render(T t);

    protected abstract void onClick(T t, ClickType click);

    protected abstract Component menuTitle();

    /** Filter ids (translated via gui.filter.&lt;id&gt;). The first one is normally "all". Empty = no filter button. */
    protected List<String> filterIds() {
        return List.of();
    }

    protected boolean matchesFilter(T t, String filterId) {
        return true;
    }

    protected boolean matchesSearch(T t, String lowerQuery) {
        return true;
    }

    /** Extra buttons in the footer (slots 48 and 50). */
    protected void footer() {
    }

    protected boolean searchable() {
        return true;
    }

    @Override
    protected int rows() {
        return 6;
    }

    @Override
    protected Component title() {
        return menuTitle();
    }

    protected List<T> visible() {
        List<String> filters = filterIds();
        String f = filters.isEmpty() ? null : filters.get(Math.floorMod(filterIndex, filters.size()));
        String q = search.toLowerCase(Locale.ROOT);
        List<T> out = new ArrayList<>();
        for (T t : all()) {
            if ((f == null || matchesFilter(t, f)) && (q.isEmpty() || matchesSearch(t, q))) {
                out.add(t);
            }
        }
        return out;
    }

    @Override
    protected void build() {
        List<T> list = visible();
        int pages = Math.max(1, (list.size() + PER_PAGE - 1) / PER_PAGE);
        page = Math.max(0, Math.min(page, pages - 1));
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = page * PER_PAGE + i;
            if (idx >= list.size()) {
                break;
            }
            T t = list.get(idx);
            int slot = (1 + i / 7) * 9 + 1 + i % 7;
            set(slot, render(t), c -> onClick(t, c));
        }
        if (list.isEmpty()) {
            set(22, gui.icon("empty", gui.text(lang, "gui.empty"), List.of()));
        }
        backButton(45);
        if (searchable()) {
            ItemStack s = gui.button("search", lang, "query", search.isEmpty() ? "-" : search);
            set(46, s, c -> {
                if (c.isRightClick()) {
                    search = "";
                    page = 0;
                    refresh();
                } else {
                    gui.prompt(viewer, "gui-search-prompt", text -> {
                        if (text != null) {
                            search = text.trim();
                            page = 0;
                        }
                        reopen();
                    });
                }
            });
        }
        List<String> filters = filterIds();
        if (!filters.isEmpty()) {
            String cur = filters.get(Math.floorMod(filterIndex, filters.size()));
            set(47, gui.button("filter", lang, "filter", plugin.messages().plain(lang, "gui.filter-name." + cur)), c -> {
                filterIndex = Math.floorMod(filterIndex + (c.isRightClick() ? -1 : 1), filters.size());
                page = 0;
                refresh();
            });
        }
        footer();
        set(49, gui.button("page", lang, "page", page + 1, "pages", pages));
        if (page > 0) {
            set(52, gui.button("previous", lang), c -> {
                page--;
                refresh();
            });
        }
        if (page < pages - 1) {
            set(53, gui.button("next", lang), c -> {
                page++;
                refresh();
            });
        }
    }

    /** Re-opens this menu (after a chat prompt closed it). */
    protected void reopen() {
        open();
    }

    protected void extra(int slot, ItemStack item, java.util.function.Consumer<ClickType> action) {
        set(slot, item, action);
    }
}
