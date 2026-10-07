package com.ultras.claims.claim;

import java.util.Locale;

/** Per-claim notification switches. */
public enum NotificationKey {
    CABIN_WARNING, CABIN_EMPTY, CABIN_RENEWED, MEMBER_JOINED, MEMBER_LEFT, CLAIM_EXPANDED,
    CLAIM_EXPIRED, CLAIM_RENEWED, HEAD_BROKEN, VISITOR_ENTERED, VISITOR_LEFT;

    public String id() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public static NotificationKey fromId(String id) {
        for (NotificationKey k : values()) {
            if (k.id().equalsIgnoreCase(id) || k.name().equalsIgnoreCase(id)) {
                return k;
            }
        }
        return null;
    }
}
