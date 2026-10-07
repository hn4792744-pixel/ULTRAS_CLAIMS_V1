package com.ultras.claims.settings;

public enum EntryPosition {
    ACTIONBAR, TITLE;

    public EntryPosition next() {
        return this == ACTIONBAR ? TITLE : ACTIONBAR;
    }
}
