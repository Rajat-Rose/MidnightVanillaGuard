package com.midnightsmp.midnightvanillaguard;

import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MidnightVanillaGuard extends JavaPlugin implements Listener {

    private final Map<UUID, Integer> warningMap = new HashMap<>();
    private final Map<UUID, Long> eatStartTimeMap = new HashMap<>();
    
    private double maxReach;
    private int banHours;
    private String discordLink;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        maxReach = getConfig().getDouble("max-reach", 3.0);
        banHours = getConfig().getInt("ban-duration-hours", 24);
        discordLink = getConfig().getString("discord-appeal-link", "Discord");

        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("MidnightVanillaGuard Ultra-Vanilla Enforcement Enabled!");
    }

    // --- 1. COMBAT CHECKS (Reach, KillAura Angle, Cooldown) ---
    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;

        Entity victim = event.getEntity();
        double distance = attacker.getLocation().distance(victim.getLocation());

        // Check A: Strict Reach Limit (Max 3.0 Blocks)
        if (distance > maxReach) {
            event.setCancelled(true);
            issueWarning(attacker, "Reach Limit Exceeded (" + String.format("%.2f", distance) + " blocks)");
            return;
        }

        // Check B: KillAura Angle Validation (Look Direction)
        double dotProduct = attacker.getLocation().getDirection().dot(
                victim.getLocation().toVector().subtract(attacker.getLocation().toVector()).normalize()
        );
        if (dotProduct < 0.25) { // Attacker is not looking towards victim
            event.setCancelled(true);
            issueWarning(attacker, "Invalid Hit Direction / Multi-Aura Lock");
        }
    }

    // --- 2. MOVEMENT CHECKS (Fly, Speed, Out-of-Bounds) ---
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        if (player.getAllowFlight() || player.isGliding()) return;

        double deltaY = event.getTo().getY() - event.getFrom().getY();
        double deltaXZ = Math.hypot(event.getTo().getX() - event.getFrom().getX(), event.getTo().getZ() - event.getFrom().getZ());

        // Check A: Illegal Flying / Vertical Speed
        if (deltaY > 1.2 && !player.isOp()) {
            player.teleport(event.getFrom());
            issueWarning(player, "Illegal Fly/Vertical Movement");
            return;
        }

        // Check B: Speed / Timer Check (Vanilla Sprint Limit ~0.66 per tick max)
        if (deltaXZ > 0.85 && !player.isSprinting() && !player.isOp()) {
            player.teleport(event.getFrom());
            issueWarning(player, "Speed / Timer Hack Detected");
        }
    }

    // --- 3. EATING / FAST-USE CHECK ---
    @EventHandler
    public void onItemConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        long now = System.currentTimeMillis();
        if (eatStartTimeMap.containsKey(uuid)) {
            long duration = now - eatStartTimeMap.get(uuid);
            if (duration < 1000) { // Vanilla eating takes ~1.6s (32 ticks)
                event.setCancelled(true);
                issueWarning(player, "FastEat / FastUse Hack Detected");
            }
        }
        eatStartTimeMap.put(uuid, now);
    }

    // --- 4. PUNISHMENT WORKFLOW (3 Warns -> 24h Ban) ---
    private void issueWarning(Player player, String reason) {
        UUID uuid = player.getUniqueId();
        int currentWarns = warningMap.getOrDefault(uuid, 0) + 1;
        warningMap.put(uuid, currentWarns);

        if (currentWarns < 3) {
            String kickMsg = ChatColor.RED + "❌ Illegal Modification Detected!\n" +
                    ChatColor.YELLOW + "Reason: " + reason + "\n" +
                    ChatColor.GOLD + "Warning (" + currentWarns + "/3)\n\n" +
                    ChatColor.GRAY + "Note: Pure Vanilla behavior is strictly enforced.\n" +
                    ChatColor.GRAY + "Repeated violations will cause a " + banHours + "h ban.";
            player.kickPlayer(kickMsg);
        } else {
            Date banExpiration = new Date(System.currentTimeMillis() + (banHours * 3600000L));
            String banMsg = ChatColor.RED + "⛔ Banned for " + banHours + " Hours!\n" +
                    ChatColor.YELLOW + "Reason: Reached 3/3 Anti-Cheat Warnings (" + reason + ")\n\n" +
                    ChatColor.AQUA + "For inquiries or appeals, open a ticket on Discord:\n" +
                    ChatColor.WHITE + discordLink;

            Bukkit.getBanList(BanList.Type.NAME).addBan(player.getName(), banMsg, banExpiration, "MidnightVanillaGuard");
            player.kickPlayer(banMsg);
            warningMap.remove(uuid);
        }
    }
}
