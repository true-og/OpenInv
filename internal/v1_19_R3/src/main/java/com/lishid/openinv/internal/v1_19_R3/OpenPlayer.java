/*
 * Copyright (C) 2011-2023 lishid. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.lishid.openinv.internal.v1_19_R3;

import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.PlayerDataStorage;
import org.apache.logging.log4j.LogManager;
import org.bukkit.craftbukkit.v1_19_R3.CraftServer;
import org.bukkit.craftbukkit.v1_19_R3.entity.CraftPlayer;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;

public class OpenPlayer extends CraftPlayer {

    private static final Set<String> RESET_TAGS = Set.of(
        // net.minecraft.world.Entity#saveWithoutId(CompoundTag)
        "CustomName",
        "CustomNameVisible",
        "Silent",
        "NoGravity",
        "Glowing",
        "TicksFrozen",
        "HasVisualFire",
        "Tags",
        "Passengers",
        // net.minecraft.server.level.ServerPlayer#addAdditionalSaveData(CompoundTag)
        // Intentional omissions to prevent mount loss: Attach, Entity, and RootVehicle
        "warden_spawn_tracker",
        "enteredNetherPosition",
        "SpawnX",
        "SpawnY",
        "SpawnZ",
        "SpawnForced",
        "SpawnAngle",
        "SpawnDimension",
        // net.minecraft.world.entity.player.Player#addAdditionalSaveData(CompoundTag)
        "ShoulderEntityLeft",
        "ShoulderEntityRight",
        "LastDeathLocation",
        // net.minecraft.world.entity.LivingEntity#addAdditionalSaveData(CompoundTag)
        "ActiveEffects",
        "SleepingX",
        "SleepingY",
        "SleepingZ",
        "Brain"
    );

    public OpenPlayer(CraftServer server, ServerPlayer entity) {
        super(server, entity);
    }

    @Override
    public void loadData() {
        // See CraftPlayer#loadData
        ServerPlayer serverPlayer = getHandle();
        // The server's own storage, never the hookable PlayerList.playerIo, so an offline edit always targets the main profile
        CompoundTag loaded = this.server.getServer().playerDataStorage.load(serverPlayer);
        if (loaded != null) {
            serverPlayer.readAdditionalSaveData(loaded);
            serverPlayer.loadGameTypes(loaded);
        }
    }

    @Override
    public void saveData() {
        ServerPlayer player = this.getHandle();
        // An offline save is a read-modify-write of the stored profile: hold the Utilities-OG player lock that MyWorlds and
        // DiamondBank hold for theirs, so the two never interleave (no lock without that plugin, nothing else writes then)
        boolean offline = !isOnline();
        if (offline) {
            UtilitiesLock.lock(player.getUUID());
        }
        // See net.minecraft.world.level.storage.PlayerDataStorage#save(EntityHuman)
        try {
            PlayerDataStorage worldNBTStorage = player.server.playerDataStorage;

            // Read without applying: load(Player) would push the stored inventory back over the edit being saved
            CompoundTag oldData = offline ? readStoredTag(worldNBTStorage, player) : null;
            CompoundTag playerData = getWritableTag(oldData);
            playerData = player.saveWithoutId(playerData);
            setExtraData(playerData);

            if (oldData != null) {
                // Revert certain special data values when offline.
                revertSpecialValues(playerData, oldData);
            }

            if (saveThroughStorageApi(worldNBTStorage, player.getUUID(), playerData)) {
                return;
            }
            File file = File.createTempFile(player.getStringUUID() + "-", ".dat", worldNBTStorage.getPlayerDir());
            NbtIo.writeCompressed(playerData, file);
            File file1 = new File(worldNBTStorage.getPlayerDir(), player.getStringUUID() + ".dat");
            File file2 = new File(worldNBTStorage.getPlayerDir(), player.getStringUUID() + ".dat_old");
            Util.safeReplaceFile(file1, file, file2);
        } catch (Exception e) {
            LogManager.getLogger().warn("Failed to save player data for {}: {}", player.getScoreboardName(), e);
        } finally {
            if (offline) {
                UtilitiesLock.unlock(player.getUUID());
            }
        }
    }

    // Utilities-OG's per player lock, reached by reflection so OpenInv needs no compile dependency on it
    private static final class UtilitiesLock {
        private static final Method LOCK;
        private static final Method UNLOCK;

        static {
            Method lock = null;
            Method unlock = null;
            try {
                Class<?> utilities = Class.forName("net.trueog.utilitiesog.UtilitiesOG", true, OpenPlayer.class.getClassLoader());
                lock = utilities.getMethod("lockPlayerData", UUID.class);
                unlock = utilities.getMethod("unlockPlayerData", UUID.class);
            } catch (ReflectiveOperationException e) {
                // Utilities-OG is not installed, offline edits are unsynchronized like on vanilla
            }
            LOCK = lock;
            UNLOCK = unlock;
        }

        static void lock(UUID uuid) {
            invoke(LOCK, uuid);
        }

        static void unlock(UUID uuid) {
            invoke(UNLOCK, uuid);
        }

        private static void invoke(@Nullable Method method, UUID uuid) {
            if (method == null) {
                return;
            }
            try {
                method.invoke(null, uuid);
            } catch (ReflectiveOperationException e) {
                LogManager.getLogger().warn("Utilities-OG player data lock failed for {}", uuid, e);
            }
        }
    }

    // A storage with saveCompoundTag(UUID, CompoundTag) keeps profiles in a database (TrueOG Purpur); its getPlayerData(UUID) never touches the entity
    private static @Nullable Method storageSave(@NotNull PlayerDataStorage storage) {
        try {
            return storage.getClass().getMethod("saveCompoundTag", UUID.class, CompoundTag.class);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static @Nullable CompoundTag readStoredTag(@NotNull PlayerDataStorage storage, @NotNull ServerPlayer player) throws ReflectiveOperationException {
        if (storageSave(storage) != null) {
            Method get = storage.getClass().getMethod("getPlayerData", UUID.class);
            return (CompoundTag) get.invoke(storage, player.getUUID());
        }
        // CraftBukkit's file read, which does not apply the data to the entity
        Method getPlayerData = storage.getClass().getMethod("getPlayerData", String.class);
        return (CompoundTag) getPlayerData.invoke(storage, player.getStringUUID());
    }

    // True when the storage took the profile itself (it logs a failed write); false means the caller keeps the file layout
    private static boolean saveThroughStorageApi(@NotNull PlayerDataStorage storage, @NotNull UUID uuid, @NotNull CompoundTag playerData) throws ReflectiveOperationException {
        Method save = storageSave(storage);
        if (save == null) {
            return false;
        }
        save.invoke(storage, uuid, playerData);
        return true;
    }

    @Contract("null -> new")
    private @NotNull CompoundTag getWritableTag(@Nullable CompoundTag oldData) {
        if (oldData == null) {
            return new CompoundTag();
        }

        // Copy old data. This is a deep clone, so operating on it should be safe.
        oldData = oldData.copy();

        // Remove vanilla/server data that is not written every time.
        oldData.getAllKeys().removeIf(key -> RESET_TAGS.contains(key) || key.startsWith("Bukkit"));

        return oldData;
    }

    private void revertSpecialValues(@NotNull CompoundTag newData, @NotNull CompoundTag oldData) {
        // Revert automatic updates to play timestamps.
        copyValue(oldData, newData, "bukkit", "lastPlayed", NumericTag.class);
        copyValue(oldData, newData, "Paper", "LastSeen", NumericTag.class);
        copyValue(oldData, newData, "Paper", "LastLogin", NumericTag.class);
        // The persistent data container was captured when the inventory was opened; other plugins (MyWorlds nests its
        // per-world profiles there) may have changed the stored one since, and this edit never touches it
        Tag storedValues = oldData.get("BukkitValues");
        if (storedValues != null) {
            newData.put("BukkitValues", storedValues.copy());
        } else {
            newData.remove("BukkitValues");
        }
    }

    private <T extends Tag> void copyValue(
        @NotNull CompoundTag source,
        @NotNull CompoundTag target,
        @NotNull String container,
        @NotNull String key,
        @NotNull Class<T> tagType) {
        CompoundTag oldContainer = getTag(source, container, CompoundTag.class);
        CompoundTag newContainer = getTag(target, container, CompoundTag.class);

        // New container being null means the server implementation doesn't store this data.
        if (newContainer == null) {
            return;
        }

        // If old tag exists, copy it to new location, removing otherwise.
        setTag(newContainer, key, getTag(oldContainer, key, tagType));
    }

    private <T extends Tag> @Nullable T getTag(
        @Nullable CompoundTag container,
        @NotNull String key,
        @NotNull Class<T> dataType) {
        if (container == null) {
            return null;
        }
        Tag value = container.get(key);
        if (value == null || !dataType.isAssignableFrom(value.getClass())) {
            return null;
        }
        return dataType.cast(value);
    }

    private <T extends Tag> void setTag(
        @NotNull CompoundTag container,
        @NotNull String key,
        @Nullable T data) {
        if (data == null) {
            container.remove(key);
        } else {
            container.put(key, data);
        }
    }

}
