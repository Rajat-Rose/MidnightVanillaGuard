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
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MidnightVanillaGuard extends JavaPlugin implements Listener {

    private final Map<UUID, Integer> warningMap = new HashMap<>();
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
        getLogger().info("MidnightVanillaGuard Anti-Cheat successfully enabled!");
    }

    // --- 1. Reach & KillAura Look Validation ---
    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;

        Entity victim = event.getEntity();
        double distance = attacker.getLocation().distance(victim.getLocation());

        // Check 1: Exceeding 3.0 Blocks Reach
        if (distance > maxReach) {
            event.setCancelled(true);
            issueWarning(attacker, "Reach Limit Exceeded (" + String.format("%.2f", distance) + " blocks)");
            return;
        }

        // Check 2: Directional Look Validation
        double dotProduct = attacker.getLocation().getDirection().dot(
                victim.getLocation().toVector().subtract(attacker.getLocation().toVector()).normalize()
        );
        if (dotProduct < 0.2) { 
            event.setCancelled(true);
            issueWarning(attacker, "Invalid Hit Direction (KillAura Target Lock)");
        }
    }

    // --- 2. Flight Check ---
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        if (player.getAllowFlight() || player.isGliding()) return;

        if (event.getTo().getY() - event.getFrom().getY() > 1.5 && !player.isOp()) {
            player.teleport(event.getFrom());
            issueWarning(player, "Illegal Fly/Jump Detected");
        }
    }

    // --- 3. Punishment Workflow ---
    private void issueWarning(Player player, String reason) {
        UUID uuid = player.getUniqueId();
        int currentWarns = warningMap.getOrDefault(uuid, 0) + 1;
        warningMap.put(uuid, currentWarns);

        if (currentWarns < 3) {
            String kickMsg = ChatColor.RED + "❌ Illegal Modification Detected!\n" +
                    ChatColor.YELLOW + "Reason: " + reason + "\n" +
                    ChatColor.GOLD + "Warning (" + currentWarns + "/3)\n\n" +
                    ChatColor.GRAY + "Repeated violations will result in a " + banHours + "h ban.";
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
