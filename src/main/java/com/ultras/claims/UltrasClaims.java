package com.ultras.claims;

import com.ultras.claims.border.BorderService;
import com.ultras.claims.border.PlusListener;
import com.ultras.claims.cabin.CabinService;
import com.ultras.claims.claim.ClaimManager;
import com.ultras.claims.claim.HeadService;
import com.ultras.claims.command.ClaimCommand;
import com.ultras.claims.command.ClaimSettingCommand;
import com.ultras.claims.economy.EconomyService;
import com.ultras.claims.expansion.ExpansionService;
import com.ultras.claims.gui.GuiService;
import com.ultras.claims.gui.MenuListener;
import com.ultras.claims.hologram.HologramService;
import com.ultras.claims.language.Messages;
import com.ultras.claims.listener.EntryListener;
import com.ultras.claims.listener.HeadListener;
import com.ultras.claims.listener.PlayerListener;
import com.ultras.claims.listener.ProtectionListener;
import com.ultras.claims.listener.WandListener;
import com.ultras.claims.logging.ClaimLog;
import com.ultras.claims.map.MapService;
import com.ultras.claims.member.MemberService;
import com.ultras.claims.notification.NotificationService;
import com.ultras.claims.protection.ProtectionService;
import com.ultras.claims.settings.PlayerSettingsService;
import com.ultras.claims.sound.SoundService;
import com.ultras.claims.storage.Storage;
import com.ultras.claims.util.Keys;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Ultras_Claims_v1 - entry point. Wires the services together; contains no game logic itself. */
public final class UltrasClaims extends JavaPlugin {
    private Keys keys;
    private Storage storage;
    private ClaimLog claimLog;
    private Messages messages;
    private SoundService sounds;
    private PlayerSettingsService players;
    private ClaimManager claims;
    private HeadService heads;
    private EconomyService economy;
    private ProtectionService protection;
    private NotificationService notifications;
    private MemberService members;
    private ExpansionService expansion;
    private BorderService borders;
    private CabinService cabin;
    private HologramService holograms;
    private MapService maps;
    private GuiService gui;
    private final Set<String> usedTokens = new HashSet<>();

    @Override
    public void onEnable() {
        try {
            saveDefaultConfig();
            getDataFolder().mkdirs();
            keys = new Keys(this);
            storage = new Storage(getDataFolder().toPath().resolve("claims.db"), getLogger());
            storage.open();
            usedTokens.addAll(storage.loadUsedTokens());

            claimLog = new ClaimLog(this);
            claimLog.reload();
            players = new PlayerSettingsService(this);
            players.load();
            messages = new Messages(this);
            messages.load();
            sounds = new SoundService(this);
            sounds.reload();
            economy = new EconomyService(this);
            economy.reload();
            heads = new HeadService(this);
            protection = new ProtectionService(this);
            notifications = new NotificationService(this);
            members = new MemberService(this);
            expansion = new ExpansionService(this);
            borders = new BorderService(this);
            cabin = new CabinService(this);
            holograms = new HologramService(this);
            maps = new MapService(this);
            gui = new GuiService(this);
            claims = new ClaimManager(this);
            claims.load();
            cabin.start();
        } catch (Exception e) {
            getLogger().log(java.util.logging.Level.SEVERE, "Could not start Ultras_Claims_v1 - disabling", e);
            if (storage != null) {
                storage.close();
                storage = null;
            }
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        borders.purgeOrphans();
        holograms.start();
        maps.start();

        var pm = getServer().getPluginManager();
        pm.registerEvents(new HeadListener(this), this);
        pm.registerEvents(new ProtectionListener(this), this);
        pm.registerEvents(new EntryListener(this), this);
        pm.registerEvents(new PlayerListener(this), this);
        pm.registerEvents(new WandListener(this), this);
        pm.registerEvents(new PlusListener(this), this);
        pm.registerEvents(new MenuListener(this), this);

        ClaimCommand cc = new ClaimCommand(this);
        bind("claim", cc, cc);
        ClaimSettingCommand sc = new ClaimSettingCommand(this);
        bind("claim_setting", sc, sc);
        getLogger().info("Ultras_Claims_v1 enabled: " + claims.all().size() + " claims, economy=" + economy.providerId()
                + ", cabin=" + (cabin.enabled() ? "on" : "off"));
    }

    private void bind(String name, org.bukkit.command.CommandExecutor ex, org.bukkit.command.TabCompleter tab) {
        PluginCommand c = getCommand(name);
        if (c != null) {
            c.setExecutor(ex);
            c.setTabCompleter(tab);
        }
    }

    @Override
    public void onDisable() {
        if (gui != null) {
            gui.closeAll();
        }
        if (cabin != null) {
            cabin.stop();
        }
        if (borders != null) {
            borders.hideAll();
            borders.purgeOrphans();
        }
        if (holograms != null) {
            holograms.stop();
        }
        if (maps != null) {
            maps.stop();
        }
        if (claimLog != null) {
            claimLog.log("DISABLE", "plugin disabled");
            claimLog.close();
        }
        if (storage != null) {
            storage.close();
        }
    }

    /** /claim reload: files and services, never the database. */
    public void reloadAll() {
        reloadConfig();
        claimLog.reload();
        messages.load();
        sounds.reload();
        economy.reload();
        cabin.reload();
        gui.reload();
        holograms.reload();
        maps.reload();
        borders.hideAll();
        borders.purgeOrphans();
    }

    public boolean hasBypass(Player p) {
        return p.hasPermission("ultrasclaims.admin.bypass");
    }

    public String defaultLanguage() {
        String l = getConfig().getString("language.default", "en").toLowerCase(Locale.ROOT);
        return Messages.LANGUAGES.contains(l) ? l : "en";
    }

    public boolean worldDisabled(String world) {
        return getConfig().getStringList("disabled-worlds").contains(world);
    }

    // ---- head tokens (anti duplication)
    public boolean tokenUsed(String token) {
        return token != null && usedTokens.contains(token);
    }

    public void useToken(String token, String claimId) {
        if (token != null && usedTokens.add(token)) {
            storage.markTokenUsed(token, claimId);
        }
    }

    public Keys keys() { return keys; }
    public Storage storage() { return storage; }
    public ClaimLog log() { return claimLog; }
    public Messages messages() { return messages; }
    public SoundService sounds() { return sounds; }
    public PlayerSettingsService players() { return players; }
    public ClaimManager claims() { return claims; }
    public HeadService heads() { return heads; }
    public EconomyService economy() { return economy; }
    public ProtectionService protection() { return protection; }
    public NotificationService notifications() { return notifications; }
    public MemberService members() { return members; }
    public ExpansionService expansion() { return expansion; }
    public BorderService borders() { return borders; }
    public CabinService cabin() { return cabin; }
    public HologramService holograms() { return holograms; }
    public MapService maps() { return maps; }
    public GuiService gui() { return gui; }
}
