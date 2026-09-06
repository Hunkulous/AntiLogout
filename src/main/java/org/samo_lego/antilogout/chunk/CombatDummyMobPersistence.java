package org.samo_lego.antilogout.chunk;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import org.samo_lego.antilogout.AntiLogout;
import org.samo_lego.antilogout.datatracker.LogoutRules;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class CombatDummyMobPersistence {
    private static final Map<UUID, Set<UUID>> dummyMobs = new HashMap<>();
    private static final Map<UUID, MobRecord> mobs = new HashMap<>();

    private CombatDummyMobPersistence() {
    }

    public static synchronized void retain(ServerPlayer player) {
        UUID dummyId = player.getUUID();
        if (!LogoutRules.DISCONNECTED_PLAYERS.contains(player)) {
            return;
        }
        if (dummyMobs.containsKey(dummyId)) {
            if (AntiLogout.config.general.debug) {
                AntiLogout.LOGGER.info("[MOBS] Ignoring duplicate retain dummy {}", dummyId);
            }
            return;
        }

        ServerLevel level = player.level();
        var position = player.position();
        ChunkPos center = ChunkPos.containing(player.blockPosition());
        AABB bounds = new AABB(
                position.x - 24.0D, position.y - 24.0D, position.z - 24.0D,
                position.x + 24.0D, position.y + 24.0D, position.z + 24.0D);
        Set<UUID> tracked = new HashSet<>();
        List<? extends Entity> loadedEntities = level.getEntities(EntityTypeTest.forClass(Entity.class),
            entity -> entity.getBoundingBox().intersects(bounds));
        int hostileFound = 0;
        int alreadyPersistent = 0;
        int changed = 0;

        for (Entity entity : loadedEntities) {
            if (!(entity instanceof Mob mob) || !(mob instanceof Enemy)) {
            continue;
            }
            hostileFound++;
            UUID mobId = mob.getUUID();
            MobRecord record = mobs.get(mobId);
            if (record == null) {
                boolean originallyPersistent = mob.isPersistenceRequired();
                record = new MobRecord(mob, originallyPersistent, new HashSet<>());
                mobs.put(mobId, record);
                if (!originallyPersistent) {
                    mob.setPersistenceRequired();
                    record.changedByAntiLogout = true;
                    changed++;
                    if (AntiLogout.config.general.debug) {
                        AntiLogout.LOGGER.info("[MOBS] Captured mob {} persistentBefore=false persistentAfter={} dummy={}", mobId, mob.isPersistenceRequired(), dummyId);
                    }
                } else {
                    alreadyPersistent++;
                    if (AntiLogout.config.general.debug) {
                        AntiLogout.LOGGER.info("[MOBS] Captured mob {} persistentBefore=true persistentAfter=true dummy={}", mobId, dummyId);
                    }
                }
            }
            record.owners.add(dummyId);
            tracked.add(mobId);
        }

        dummyMobs.put(dummyId, tracked);
        if (AntiLogout.config.general.debug) {
            AntiLogout.LOGGER.info("[MOBS] Dummy {} ({}) scan: dimension={} logout={} centerChunk={} bounds=48x48x48 loadedEntities={} hostileFound={} alreadyPersistent={} changed={}",
                    dummyId, player.getName().getString(), level.dimension(), player.blockPosition(),
                    center, loadedEntities.size(), hostileFound, alreadyPersistent, changed);
            AntiLogout.LOGGER.info("[MOBS] Dummy {} tracked hostile mob UUIDs: {}", dummyId, tracked);
        }
    }

    public static synchronized void release(ServerPlayer player, String reason) {
        release(player.getUUID(), reason);
    }

    public static synchronized void release(UUID dummyId, String reason) {
        Set<UUID> tracked = dummyMobs.remove(dummyId);
        if (tracked == null) {
            if (AntiLogout.config.general.debug) {
                AntiLogout.LOGGER.info("[MOBS] Ignoring duplicate cleanup dummy {} reason={}", dummyId, reason);
            }
            return;
        }

        int restored = 0;
        int removed = 0;
        for (UUID mobId : tracked) {
            MobRecord record = mobs.get(mobId);
            if (record == null) {
                removed++;
                continue;
            }
            record.owners.remove(dummyId);
            if (record.owners.isEmpty()) {
                if (record.changedByAntiLogout && !record.mob.isRemoved()) {
                    boolean previous = record.mob.isPersistenceRequired();
                    ((TemporaryMobPersistence) record.mob).antilogout_setPersistenceRequired(record.originallyPersistent);
                    restored++;
                    if (AntiLogout.config.general.debug) {
                        AntiLogout.LOGGER.info("[MOBS] Restored mob {} persistence={} (was {}) reason={}", mobId, record.mob.isPersistenceRequired(), previous, reason);
                    }
                } else if (record.mob.isRemoved()) {
                    removed++;
                }
                mobs.remove(mobId);
            }
        }

        if (AntiLogout.config.general.debug) {
            AntiLogout.LOGGER.info("[MOBS] Dummy {} cleanup ({}): tracked={} restored={} removed={}",
                    dummyId, reason, tracked.size(), restored, removed);
        }
    }

    public static synchronized void cleanupStale(Set<ServerPlayer> activeDummies) {
        Set<UUID> activeIds = activeDummies.stream().map(ServerPlayer::getUUID).collect(java.util.stream.Collectors.toSet());
        for (UUID dummyId : new HashSet<>(dummyMobs.keySet())) {
            if (!activeIds.contains(dummyId)) {
                releaseWithoutPlayer(dummyId, "watchdog");
            }
        }
        Set<UUID> removedMobIds = new HashSet<>();
        for (Map.Entry<UUID, MobRecord> entry : mobs.entrySet()) {
            if (entry.getValue().mob.isRemoved()) {
                removedMobIds.add(entry.getKey());
            }
        }
        if (!removedMobIds.isEmpty()) {
            mobs.keySet().removeAll(removedMobIds);
            for (Set<UUID> tracked : dummyMobs.values()) {
                tracked.removeAll(removedMobIds);
            }
            if (AntiLogout.config.general.debug) {
                AntiLogout.LOGGER.info("[MOBS] Watchdog removed {} dead mob records", removedMobIds.size());
            }
        }
    }

    public static synchronized void clear() {
        for (MobRecord record : mobs.values()) {
            if (record.changedByAntiLogout && !record.mob.isRemoved()) {
                ((TemporaryMobPersistence) record.mob).antilogout_setPersistenceRequired(record.originallyPersistent);
            }
        }
        dummyMobs.clear();
        mobs.clear();
    }

    private static void releaseWithoutPlayer(UUID dummyId, String reason) {
            Set<UUID> tracked = dummyMobs.remove(dummyId);
            if (tracked == null) {
                return;
            }
            int restored = 0;
            int removed = 0;
            for (UUID mobId : tracked) {
                MobRecord record = mobs.get(mobId);
                if (record != null) {
                    record.owners.remove(dummyId);
                    if (record.owners.isEmpty()) {
                        if (record.changedByAntiLogout && !record.mob.isRemoved()) {
                            boolean previous = record.mob.isPersistenceRequired();
                            ((TemporaryMobPersistence) record.mob).antilogout_setPersistenceRequired(record.originallyPersistent);
                            restored++;
                            if (AntiLogout.config.general.debug) {
                                AntiLogout.LOGGER.info("[MOBS] Restored mob {} persistence={} (was {}) reason={}", mobId, record.mob.isPersistenceRequired(), previous, reason);
                            }
                        } else if (record.mob.isRemoved()) {
                            removed++;
                        }
                        mobs.remove(mobId);
                    }
                }
            }
            if (AntiLogout.config.general.debug) {
                AntiLogout.LOGGER.info("[MOBS] Dummy {} cleanup ({}): tracked={} restored={} removed={}",
                        dummyId, reason, tracked.size(), restored, removed);
            }
    }

    private static final class MobRecord {
        private final Mob mob;
        private final boolean originallyPersistent;
        private final Set<UUID> owners;
        private boolean changedByAntiLogout;

        private MobRecord(Mob mob, boolean originallyPersistent, Set<UUID> owners) {
            this.mob = mob;
            this.originallyPersistent = originallyPersistent;
            this.owners = owners;
        }
    }
}
