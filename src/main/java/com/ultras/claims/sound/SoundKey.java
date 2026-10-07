package com.ultras.claims.sound;

import java.util.Locale;

/** Every sound the plugin can play; each is configurable under "sounds:" in config.yml. */
public enum SoundKey {
    SUCCESS(PlayerSoundGroup.SUCCESS),
    ERROR(PlayerSoundGroup.ERROR),
    EXPANSION(PlayerSoundGroup.EXPANSION),
    WARNING(PlayerSoundGroup.WARNING),
    GUI_OPEN(PlayerSoundGroup.GUI),
    GUI_CLICK(PlayerSoundGroup.GUI),
    TOGGLE(PlayerSoundGroup.GUI),
    ENTER(PlayerSoundGroup.ENTER),
    EXIT(PlayerSoundGroup.EXIT),
    ADD_MEMBER(PlayerSoundGroup.SUCCESS),
    REMOVE_MEMBER(PlayerSoundGroup.SUCCESS),
    RENEWAL(PlayerSoundGroup.SUCCESS);

    private final PlayerSoundGroup group;

    SoundKey(PlayerSoundGroup group) {
        this.group = group;
    }

    public PlayerSoundGroup group() {
        return group;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
