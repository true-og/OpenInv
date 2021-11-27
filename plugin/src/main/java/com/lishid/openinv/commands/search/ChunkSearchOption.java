/*
 * Copyright (C) 2011-2021 lishid. All rights reserved.
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

package com.lishid.openinv.commands.search;

import com.lishid.openinv.search.bucket.ChunkBucket;
import com.lishid.openinv.search.bucket.SearchBucket;
import com.lishid.openinv.util.Permissions;
import com.lishid.openinv.util.PseudoJson;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ChunkSearchOption implements CompletableOption<SearchBucket> {

    @Override
    public @NotNull String getName() {
        return "chunk";
    }

    @Override
    public boolean matches(@NotNull CommandSender sender, @NotNull PseudoJson pseudoJson) {
        return CompletableOption.super.matches(sender, pseudoJson)
                && Permissions.SEARCH_CHUNKS_LOADED.hasPermission(sender);
    }

    @Override
    public @Nullable PseudoOption<SearchBucket> parse(@NotNull CommandSender sender, @NotNull PseudoJson pseudoJson) {
        World world = null;
        Integer chunkX = null;
        Integer chunkZ = null;
        int chunkRadius = 5;
        boolean chunkLoad = false;
        Map<String, String> mappings = pseudoJson.getMappings();

        if (!(sender instanceof Player senderPlayer)) {
            if (mappings.size() < 3) {
                // Console must specify world, x center, z center.
                return null;
            }
        } else {
            world = senderPlayer.getWorld();
            Chunk senderChunk = senderPlayer.getLocation().getChunk();
            chunkX = senderChunk.getX();
            chunkZ = senderChunk.getZ();
        }

        for (Map.Entry<String, String> entry : mappings.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            if (key.indexOf('w') >= 0) {
                world = null;
                // Name should always be lower case by this point. Ignore case when selecting world.
                for (World loaded : Bukkit.getWorlds()) {
                    if (loaded.getName().equalsIgnoreCase(value)) {
                        world = loaded;
                        break;
                    }
                }
            } else if (key.indexOf('x') >= 0) {
                try {
                    chunkX = Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    chunkX = null;
                }
            } else if (key.indexOf('z') >= 0) {
                try {
                    chunkZ = Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    chunkZ = null;
                }
            } else if (key.indexOf('r') >= 0) {
                // R is for radius. Needs to be checked later so that "world" doesn't result in a false positive.
                try {
                    chunkRadius = Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    // Fall through to default radius.
                }
            } else if (key.indexOf('l') >= 0) {
                // L is for loaded. Needs to be checked later so that "world" doesn't result in a false positive.
                chunkLoad = Permissions.SEARCH_CHUNKS_UNLOADED.hasPermission(sender) && Boolean.parseBoolean(value);
            }
        }

        if (world == null || chunkX == null || chunkZ == null || chunkRadius < 0) {
            // Invalid data provided.
            return null;
        }

        return new PseudoOption<>(pseudoJson, new ChunkBucket(world, chunkX, chunkZ, chunkRadius, chunkLoad));
    }

    @Override
    public @NotNull Collection<String> suggestOptions(@NotNull CommandSender sender, @NotNull PseudoJson pseudoJson) {
        // TODO
        return Set.of();
    }

    @Override
    public boolean isUnique() {
        return false;
    }

}
