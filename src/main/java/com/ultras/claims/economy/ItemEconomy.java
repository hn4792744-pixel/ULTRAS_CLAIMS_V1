package com.ultras.claims.economy;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.function.Consumer;

/** Pay with items from the inventory (default: 5 diamonds). */
public final class ItemEconomy implements EconomyProvider {
    private final Material material;
    private final int amount;

    public ItemEconomy(Material material, int amount) {
        this.material = material;
        this.amount = Math.max(1, amount);
    }

    @Override
    public String id() {
        return "items";
    }

    @Override
    public boolean available() {
        return material != null && material.isItem();
    }

    @Override
    public Component describe() {
        return Component.text(amount + " ").append(Component.translatable(material.translationKey()));
    }

    @Override
    public void charge(Player p, Consumer<ChargeResult> done) {
        int have = 0;
        for (ItemStack s : p.getInventory().getStorageContents()) {
            if (s != null && s.getType() == material && !s.hasItemMeta()) {
                have += s.getAmount();
            }
        }
        if (have < amount) {
            done.accept(ChargeResult.INSUFFICIENT);
            return;
        }
        int left = amount;
        ItemStack[] contents = p.getInventory().getStorageContents();
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack s = contents[i];
            if (s == null || s.getType() != material || s.hasItemMeta()) {
                continue;
            }
            int take = Math.min(left, s.getAmount());
            left -= take;
            if (take == s.getAmount()) {
                contents[i] = null;
            } else {
                s.setAmount(s.getAmount() - take);
            }
        }
        p.getInventory().setStorageContents(contents);
        done.accept(ChargeResult.OK);
    }

    @Override
    public void refund(Player p) {
        var left = p.getInventory().addItem(new ItemStack(material, amount));
        for (ItemStack rest : left.values()) {
            p.getWorld().dropItem(p.getLocation(), rest);
        }
    }
}
