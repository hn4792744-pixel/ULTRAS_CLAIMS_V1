package com.ultras.claims.member;

import com.ultras.claims.UltrasClaims;
import com.ultras.claims.claim.Claim;
import com.ultras.claims.claim.ClaimMember;
import com.ultras.claims.claim.MemberPermission;
import com.ultras.claims.claim.NotificationKey;
import com.ultras.claims.language.MessageChannel;
import com.ultras.claims.sound.SoundKey;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.EnumSet;
import java.util.UUID;

/** Adding/removing members and changing their permissions, with every limit checked in one place. */
public final class MemberService {
    public enum AddResult {
        OK, NOT_ALLOWED, FULL, TARGET_FULL, DISABLED_INVITES, ALREADY, SELF, UNKNOWN_PLAYER, OWNER
    }

    private final UltrasClaims plugin;

    public MemberService(UltrasClaims plugin) {
        this.plugin = plugin;
    }

    /** Owner, or a member with MANAGE_MEMBERS. Admin bypass always. */
    public boolean canManage(Player actor, Claim c) {
        return plugin.hasBypass(actor) || c.isOwner(actor.getUniqueId()) || c.hasPermission(actor.getUniqueId(), MemberPermission.MANAGE_MEMBERS);
    }

    public AddResult add(Player actor, Claim c, UUID target, String targetName) {
        if (!canManage(actor, c)) {
            return AddResult.NOT_ALLOWED;
        }
        if (target.equals(c.ownerUuid())) {
            return target.equals(actor.getUniqueId()) ? AddResult.SELF : AddResult.OWNER;
        }
        if (c.isMember(target)) {
            return AddResult.ALREADY;
        }
        if (c.members().size() >= plugin.getConfig().getInt("limits.max-members-per-claim", 4)) {
            return AddResult.FULL;
        }
        if (plugin.claims().memberOfCount(target) >= plugin.getConfig().getInt("limits.max-claims-member-of", 4)) {
            return AddResult.TARGET_FULL;
        }
        if (!plugin.players().get(target).claimInvitations() && !plugin.hasBypass(actor)) {
            return AddResult.DISABLED_INVITES;
        }
        EnumSet<MemberPermission> perms = plugin.claims().defaultPermissions();
        c.members().put(target, new ClaimMember(target, targetName, perms, System.currentTimeMillis()));
        plugin.claims().memberAdded(c, target);
        plugin.claims().save(c);
        plugin.log().log("MEMBER_ADDED", "claim=" + c.id() + " by=" + actor.getName() + " member=" + targetName + " (" + target + ")");
        Player online = Bukkit.getPlayer(target);
        if (online != null) {
            plugin.messages().send(online, "member-you-were-added", MessageChannel.CLAIM, "owner", c.ownerName());
        }
        plugin.notifications().notify(c, NotificationKey.MEMBER_JOINED, "notify-member-joined", MessageChannel.CLAIM, SoundKey.ADD_MEMBER,
                actor.getUniqueId(), "player", targetName);
        return AddResult.OK;
    }

    /** Remove by owner/manager, or the member leaving on their own ({@code actor} == member). */
    public boolean remove(Player actor, Claim c, UUID member) {
        ClaimMember m = c.members().get(member);
        if (m == null) {
            return false;
        }
        boolean self = actor.getUniqueId().equals(member);
        if (!self && !canManage(actor, c)) {
            return false;
        }
        // A manager who is only a member cannot remove other managers - only the owner (or an admin) can.
        if (!self && !c.isOwner(actor.getUniqueId()) && !plugin.hasBypass(actor) && m.permissions().contains(MemberPermission.MANAGE_MEMBERS)) {
            return false;
        }
        c.members().remove(member);
        plugin.claims().memberRemoved(c, member);
        plugin.claims().save(c);
        plugin.log().log("MEMBER_REMOVED", "claim=" + c.id() + " by=" + actor.getName() + " member=" + m.name() + " (" + member + ")");
        Player online = Bukkit.getPlayer(member);
        if (online != null && !self) {
            plugin.messages().send(online, "member-you-were-removed", MessageChannel.CLAIM, "owner", c.ownerName());
        }
        plugin.notifications().notify(c, NotificationKey.MEMBER_LEFT, "notify-member-left", MessageChannel.CLAIM, SoundKey.REMOVE_MEMBER,
                actor.getUniqueId(), "player", m.name());
        return true;
    }

    /** Owner (or admin) changes one permission; a manager may not grant MANAGE_MEMBERS/BREAK_HEAD. */
    public boolean setPermission(Player actor, Claim c, UUID member, MemberPermission perm, boolean value) {
        ClaimMember m = c.members().get(member);
        if (m == null) {
            return false;
        }
        boolean owner = c.isOwner(actor.getUniqueId()) || plugin.hasBypass(actor);
        if (!owner) {
            return false; // only the owner manages member permissions
        }
        if (value) {
            m.permissions().add(perm);
        } else {
            m.permissions().remove(perm);
        }
        plugin.claims().save(c);
        plugin.log().log("PERMISSION_CHANGED", "claim=" + c.id() + " by=" + actor.getName() + " member=" + m.name() + " " + perm.id() + "=" + value);
        return true;
    }

    public OfflinePlayer resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        OfflinePlayer op = Bukkit.getOfflinePlayerIfCached(name);
        return op != null && op.hasPlayedBefore() ? op : null;
    }
}
