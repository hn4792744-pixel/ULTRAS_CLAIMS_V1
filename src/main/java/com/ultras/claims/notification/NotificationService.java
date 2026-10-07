package com.ultras.claims.notification;

import com.ultras.claims.UltrasClaims;
import com.ultras.claims.claim.Claim;
import com.ultras.claims.claim.NotificationKey;
import com.ultras.claims.language.MessageChannel;
import com.ultras.claims.settings.PlayerSettings;
import com.ultras.claims.sound.SoundKey;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Sends claim notifications to the owner and members who want them (claim switch AND personal switch). */
public final class NotificationService {
    private final UltrasClaims plugin;

    public NotificationService(UltrasClaims plugin) {
        this.plugin = plugin;
    }

    public Set<UUID> audience(Claim c) {
        Set<UUID> ids = new HashSet<>(c.members().keySet());
        ids.add(c.ownerUuid());
        return ids;
    }

    /**
     * @param exclude a player who already got a direct message (may be null)
     * @return how many players were notified
     */
    public int notify(Claim c, NotificationKey key, String messageKey, MessageChannel channel, SoundKey sound, UUID exclude, Object... ph) {
        if (!c.notifies(key)) {
            return 0;
        }
        int sent = 0;
        for (UUID id : audience(c)) {
            if (id.equals(exclude)) {
                continue;
            }
            Player p = Bukkit.getPlayer(id);
            if (p == null) {
                continue;
            }
            PlayerSettings ps = plugin.players().get(id);
            if (!ps.claimNotifications()) {
                continue;
            }
            plugin.messages().send(p, messageKey, channel, ph);
            if (sound != null) {
                plugin.sounds().play(p, sound);
            }
            sent++;
        }
        return sent;
    }
}
