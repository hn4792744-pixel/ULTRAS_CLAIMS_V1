package com.ultras.claims.sound;

import java.util.Locale;

/** Sound switches a player can change in /claim_setting. */
public enum PlayerSoundGroup {
    ENTER, EXIT, EXPANSION, WARNING, GUI, SUCCESS, ERROR;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
