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

package com.lishid.openinv.commands;

import com.lishid.openinv.OpenInv;
import com.lishid.openinv.search.ChunkBucket;
import com.lishid.openinv.search.ItemMatcher;
import com.lishid.openinv.search.PlayerBucket;
import com.lishid.openinv.search.Search;
import com.lishid.openinv.search.SearchBucket;
import com.lishid.openinv.util.Permissions;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SearchCommand implements TabExecutor {

    private final OpenInv plugin;

    public SearchCommand(OpenInv plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args) {

        if (args.length < 2) {
            return false;
        }

        Collection<SearchBucket> searchBuckets = getInventories(sender, args[0]);

        if (searchBuckets.isEmpty()) {
            plugin.sendMessage(sender, "messages.error.search.invalidInventory");
            return true;
        }

        String[] matcherArgs = new String[args.length - 1];
        System.arraycopy(args, 1, matcherArgs, 0, matcherArgs.length);
        // TODO verify that matcher is set up with at least 1 valid matching function
        ItemMatcher matcher = getMatcher(matcherArgs);

        new Search(matcher, searchBuckets).schedule(sender, plugin);
        return true;
    }

    private ItemMatcher getMatcher(String[] matcherArgs) {
        // TODO
        return new ItemMatcher(Collections.emptyList(), Collections.emptyList());
    }

    private Collection<SearchBucket> getInventories(CommandSender sender, String data) {
        // TODO split up more, make method less gross
        data = data.toLowerCase(Locale.ROOT);
        String[] bucketArray = data.split(",");

        boolean player = false;
        boolean playerOffline = false;
        World chunkWorld = null;
        Integer chunkX = null;
        Integer chunkZ = null;
        int chunkRadius = 5;
        boolean chunkLoad = false;

        for (String bucketData : bucketArray) {
            if (bucketData.startsWith("player") && Permissions.SEARCH_PLAYERS_ONLINE.hasPermission(sender)) {
                player = true;
                if (!playerOffline && bucketData.contains("offline")) {
                    playerOffline = Permissions.SEARCH_PLAYERS_OFFLINE.hasPermission(sender);
                }
                continue;
            }

            if (!bucketData.startsWith("chunk") || !Permissions.SEARCH_CHUNKS_LOADED.hasPermission(sender)) {
                continue;
            }

            String[] chunkData = bucketData.split(";");

            if (!(sender instanceof Player)) {
                if (chunkData.length < 4) {
                    // Console must specify world, x center, z center.
                    return Collections.emptyList();
                }
            } else {
                Player senderPlayer = (Player) sender;
                chunkWorld = senderPlayer.getWorld();
                Chunk senderChunk = senderPlayer.getLocation().getChunk();
                chunkX = senderChunk.getX();
                chunkZ = senderChunk.getZ();
            }

            for (String chunkDatum : chunkData) {
                String[] datum = chunkDatum.split(":");
                if (datum.length < 2) {
                    continue;
                }
                String key = datum[0];
                String value = datum[1];

                if (key.startsWith("w")) {
                    chunkWorld = Bukkit.getWorld(value);
                } else if (key.equals("x")) {
                    try {
                        chunkX = Integer.parseInt(value);
                    } catch (NumberFormatException e) {
                        chunkX = null;
                    }
                } else if (key.equals("z")) {
                    try {
                        chunkZ = Integer.parseInt(value);
                    } catch (NumberFormatException e) {
                        chunkZ = null;
                    }
                } else if (key.equals("r")) {
                    try {
                        chunkRadius = Integer.parseInt(value);
                    } catch (NumberFormatException e) {
                        // Fall through to default radius.
                    }
                } else if (key.equals("load")) {
                    // TODO: migrate a StringUtil#isTruthy to PlanarWrappers? Either way, "yes" "on" etc. should be valid
                    chunkLoad = Permissions.SEARCH_CHUNKS_UNLOADED.hasPermission(sender) && Boolean.parseBoolean(value);
                }
            }

            if (chunkWorld == null || chunkX == null || chunkZ == null) {
                // Invalid data provided.
                return Collections.emptyList();
            }

        }

        List<SearchBucket> buckets = new ArrayList<>();

        if (player) {
            buckets.add(new PlayerBucket(plugin, !playerOffline));
        }

        if (chunkWorld != null) {
            buckets.add(new ChunkBucket(plugin, chunkWorld, chunkX, chunkZ, chunkRadius, chunkLoad));
        }

        return buckets;
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args) {
        // TODO
        return null;
    }

}
