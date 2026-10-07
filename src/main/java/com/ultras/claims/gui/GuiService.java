package com.ultras.claims.gui;

import com.ultras.claims.UltrasClaims;
import com.ultras.claims.claim.Claim;
import com.ultras.claims.claim.MemberPermission;
import com.ultras.claims.claim.NotificationKey;
import com.ultras.claims.core.ChunkPos;
import com.ultras.claims.language.MessageChannel;
import com.ultras.claims.settings.EntryPosition;
import com.ultras.claims.settings.PlayerSettings;
import com.ultras.claims.sound.PlayerSoundGroup;
import com.ultras.claims.sound.SoundKey;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Opens menus and builds their items. Icons are vanilla items. */
public final class GuiService {
    private record Prompt(Consumer<String> callback, long expiresAt) {
    }

    private final UltrasClaims plugin;
    private YamlConfiguration layout = new YamlConfiguration();
    private ItemStack filler;
    private BukkitTask liveTask;
    private final Map<UUID, Prompt> prompts = new HashMap<>();

    public GuiService(UltrasClaims plugin) {
        this.plugin = plugin;
        reload();
    }

    public UltrasClaims plugin() {
        return plugin;
    }

    public void reload() {
        File f = new File(plugin.getDataFolder(), "gui/layout.yml");
        if (!f.exists()) {
            plugin.saveResource("gui/layout.yml", false);
        }
        layout = YamlConfiguration.loadConfiguration(f);
        var res = plugin.getResource("gui/layout.yml");
        if (res != null) {
            layout.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(res, StandardCharsets.UTF_8)));
        }
        ItemStack fl = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta m = fl.getItemMeta();
        m.displayName(Component.space());
        fl.setItemMeta(m);
        filler = fl;
        if (liveTask != null) {
            liveTask.cancel();
        }
        liveTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getOpenInventory().getTopInventory().getHolder() instanceof Menu menu && menu.live()) {
                    menu.refresh();
                }
            }
        }, 20L, 20L);
    }

    public int slot(String menu, String button, int def) {
        return layout.getInt(menu + ".slots." + button, def);
    }

    // ------------------------------------------------------------------ items

    public ItemStack filler() {
        return filler;
    }

    /** Vanilla item per icon id. Overridable in gui/layout.yml under "icons:". */
    private static final Map<String, Material> DEFAULT_ICONS = Map.ofEntries(
            Map.entry("border", Material.LIME_STAINED_GLASS_PANE), Map.entry("members", Material.PLAYER_HEAD),
            Map.entry("settings", Material.COMPARATOR), Map.entry("cabin", Material.CHEST), Map.entry("cabin-disabled", Material.GRAY_DYE),
            Map.entry("map", Material.MAP), Map.entry("notifications", Material.BELL), Map.entry("info", Material.BOOK),
            Map.entry("close", Material.BARRIER), Map.entry("back", Material.ARROW), Map.entry("confirm", Material.LIME_DYE),
            Map.entry("cancel", Material.RED_DYE), Map.entry("next", Material.SPECTRAL_ARROW), Map.entry("previous", Material.SPECTRAL_ARROW),
            Map.entry("add-member", Material.EMERALD), Map.entry("renew", Material.CLOCK), Map.entry("search", Material.SPYGLASS),
            Map.entry("filter", Material.HOPPER), Map.entry("page", Material.PAPER), Map.entry("empty", Material.GRAY_STAINED_GLASS_PANE),
            Map.entry("up", Material.ARROW), Map.entry("down", Material.ARROW), Map.entry("left", Material.ARROW), Map.entry("right", Material.ARROW),
            Map.entry("center", Material.COMPASS), Map.entry("zoom-in", Material.SPYGLASS), Map.entry("zoom-out", Material.SPYGLASS),
            Map.entry("expand-here", Material.EMERALD), Map.entry("expand-unavailable", Material.RED_STAINED_GLASS_PANE),
            Map.entry("toggle-on", Material.LIME_DYE), Map.entry("toggle-off", Material.RED_DYE), Map.entry("option", Material.LIGHT_BLUE_DYE),
            Map.entry("deposit", Material.HOPPER), Map.entry("clock", Material.CLOCK), Map.entry("membership", Material.NAME_TAG),
            Map.entry("ps-sounds", Material.NOTE_BLOCK), Map.entry("ps-language", Material.WRITABLE_BOOK), Map.entry("ps-messages", Material.PAPER),
            Map.entry("ps-entry", Material.OAK_DOOR), Map.entry("ps-invitations", Material.NAME_TAG), Map.entry("ps-membership", Material.NAME_TAG),
            Map.entry("ps-notifications", Material.BELL), Map.entry("ps-reset", Material.REDSTONE));

    private Material materialFor(String id) {
        String configured = layout.getString("icons." + id);
        Material m = configured == null ? null : Material.matchMaterial(configured);
        return m != null ? m : DEFAULT_ICONS.getOrDefault(id, Material.PAPER);
    }

    /** A menu button: a normal vanilla item (no resource pack needed). */
    public ItemStack icon(String id, Component name, List<Component> lore) {
        ItemStack it = new ItemStack(materialFor(id));
        ItemMeta m = it.getItemMeta();
        m.displayName(name);
        if (!lore.isEmpty()) {
            m.lore(lore);
        }
        if (id.equals("toggle-on")) {
            m.setEnchantmentGlintOverride(true);
        }
        m.addItemFlags(ItemFlag.values());
        it.setItemMeta(m);
        return it;
    }

    public Component text(String lang, String key, Object... ph) {
        return plugin.messages().gui(lang, key, ph);
    }

    /** A button whose text comes from gui.&lt;id&gt; (name) and gui.&lt;id&gt;-lore (optional). */
    public ItemStack button(String id, String lang, Object... ph) {
        return icon(id, text(lang, "gui." + id, ph), lore(lang, "gui." + id + "-lore", ph));
    }

    public List<Component> lore(String lang, String key, Object... ph) {
        return plugin.messages().has(lang, key) ? plugin.messages().guiLore(lang, key, ph) : List.of();
    }

    public ItemStack head(OfflinePlayer op, Component name, List<Component> lore) {
        ItemStack it = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta m = (SkullMeta) it.getItemMeta();
        m.setOwningPlayer(op);
        m.displayName(name);
        if (!lore.isEmpty()) {
            m.lore(lore);
        }
        m.addItemFlags(ItemFlag.values());
        it.setItemMeta(m);
        return it;
    }

    // ------------------------------------------------------------------ chat prompt (search)

    /** Closes the menu, asks for one line of chat and hands it to {@code callback} (null when cancelled/expired). */
    public void prompt(Player p, String messageKey, Consumer<String> callback) {
        p.closeInventory();
        plugin.messages().error(p, messageKey);
        prompts.put(p.getUniqueId(), new Prompt(callback, System.currentTimeMillis() + 60_000L));
    }

    /** Returns the pending callback of this player (consumed), or null. */
    Consumer<String> takePrompt(UUID id) {
        Prompt p = prompts.remove(id);
        return p == null || p.expiresAt() < System.currentTimeMillis() ? null : p.callback();
    }

    boolean hasPrompt(UUID id) {
        Prompt p = prompts.get(id);
        return p != null && p.expiresAt() >= System.currentTimeMillis();
    }

    void forget(UUID id) {
        prompts.remove(id);
    }

    // ------------------------------------------------------------------ bookkeeping

    public void closeAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof Menu) {
                p.closeInventory();
            }
        }
        prompts.clear();
    }

    public void closeFor(Claim c) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof Menu m && m.claim() != null && m.claim().id().equals(c.id())) {
                p.closeInventory();
            }
        }
    }

    public void refreshCabin(Claim c) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof Menu m && m.claim() != null && m.claim().id().equals(c.id())) {
                m.refresh();
            }
        }
    }

    public boolean canConfigure(Player p, Claim c) {
        return plugin.hasBypass(p) || c.isOwner(p.getUniqueId());
    }

    // ------------------------------------------------------------------ openers (claim)

    /** Head click: owner, admin and members get the management menu, everybody else a short refusal. */
    public void openClaim(Player p, Claim c) {
        boolean ok = plugin.hasBypass(p) || c.isOwner(p.getUniqueId()) || c.isMember(p.getUniqueId());
        if (!ok) {
            plugin.messages().error(p, "claim-no-access", "owner", c.ownerName());
            plugin.sounds().play(p, SoundKey.ERROR);
            return;
        }
        new ClaimMenu(this, p, c).open();
    }

    public Menu infoMenu(Player p, Claim c, Menu back) {
        return new InfoMenu(this, p, c).back(() -> back);
    }

    public void openMembers(Player p, Claim c, Menu back) {
        new MembersMenu(this, p, c).back(() -> back).open();
    }

    public void openPicker(Player p, Claim c, Menu back) {
        new PlayerPickerMenu(this, p, c).back(() -> back).open();
    }

    public void openMemberPermissions(Player p, Claim c, UUID member, Menu back) {
        var cm = c.member(member);
        if (cm == null) {
            back.open();
            return;
        }
        String lang = plugin.messages().languageOf(p);
        boolean edit = canConfigure(p, c);
        ToggleMenu m = new ToggleMenu(this, p, text(lang, "gui.title-permissions", "player", cm.name()), () -> {
            List<ToggleMenu.Option> list = new ArrayList<>();
            for (MemberPermission perm : MemberPermission.values()) {
                String id = perm.id();
                boolean on = cm.permissions().contains(perm);
                String l = plugin.messages().languageOf(p);
                List<Component> lore = new ArrayList<>(lore(l, "gui.perm." + id + "-lore"));
                lore.add(Component.empty());
                lore.add(text(l, on ? "gui.state-on" : "gui.state-off"));
                if (perm.sensitive()) {
                    lore.add(text(l, "gui.sensitive"));
                }
                list.add(new ToggleMenu.Option(text(l, "gui.perm." + id), lore, on, click -> {
                    if (!edit) {
                        plugin.messages().error(p, "gui-no-permission");
                        return;
                    }
                    plugin.members().setPermission(p, c, member, perm, !cm.permissions().contains(perm));
                }));
            }
            return list;
        });
        m.claim = c;
        m.back(() -> back).open();
    }

    public void openSettings(Player p, Claim c, String chunkLabel, Menu back) {
        new SettingsMenu(this, p, c, chunkLabel).back(() -> back).open();
    }

    public void openNotifications(Player p, Claim c, Menu back) {
        String lang = plugin.messages().languageOf(p);
        boolean edit = canConfigure(p, c);
        ToggleMenu m = new ToggleMenu(this, p, text(lang, "gui.title-notifications"), () -> {
            List<ToggleMenu.Option> list = new ArrayList<>();
            String l = plugin.messages().languageOf(p);
            for (NotificationKey k : NotificationKey.values()) {
                boolean on = c.notifies(k);
                List<Component> lore = new ArrayList<>(lore(l, "gui.notif." + k.id() + "-lore"));
                lore.add(Component.empty());
                lore.add(text(l, on ? "gui.state-on" : "gui.state-off"));
                list.add(new ToggleMenu.Option(text(l, "gui.notif." + k.id()), lore, on, click -> {
                    if (!edit) {
                        plugin.messages().error(p, "gui-no-permission");
                        return;
                    }
                    if (on) {
                        c.notifications().remove(k);
                    } else {
                        c.notifications().add(k);
                    }
                    plugin.claims().save(c);
                }));
            }
            return list;
        });
        m.claim = c;
        m.back(() -> back).open();
    }

    public void openCabin(Player p, Claim c, Menu back) {
        if (!plugin.cabin().enabled()) {
            plugin.messages().error(p, "cabin-disabled");
            plugin.sounds().play(p, SoundKey.ERROR);
            return;
        }
        new CabinMenu(this, p, c).back(() -> back).open();
    }

    public void openMap(Player p, Menu back) {
        new MapMenu(this, p).back(back == null ? null : () -> back).open();
    }

    public void openConfirmExpand(Player p, Claim c, ChunkPos target) {
        openConfirmExpand(p, c, target, null);
    }

    public void openConfirmExpand(Player p, Claim c, ChunkPos target, Menu back) {
        String lang = plugin.messages().languageOf(p);
        List<Component> lore = new ArrayList<>(lore(lang, "gui.confirm-expand-lore", "x", target.x(), "z", target.z(),
                "current", c.expansions(), "max", plugin.expansion().max()));
        lore.add(plugin.economy().cost(p));
        ConfirmMenu m = new ConfirmMenu(this, p, text(lang, "gui.title-confirm-expand"), lore, () -> plugin.expansion().requestTarget(p, c, target, true));
        m.claim = c;
        if (back != null) {
            m.back(() -> back);
        }
        m.open();
    }

    public void openConfirm(Player p, Component title, List<Component> lore, Menu back, Runnable action) {
        ConfirmMenu m = new ConfirmMenu(this, p, title, lore, action);
        m.claim = back == null ? null : back.claim();
        m.back(() -> back).open();
    }

    // ------------------------------------------------------------------ openers (player settings, /claim_setting)

    public void openPlayerSettings(Player p) {
        new PlayerSettingsMenu(this, p).open();
    }

    public void openSounds(Player p, Menu back) {
        PlayerSettings ps = plugin.players().get(p.getUniqueId());
        ToggleMenu m = new ToggleMenu(this, p, text(plugin.messages().languageOf(p), "gui.title-sounds"), () -> {
            String l = plugin.messages().languageOf(p);
            List<ToggleMenu.Option> list = new ArrayList<>();
            boolean all = ps.allSounds();
            list.add(new ToggleMenu.Option(text(l, "gui.sound.all"), state(l, all), all, c -> {
                ps.allSounds(!all);
                plugin.players().save(ps);
            }));
            for (PlayerSoundGroup g : PlayerSoundGroup.values()) {
                boolean on = ps.sound(g);
                list.add(new ToggleMenu.Option(text(l, "gui.sound." + g.name().toLowerCase()), state(l, on), on, c -> {
                    ps.sound(g, !on);
                    plugin.players().save(ps);
                }));
            }
            return list;
        });
        m.back(() -> back).open();
    }

    public void openMessages(Player p, Menu back) {
        PlayerSettings ps = plugin.players().get(p.getUniqueId());
        ToggleMenu m = new ToggleMenu(this, p, text(plugin.messages().languageOf(p), "gui.title-messages"), () -> {
            String l = plugin.messages().languageOf(p);
            List<ToggleMenu.Option> list = new ArrayList<>();
            for (MessageChannel ch : MessageChannel.values()) {
                if (ch == MessageChannel.ALWAYS) {
                    continue;
                }
                boolean on = ps.message(ch);
                list.add(new ToggleMenu.Option(text(l, "gui.msg." + ch.name().toLowerCase()), state(l, on), on, c -> {
                    ps.message(ch, !on);
                    plugin.players().save(ps);
                }));
            }
            return list;
        });
        m.back(() -> back).open();
    }

    public void openEntryExit(Player p, Menu back) {
        PlayerSettings ps = plugin.players().get(p.getUniqueId());
        ToggleMenu m = new ToggleMenu(this, p, text(plugin.messages().languageOf(p), "gui.title-entry"), () -> {
            String l = plugin.messages().languageOf(p);
            List<ToggleMenu.Option> list = new ArrayList<>();
            list.add(new ToggleMenu.Option(text(l, "gui.entry.owner"), state(l, ps.ownerEntryAlerts()), ps.ownerEntryAlerts(), c -> {
                ps.ownerEntryAlerts(!ps.ownerEntryAlerts());
                plugin.players().save(ps);
            }));
            list.add(new ToggleMenu.Option(text(l, "gui.entry.member"), state(l, ps.memberEntryAlerts()), ps.memberEntryAlerts(), c -> {
                ps.memberEntryAlerts(!ps.memberEntryAlerts());
                plugin.players().save(ps);
            }));
            list.add(new ToggleMenu.Option(text(l, "gui.entry.leave"), state(l, ps.notifyOnLeave()), ps.notifyOnLeave(), c -> {
                ps.notifyOnLeave(!ps.notifyOnLeave());
                plugin.players().save(ps);
            }));
            boolean title = ps.entryPosition() == EntryPosition.TITLE;
            list.add(new ToggleMenu.Option(text(l, "gui.entry.position"), List.of(text(l, title ? "gui.entry.position-title" : "gui.entry.position-actionbar"),
                    Component.empty(), text(l, "gui.click-to-change")), null, c -> {
                ps.entryPosition(ps.entryPosition().next());
                plugin.players().save(ps);
            }));
            return list;
        });
        m.back(() -> back).open();
    }

    public void openLanguage(Player p, Menu back) {
        PlayerSettings ps = plugin.players().get(p.getUniqueId());
        ToggleMenu m = new ToggleMenu(this, p, text(plugin.messages().languageOf(p), "gui.title-language"), () -> {
            String l = plugin.messages().languageOf(p);
            List<ToggleMenu.Option> list = new ArrayList<>();
            for (String code : com.ultras.claims.language.Messages.LANGUAGES) {
                boolean cur = ps.language().equals(code);
                list.add(new ToggleMenu.Option(Component.text(plugin.messages().languageName(code)), List.of(text(l, cur ? "gui.state-selected" : "gui.click-to-select")), cur, c -> {
                    ps.language(code);
                    plugin.players().save(ps);
                    // the whole menu chain is rebuilt in the new language
                    openPlayerSettings(p);
                }));
            }
            return list;
        });
        m.back(() -> back).open();
    }

    private List<Component> state(String lang, boolean on) {
        return List.of(text(lang, on ? "gui.state-on" : "gui.state-off"), Component.empty(), text(lang, "gui.click-to-toggle"));
    }

    public void openMembership(Player p, Menu back) {
        new MembershipMenu(this, p).back(() -> back).open();
    }

    public void click(Player p, ClickType t) {
        // reserved for future shared behaviour
    }
}
