package com.ultras.claims.economy;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.function.Consumer;

/** A way to pay for an expansion. The callback always runs on the server thread. */
public interface EconomyProvider {
    String id();

    boolean available();

    Component describe();

    /** Takes the price from the player, or reports why not. Must never take money and report failure. */
    void charge(Player p, Consumer<ChargeResult> done);

    /** Gives the price back (used when an expansion fails after payment). */
    void refund(Player p);
}
