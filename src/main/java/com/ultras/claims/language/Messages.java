package com.ultras.claims.language;

import com.ultras.claims.UltrasClaims;
import com.ultras.claims.core.SmallCaps;
import com.ultras.claims.settings.PlayerSettings;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.title.Title;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * All player-visible text lives in messages_en.yml / messages_ar.yml (MiniMessage). Every player sees the
 * language they chose. English text is rendered in small caps; Arabic is left untouched.
 */
public final class Messages {
    public static final List<String> LANGUAGES = List.of("en", "ar");
    private static final Pattern HEX = Pattern.compile("^#[0-9a-fA-F]{6}$");

    private final UltrasClaims plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private volatile Map<String, Map<String, List<String>>> catalog = Map.of();
    private volatile Map<String, Boolean> smallCaps = Map.of();
    private volatile Map<String, String> languageNames = Map.of();
    private volatile TagResolver tags = TagResolver.empty();
    private volatile Component prefix = Component.empty();

    public Messages(UltrasClaims plugin) {
        this.plugin = plugin;
    }

    public void load() {
        Map<String, Map<String, List<String>>> cat = new HashMap<>();
        Map<String, Boolean> sc = new HashMap<>();
        Map<String, String> names = new HashMap<>();
        for (String lang : LANGUAGES) {
            String file = "messages_" + lang + ".yml";
            File f = new File(plugin.getDataFolder(), file);
            if (!f.exists()) {
                plugin.saveResource(file, false);
            }
            YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
            var res = plugin.getResource(file);
            if (res != null) {
                y.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(res, StandardCharsets.UTF_8)));
            }
            Map<String, List<String>> flat = new HashMap<>();
            for (String key : y.getKeys(true)) {
                if (key.startsWith("meta.") || key.equals("file-version")) {
                    continue;
                }
                Object v = y.get(key);
                if (v instanceof List<?> l) {
                    List<String> lines = new ArrayList<>();
                    for (Object o : l) {
                        lines.add(String.valueOf(o));
                    }
                    flat.put(key, lines);
                } else if (v instanceof String s) {
                    flat.put(key, List.of(s));
                }
            }
            // defaults not yet in the user's file (new keys after an update)
            if (y.getDefaults() != null) {
                for (String key : y.getDefaults().getKeys(true)) {
                    Object v = y.getDefaults().get(key);
                    if (!flat.containsKey(key) && !key.startsWith("meta.") && !key.equals("file-version")) {
                        if (v instanceof List<?> l) {
                            flat.put(key, l.stream().map(String::valueOf).toList());
                        } else if (v instanceof String s) {
                            flat.put(key, List.of(s));
                        }
                    }
                }
            }
            cat.put(lang, flat);
            sc.put(lang, y.getBoolean("meta.small-caps", lang.equals("en")));
            names.put(lang, y.getString("meta.name", lang));
        }
        catalog = cat;
        smallCaps = sc;
        languageNames = names;
        rebuildStyle();
    }

    private void rebuildStyle() {
        var cfg = plugin.getConfig();
        TagResolver.Builder tb = TagResolver.builder();
        ConfigurationSection colors = cfg.getConfigurationSection("colors");
        if (colors != null) {
            for (String key : colors.getKeys(false)) {
                TextColor c = parseColor(colors.getString(key));
                tb.resolver(TagResolver.resolver(key, Tag.styling(c == null ? NamedTextColor.WHITE : c)));
            }
        }
        tb.resolver(TagResolver.resolver("raw", Tag.styling()));
        tags = tb.build();
        String start = cfg.getString("prefix.gradient-start", "#ff5a4d");
        String end = cfg.getString("prefix.gradient-end", "#8b0000");
        if (!HEX.matcher(start).matches()) {
            start = "#ff5a4d";
        }
        if (!HEX.matcher(end).matches()) {
            end = "#8b0000";
        }
        String sep = cfg.getString("prefix.separator-color", "#8a8a8a");
        if (!HEX.matcher(sep).matches()) {
            sep = "#8a8a8a";
        }
        String text = cfg.getString("prefix.text", "ᴜʟᴛʀᴀs");
        String bar = cfg.getString("prefix.separator", "│");
        prefix = mm.deserialize("<gradient:" + start + ":" + end + ">" + MiniMessage.miniMessage().escapeTags(text) + "</gradient><" + sep + ">"
                + MiniMessage.miniMessage().escapeTags(bar));
    }

    private static TextColor parseColor(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        if (s.startsWith("#")) {
            return TextColor.fromHexString(s);
        }
        return NamedTextColor.NAMES.value(s.toLowerCase(Locale.ROOT).replace(' ', '_'));
    }

    // ------------------------------------------------------------------ language helpers

    public String languageOf(UUID uuid) {
        PlayerSettings s = plugin.players().get(uuid);
        return s == null ? plugin.defaultLanguage() : s.language();
    }

    public String languageOf(Player p) {
        return languageOf(p.getUniqueId());
    }

    public boolean arabic(Player p) {
        return languageOf(p).equals("ar");
    }

    public String languageName(String lang) {
        return languageNames.getOrDefault(lang, lang);
    }

    public boolean has(String lang, String key) {
        return catalog.getOrDefault(lang, Map.of()).containsKey(key) || catalog.getOrDefault("en", Map.of()).containsKey(key);
    }

    private List<String> lines(String lang, String key) {
        List<String> l = catalog.getOrDefault(lang, Map.of()).get(key);
        if (l == null) {
            l = catalog.getOrDefault("en", Map.of()).get(key);
        }
        return l == null ? List.of("<muted>" + key) : l;
    }

    // ------------------------------------------------------------------ rendering

    /** Renders a template line; ph is name, value, name, value... Values are plain text (never parsed as markup). */
    public Component line(String lang, String template, Object... ph) {
        String t = template;
        if (Boolean.TRUE.equals(smallCaps.get(lang))) {
            t = SmallCaps.convertTemplate(t);
        }
        TagResolver.Builder rb = TagResolver.builder().resolver(tags).resolver(Placeholder.component("prefix", prefix));
        for (int i = 0; i + 1 < ph.length; i += 2) {
            String name = String.valueOf(ph[i]);
            Object v = ph[i + 1];
            t = t.replace("%" + name + "%", "<" + name + ">");
            rb.resolver(v instanceof Component c ? Placeholder.component(name, c) : Placeholder.unparsed(name, String.valueOf(v)));
        }
        return mm.deserialize(t, rb.build());
    }

    public Component render(String lang, String key, Object... ph) {
        List<Component> parts = new ArrayList<>();
        for (String l : lines(lang, key)) {
            parts.add(line(lang, l, ph));
        }
        return Component.join(JoinConfiguration.newlines(), parts);
    }

    public Component render(Player p, String key, Object... ph) {
        return render(languageOf(p), key, ph);
    }

    /** First line only, for item names, titles and action bars. */
    public Component first(String lang, String key, Object... ph) {
        return line(lang, lines(lang, key).get(0), ph);
    }

    /** GUI text: no italics, never inherits the prefix. */
    public Component gui(String lang, String key, Object... ph) {
        return first(lang, key, ph).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public List<Component> guiLore(String lang, String key, Object... ph) {
        List<Component> out = new ArrayList<>();
        for (String l : lines(lang, key)) {
            if (l.isEmpty()) {
                out.add(Component.empty());
            } else {
                out.add(line(lang, l, ph).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
            }
        }
        return out;
    }

    public String plain(String lang, String key, Object... ph) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(first(lang, key, ph));
    }

    // ------------------------------------------------------------------ sending

    private boolean wants(Player p, MessageChannel ch) {
        PlayerSettings s = plugin.players().get(p.getUniqueId());
        if (s == null || ch == MessageChannel.ALWAYS) {
            return true;
        }
        return s.message(MessageChannel.PLUGIN) && s.message(ch);
    }

    public void send(Player p, String key, MessageChannel ch, Object... ph) {
        if (p != null && p.isOnline() && wants(p, ch)) {
            p.sendMessage(render(p, key, ph));
        }
    }

    /** Error / refusal: never muted. */
    public void error(Player p, String key, Object... ph) {
        send(p, key, MessageChannel.ALWAYS, ph);
    }

    public void info(Player p, String key, Object... ph) {
        send(p, key, MessageChannel.PLUGIN, ph);
    }

    public void send(Audience a, String lang, String key, Object... ph) {
        a.sendMessage(render(lang, key, ph));
    }

    public void actionBar(Player p, String key, MessageChannel ch, Object... ph) {
        if (p.isOnline() && wants(p, ch)) {
            p.sendActionBar(first(languageOf(p), key, ph));
        }
    }

    /** Entry / exit style text, shown where the player prefers (action bar or title). */
    public void positioned(Player p, String key, MessageChannel ch, Object... ph) {
        PlayerSettings s = plugin.players().get(p.getUniqueId());
        if (!p.isOnline() || !wants(p, ch)) {
            return;
        }
        Component c = first(languageOf(p), key, ph);
        if (s != null && s.entryPosition() == com.ultras.claims.settings.EntryPosition.TITLE) {
            p.showTitle(Title.title(Component.empty(), c, Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1400), Duration.ofMillis(400))));
        } else {
            p.sendActionBar(c);
        }
    }
}
