package com.ultras.claims.economy;

import com.ultras.claims.UltrasClaims;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * "UC" economy driven by console commands: a check command whose reply is read with a regex, and a take command.
 * After taking, the balance is read again (verify-after-take) so a broken take command can never give free expansions.
 */
public final class CommandEconomy implements EconomyProvider {
    private final UltrasClaims plugin;
    private final double cost;
    private final String checkCommand;
    private final String takeCommand;
    private final String refundCommand;
    private final String costFormat;
    private final boolean verify;
    private final int waitTicks;
    private final Pattern balance;

    public CommandEconomy(UltrasClaims plugin, double cost, String check, String take, String refund, String regex,
                          String costFormat, boolean verify, int waitTicks) {
        this.plugin = plugin;
        this.cost = cost;
        this.checkCommand = check;
        this.takeCommand = take;
        this.refundCommand = refund;
        this.costFormat = costFormat;
        this.verify = verify;
        this.waitTicks = Math.max(1, waitTicks);
        Pattern p = null;
        try {
            p = Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            plugin.getLogger().warning("economy.uc.balance-regex is not a valid regex: " + e.getMessage());
        }
        this.balance = p;
    }

    @Override
    public String id() {
        return "command";
    }

    @Override
    public boolean available() {
        return balance != null && !checkCommand.isBlank() && !takeCommand.isBlank();
    }

    @Override
    public Component describe() {
        return Component.text(costFormat.replace("{amount}", amountText(cost)));
    }

    private static String amountText(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    private void readBalance(Player p, DoubleConsumer cb) {
        StringBuilder out = new StringBuilder();
        CommandSender sender = Bukkit.createCommandSender(c -> out.append(PlainTextComponentSerializer.plainText().serialize(c)).append('\n'));
        try {
            Bukkit.dispatchCommand(sender, fill(checkCommand, p));
        } catch (RuntimeException e) {
            plugin.getLogger().warning("economy.uc.check-command failed: " + e);
            cb.accept(-1);
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Matcher m = balance.matcher(out);
            if (!m.find() || m.groupCount() < 1 || m.group(1) == null) {
                cb.accept(-1);
                return;
            }
            try {
                cb.accept(Double.parseDouble(m.group(1).replace(",", "").replace(" ", "")));
            } catch (NumberFormatException e) {
                cb.accept(-1);
            }
        }, waitTicks);
    }

    private String fill(String template, Player p) {
        return template.replace("{player}", p.getName()).replace("{amount}", amountText(cost));
    }

    @Override
    public void charge(Player p, Consumer<ChargeResult> done) {
        if (!available()) {
            done.accept(ChargeResult.UNAVAILABLE);
            return;
        }
        readBalance(p, before -> {
            if (before < 0) {
                done.accept(ChargeResult.UNAVAILABLE);
            } else if (before < cost) {
                done.accept(ChargeResult.INSUFFICIENT);
            } else {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), fill(takeCommand, p));
                if (!verify) {
                    done.accept(ChargeResult.OK);
                    return;
                }
                readBalance(p, after -> {
                    if (after >= 0 && after <= before - cost + 0.0001) {
                        done.accept(ChargeResult.OK);
                    } else {
                        plugin.getLogger().severe("economy.uc: the balance of " + p.getName() + " did not drop after the take command - expansion refused");
                        done.accept(ChargeResult.UNAVAILABLE);
                    }
                });
            }
        });
    }

    @Override
    public void refund(Player p) {
        if (refundCommand == null || refundCommand.isBlank()) {
            plugin.getLogger().severe("economy.uc.refund-command is empty: could not refund " + p.getName());
            return;
        }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), fill(refundCommand, p));
    }
}
