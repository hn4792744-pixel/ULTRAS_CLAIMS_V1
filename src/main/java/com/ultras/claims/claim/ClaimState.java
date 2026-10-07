package com.ultras.claims.claim;

public enum ClaimState {
    /** Protection is running. */
    ACTIVE,
    /** Protection time ran out; only the owner may renew; nobody else may claim the area. */
    GRACE
}
