package com.ultras.claims.language;

/**
 * Which player-side message switch a message belongs to. ALWAYS (errors) is never muted by the player.
 */
public enum MessageChannel {
    ALWAYS, PLUGIN, CLAIM, ENTRY, EXIT, EXPANSION, WARNING
}
