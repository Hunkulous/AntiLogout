package org.samo_lego.antilogout.chunk;

import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import org.samo_lego.antilogout.AntiLogout;
import org.samo_lego.antilogout.datatracker.LogoutRules;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class CombatDummyChunkLoading {
    private static final Map<UUID, Set<ChunkKey>> dummyChunks = new HashMap<>();
    private static final Map<ChunkKey, Integer> chunkOwners = new HashMap<>();

    private CombatDummyChunkLoading() {
    }

    public static synchronized void retain(ServerPlayer player) {
        UUID playerId = player.getUUID();
        if (dummyChunks.containsKey(playerId) || !LogoutRules.DISCONNECTED_PLAYERS.contains(player)) {
            return;
        }

        ServerLevel level = player.level();
        ChunkPos center = ChunkPos.containing(player.blockPosition());
        Set<ChunkKey> chunks = new HashSet<>();
        ServerChunkCache chunkSource = level.getChunkSource();

        for (int offsetX = -1; offsetX <= 1; offsetX++) {
            for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                ChunkPos chunk = new ChunkPos(center.x() + offsetX, center.z() + offsetZ);
                ChunkKey key = new ChunkKey(level, chunk);
                chunks.add(key);
                int owners = chunkOwners.getOrDefault(key, 0);
                if (owners == 0) {
                    chunkSource.addTicketWithRadius(TicketType.PLAYER_LOADING, chunk, 0);
                }
                chunkOwners.put(key, owners + 1);
            }
        }

        dummyChunks.put(playerId, chunks);
        if (AntiLogout.config.general.debug) {
            AntiLogout.LOGGER.info("[CHUNKS] Loaded 3x3 combat-dummy area for {} at {} {} in {}", player.getName().getString(), center.x(), center.z(), level.dimension());
        }
    }

    public static synchronized void release(ServerPlayer player, String reason) {
        release(player.getUUID(), player.getName().getString(), reason);
    }

    public static synchronized void release(UUID playerId, String playerName, String reason) {
        Set<ChunkKey> chunks = dummyChunks.remove(playerId);
        if (chunks == null) {
            if (AntiLogout.config.general.debug) {
                AntiLogout.LOGGER.info("[CHUNKS] Ignoring duplicate cleanup for dummy {} reason={}", playerId, reason);
            }
            return;
        }

        for (ChunkKey key : chunks) {
            int owners = chunkOwners.getOrDefault(key, 0) - 1;
            if (owners <= 0) {
                key.level().getChunkSource().removeTicketWithRadius(TicketType.PLAYER_LOADING, key.chunk(), 0);
                chunkOwners.remove(key);
            } else {
                chunkOwners.put(key, owners);
            }
        }

        if (AntiLogout.config.general.debug) {
            AntiLogout.LOGGER.info("[CHUNKS] Released combat-dummy area for {} reason={} chunks={}", playerName, reason, chunks.size());
        }
    }

    public static synchronized void clear() {
        for (ChunkKey key : chunkOwners.keySet()) {
            key.level().getChunkSource().removeTicketWithRadius(TicketType.PLAYER_LOADING, key.chunk(), 0);
        }
        dummyChunks.clear();
        chunkOwners.clear();
    }

    private record ChunkKey(ServerLevel level, ChunkPos chunk) {
    }
}
