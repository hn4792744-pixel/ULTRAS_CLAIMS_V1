package com.ultras.claims.economy;

import com.ultras.claims.UltrasClaims;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.function.Consumer;

/** Vault economy through reflection, so the plugin builds and runs without the Vault jar. */
public final class VaultEconomy implements EconomyProvider {
    private final UltrasClaims plugin;
    private final double cost;
    private final String symbol;
    private Object economy;
    private Method has;
    private Method withdraw;
    private Method deposit;
    private Method success;

    @SuppressWarnings({"unchecked", "rawtypes"})
    public VaultEconomy(UltrasClaims plugin, double cost, String symbol) {
        this.plugin = plugin;
        this.cost = cost;
        this.symbol = symbol;
        try {
            if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
                return;
            }
            Class<?> econ = Class.forName("net.milkbowl.vault.economy.Economy");
            RegisteredServiceProvider rsp = Bukkit.getServicesManager().getRegistration((Class) econ);
            if (rsp == null) {
                return;
            }
            economy = rsp.getProvider();
            has = econ.getMethod("has", OfflinePlayer.class, double.class);
            withdraw = econ.getMethod("withdrawPlayer", OfflinePlayer.class, double.class);
            deposit = econ.getMethod("depositPlayer", OfflinePlayer.class, double.class);
            success = Class.forName("net.milkbowl.vault.economy.EconomyResponse").getMethod("transactionSuccess");
        } catch (ReflectiveOperationException | RuntimeException e) {
            plugin.getLogger().warning("Vault economy could not be hooked: " + e.getMessage());
            economy = null;
        }
    }

    @Override
    public String id() {
        return "vault";
    }

    @Override
    public boolean available() {
        return economy != null;
    }

    @Override
    public Component describe() {
        return Component.text(format(cost));
    }

    public String format(double amount) {
        DecimalFormat df = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.US));
        return symbol + df.format(amount);
    }

    @Override
    public void charge(Player p, Consumer<ChargeResult> done) {
        if (economy == null) {
            done.accept(ChargeResult.UNAVAILABLE);
            return;
        }
        try {
            if (!(Boolean) has.invoke(economy, p, cost)) {
                done.accept(ChargeResult.INSUFFICIENT);
                return;
            }
            Object response = withdraw.invoke(economy, p, cost);
            done.accept((Boolean) success.invoke(response) ? ChargeResult.OK : ChargeResult.INSUFFICIENT);
        } catch (ReflectiveOperationException | RuntimeException e) {
            plugin.getLogger().warning("Vault charge failed: " + e);
            done.accept(ChargeResult.UNAVAILABLE);
        }
    }

    @Override
    public void refund(Player p) {
        if (economy == null) {
            return;
        }
        try {
            deposit.invoke(economy, p, cost);
        } catch (ReflectiveOperationException | RuntimeException e) {
            plugin.getLogger().severe("Could not refund " + p.getName() + " " + format(cost) + ": " + e);
        }
    }
}
