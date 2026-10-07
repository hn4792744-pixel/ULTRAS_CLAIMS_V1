package com.ultras.claims.economy;

import com.ultras.claims.UltrasClaims;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.function.Consumer;

/** Chooses the configured payment method and exposes it to expansion and the GUIs. */
public final class EconomyService {
    private final UltrasClaims plugin;
    private volatile EconomyProvider provider;
    private volatile boolean enabled;
    private volatile boolean adminBypassCost;

    public EconomyService(UltrasClaims plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        FileConfiguration c = plugin.getConfig();
        enabled = c.getBoolean("economy.enabled", true);
        adminBypassCost = c.getBoolean("economy.admin-bypass-cost", false);
        String type = c.getString("economy.type", "vault").toLowerCase(Locale.ROOT);
        EconomyProvider p = null;
        switch (type) {
            case "vault" -> p = new VaultEconomy(plugin, c.getDouble("economy.expansion-cost", 5000), c.getString("economy.symbol", "$"));
            case "command" -> {
                if (c.getBoolean("economy.uc.enabled", false)) {
                    p = new CommandEconomy(plugin, c.getDouble("economy.uc.expansion-cost", 5000),
                            c.getString("economy.uc.check-command", ""), c.getString("economy.uc.take-command", ""),
                            c.getString("economy.uc.refund-command", ""), c.getString("economy.uc.balance-regex", "([0-9][0-9,.]*)"),
                            c.getString("economy.uc.cost-format", "{amount} UC"), c.getBoolean("economy.uc.verify-after-take", true),
                            c.getInt("economy.uc.check-wait-ticks", 4));
                } else {
                    plugin.getLogger().warning("economy.type is 'command' but economy.uc.enabled is false - expansions stay blocked");
                }
            }
            case "items" -> {
                Material m = Material.matchMaterial(c.getString("economy.items.material", "DIAMOND"));
                if (m == null) {
                    plugin.getLogger().warning("economy.items.material is not a valid material");
                } else {
                    p = new ItemEconomy(m, c.getInt("economy.items.amount", 5));
                }
            }
            default -> plugin.getLogger().warning("economy.type '" + type + "' is unknown (vault | command | items)");
        }
        provider = p;
        if (enabled && (p == null || !p.available())) {
            plugin.getLogger().warning("Economy is enabled but the '" + type + "' provider is not available: expansions are blocked until it is (admins with bypass may still expand if economy.admin-bypass-cost is true).");
        }
    }

    public boolean enabled() {
        return enabled;
    }

    /** True when this actor pays nothing. */
    public boolean free(Player p) {
        return !enabled || (adminBypassCost && plugin.hasBypass(p));
    }

    public boolean usable() {
        EconomyProvider pr = provider;
        return !enabled || (pr != null && pr.available());
    }

    public Component cost(Player p) {
        EconomyProvider pr = provider;
        if (free(p) || pr == null) {
            return Component.text("0");
        }
        return pr.describe();
    }

    public void charge(Player p, Consumer<ChargeResult> done) {
        if (free(p)) {
            done.accept(ChargeResult.OK);
            return;
        }
        EconomyProvider pr = provider;
        if (pr == null || !pr.available()) {
            done.accept(ChargeResult.UNAVAILABLE);
            return;
        }
        pr.charge(p, done);
    }

    public void refund(Player p) {
        EconomyProvider pr = provider;
        if (!free(p) && pr != null) {
            pr.refund(p);
        }
    }

    public String providerId() {
        EconomyProvider pr = provider;
        return !enabled ? "none" : pr == null ? "unavailable" : pr.id();
    }
}
