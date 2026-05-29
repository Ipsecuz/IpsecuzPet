package org.ipsecuz.pet;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

public class PetListener implements Listener {

    private final IpsecuzPet plugin;

    public PetListener(IpsecuzPet plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent e) {
        Entity victim = e.getEntity();
        Entity damager = e.getDamager();

        // Nếu con bị đánh là Pet (có Metadata pet_owner)
        if (victim.hasMetadata("pet_owner")) {
            String ownerUuidStr = victim.getMetadata("pet_owner").get(0).asString();

            // 1. Nếu người đánh là Player (Chủ)
            if (damager instanceof Player p) {
                if (p.getUniqueId().toString().equals(ownerUuidStr)) {
                    e.setCancelled(true); // CHẶN
                    return;
                }
            }

            // 2. Nếu người đánh là Cung tên/Phép do chủ bắn
            if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) {
                if (p.getUniqueId().toString().equals(ownerUuidStr)) {
                    e.setCancelled(true); // CHẶN
                    return;
                }
            }
        }
    }

    // Chặn Pet chết do cháy, ngạt thở (Optional - muốn pet trâu bò thì để)
    @EventHandler
    public void onEnvironmentalDamage(EntityDamageEvent e) {
        if (e.getEntity().hasMetadata("pet_owner")) {
            // Chỉ chặn mấy cái sát thương môi trường, chừa lại ENTITY_ATTACK để quái còn đánh được
            if (e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK &&
                    e.getCause() != EntityDamageEvent.DamageCause.PROJECTILE) {
                e.setCancelled(true);
            }
        }
    }
}