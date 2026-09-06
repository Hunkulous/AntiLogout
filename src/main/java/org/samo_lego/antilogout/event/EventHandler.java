package org.samo_lego.antilogout.event;

import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.samo_lego.antilogout.AntiLogout;
import org.samo_lego.antilogout.datatracker.LogoutRules;
import org.samo_lego.antilogout.chunk.CombatDummyChunkLoading;
import org.samo_lego.antilogout.chunk.CombatDummyMobPersistence;

/**
 * Handles all AntiLogout-related events for combat, AFK, and player state.
 * Uses Fabric events to allow configurable combat timeout and custom logic.
 * We do not use vanilla combat tracking directly
 * because we require more control and configuration than vanilla provides.
 */
public class EventHandler {

    private static boolean isCombatEntity(Entity entity) {
        return entity instanceof Player || entity instanceof Enemy || entity instanceof NeutralMob;
    }

    /**
     * Marks both the attacker and the target as "in combat state" if they are players.
     * This is triggered on a player attack event and sets the combat timeout for both parties.
     *
     * @param attacker         the player who attacked
     * @param _level           the world
     * @param _interactionHand the hand used to attack
     * @param target           the targeted entity
     * @param _entityHitResult the hit result
    * @return {@link InteractionResult#PASS} to allow normal event flow
     */
    public static InteractionResult onAttack(Player attacker, Level _level, InteractionHand _interactionHand,
            Entity target, @Nullable EntityHitResult _entityHitResult) {
        if (isCombatEntity(target)) {
            long allowedDc = System.currentTimeMillis() + Math.round(AntiLogout.config.combatLog.combatTimeout * 1000L);

            // Mark living targets that participate in AntiLogout combat tracking.
            if (target instanceof Player && target instanceof LogoutRules logoutTarget) {
                logoutTarget.al_setInCombatUntil(allowedDc);
            }

            // Mark the attacking player for any living-entity combat.
            if (attacker instanceof LogoutRules logoutAttacker) {
                logoutAttacker.al_setInCombatUntil(allowedDc);
            }
        }
        return InteractionResult.PASS;
    }

    /**
     * Disconnects a fake (AFK/dummy) player on death.
     * Ensures that fake players are properly removed from the world when they die.
     *
     * @param deadEntity    the entity that died
     * @param _damageSource the damage source of death
     */
    public static void onDeath(LivingEntity deadEntity, DamageSource _damageSource) {
        if (deadEntity instanceof LogoutRules player && player.al_isFake()) {
            // Remove player from online players
            ServerPlayer serverPlayer = (ServerPlayer) player;
            CombatDummyMobPersistence.release(serverPlayer, "death");
            CombatDummyChunkLoading.release(serverPlayer, "death");
            LogoutRules.DISCONNECTED_PLAYERS.remove(serverPlayer);
            serverPlayer.connection.disconnect(new DisconnectionDetails(Component.empty()));
        }
    }

    /**
     * Marks a player as "in combat state" if the damage source is allowed by config.
     * If the damage source is a projectile shot by a player, the shooter is also marked.
     *
     * @param target       the player who was hurt
     * @param damageSource the damage source
     */
    public static void onHurt(ServerPlayer target, DamageSource damageSource) {
        long allowedDc = System.currentTimeMillis() + Math.round(AntiLogout.config.combatLog.combatTimeout * 1000L);
        if (target != null) {
            boolean trigger;
            Entity damageEntity = damageSource.getEntity();
            if (AntiLogout.config.combatLog.playerHurtOnly) {
                // Only player or player projectile
                trigger = damageEntity instanceof Player ||
                        (damageEntity instanceof Projectile p && p.getOwner() instanceof Player);
            } else {
                trigger = isCombatEntity(damageEntity) ||
                        (damageEntity instanceof Projectile p && isCombatEntity(p.getOwner()));
            }
            if (trigger) {
                ((LogoutRules) target).al_setInCombatUntil(allowedDc);
            }
        }
    }

    /**
     * Sends a stored death message to a player if they died while disconnected but are still present in the world.
     * This ensures the player receives their death message upon rejoining.
     *
     * @param listener the packet listener for the player
     * @param _sender  the packet sender
     * @param _server  the Minecraft server
     */
    public static void onPlayerJoin(ServerGamePacketListenerImpl listener, PacketSender _sender,
            MinecraftServer _server) {
        final Component deathMessage = LogoutRules.SKIPPED_DEATH_MESSAGES.get(listener.player.getUUID());
        if (deathMessage != null) {
            listener.player.sendSystemMessage(deathMessage);
            listener.send(new ClientboundPlayerCombatKillPacket(listener.player.getId(), deathMessage));
            LogoutRules.SKIPPED_DEATH_MESSAGES.remove(listener.player.getUUID());
        }
    }
}
