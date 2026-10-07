package com.ultras.claims.core;

/**
 * The one place that decides who may do what inside a claim.
 * Priority: admin bypass, owner, member permission, claim visitor setting, default deny.
 */
public final class AccessRules {
    private AccessRules() {
    }

    /**
     * @param claimProtected     false while the protection is expired (grace period) - everything is allowed then
     * @param bypass             ultrasclaims.admin.bypass (and bypass mode on)
     * @param owner              the actor owns the claim
     * @param memberHasPermission the actor is a member holding the matching permission
     * @param visitorAllows      the matching claim setting is OFF (visitors may do it)
     */
    public static boolean allowed(boolean claimProtected, boolean bypass, boolean owner, boolean memberHasPermission, boolean visitorAllows) {
        if (!claimProtected) {
            return true;
        }
        if (bypass || owner) {
            return true;
        }
        if (memberHasPermission) {
            return true;
        }
        return visitorAllows; // default deny: false unless a setting explicitly allows visitors
    }
}
