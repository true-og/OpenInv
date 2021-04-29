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

import com.github.jikoo.planarwrappers.util.StringConverters;
import com.lishid.openinv.OpenInv;
import com.lishid.openinv.search.ChunkBucket;
import com.lishid.openinv.search.ItemMatcher;
import com.lishid.openinv.search.MatchMetaOption;
import com.lishid.openinv.search.MatchOption;
import com.lishid.openinv.search.MatchOptions;
import com.lishid.openinv.search.PlayerBucket;
import com.lishid.openinv.search.Search;
import com.lishid.openinv.search.SearchBucket;
import com.lishid.openinv.util.Permissions;
import com.lishid.openinv.util.PseudoJson;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.enchantments.Enchantment;
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
        // TODO
        //  - 1 search active per sender
        //  - less gross way to deal with parsing options

        if (args.length < 2) {
            return false;
        }

        Collection<PseudoJson> arguments = new HashSet<>();
        for (String arg : args) {
            arguments.add(PseudoJson.fromString(arg));
        }

        Collection<SearchBucket> searchBuckets = getSearchBuckets(sender, arguments);

        if (searchBuckets.isEmpty()) {
            plugin.sendMessage(sender, "messages.error.search.invalidInventory");
            return true;
        }

        ItemMatcher matcher = getMatcher(arguments);

        if (matcher == null) {
            plugin.sendMessage(sender, "messages.error.search.invalidMatcher");
            return true;
        }

        new Search(matcher, searchBuckets).schedule(sender, plugin);
        return true;
    }

    private @Nullable ItemMatcher getMatcher(Collection<PseudoJson> args) {
        List<MatchMetaOption> matchMetaOptions = new ArrayList<>();
        Material type = null;
        Integer minAmount = null;

        for (PseudoJson arg : args) {
            if (arg.getIdentifier().startsWith("type")) {
                String typeName = arg.getMappings().entrySet().stream().findFirst().map(Map.Entry::getValue).orElse(null);
                Material material = StringConverters.toMaterial(typeName);
                if (material == null) {
                    // Invalid material, warn about matcher spec.
                    return null;
                }
                type = material;
                continue;
            }

            if (arg.getIdentifier().startsWith("amount")) {
                String minAmountString = arg.getMappings().entrySet().stream().findFirst().map(Map.Entry::getValue).orElse(null);
                if (minAmountString == null) {
                    // Amount not correctly specified. Warn about spec.
                    return null;
                }
                try {
                    minAmount = Integer.parseInt(minAmountString);
                } catch (NumberFormatException e) {
                    // Amount can't be parsed. Warn about spec.
                    return null;
                }
                continue;
            }

            if (arg.getIdentifier().startsWith("enchant")) {
                Enchantment enchantment = null;
                Integer enchantLevel = null;
                for (Map.Entry<String, String> entry : arg.getMappings().entrySet()) {
                    if (entry.getKey().indexOf('l') >= 0) {
                        try {
                            enchantLevel = Integer.parseInt(entry.getValue());
                        } catch (NumberFormatException e) {
                            // Level can't be parsed. Warn about spec.
                            return null;
                        }
                    }
                    if (entry.getKey().indexOf('t') >= 0) {
                        enchantment = StringConverters.toEnchant(entry.getValue());
                        if (enchantment == null) {
                            // Enchantment is specified but can't be parsed. Warn about spec.
                            return null;
                        }
                    }
                }

                matchMetaOptions.add(MatchOptions.hasEnchant(enchantment, enchantLevel));
            }
        }

        List<MatchOption> matchOptions = new ArrayList<>();

        if (type != null) {
            matchOptions.add(MatchOptions.isType(type));
        }

        if (minAmount != null) {
            matchOptions.add(MatchOptions.hasAmount(minAmount));
        }

        if (matchOptions.isEmpty()) {
            return null;
        }

        return new ItemMatcher(matchOptions, matchMetaOptions);
    }

    private Collection<SearchBucket> getSearchBuckets(CommandSender sender, Collection<PseudoJson> args) {
        List<SearchBucket> buckets = new ArrayList<>();

        // Only one player bucket - offline option will still search online players.
        boolean player = false;
        boolean playerOffline = false;

        for (PseudoJson arg : args) {
            if (arg.getIdentifier().startsWith("player") && Permissions.SEARCH_PLAYERS_ONLINE.hasPermission(sender)) {
                player = true;
                if (playerOffline) {
                    continue;
                }
                Optional<Boolean> optional = arg.getMappings().entrySet().stream()
                        .filter(entry -> entry.getKey().contains("offline"))
                        .findFirst()
                        .map(Map.Entry::getValue)
                        .map(Boolean::parseBoolean);
                if (optional.orElse(false)) {
                    playerOffline = Permissions.SEARCH_PLAYERS_OFFLINE.hasPermission(sender);
                }
                continue;
            }
            if (arg.getIdentifier().startsWith("chunk") && Permissions.SEARCH_CHUNKS_LOADED.hasPermission(sender)) {
                ChunkBucket chunkBucket = getChunkBucket(sender, arg);

                if (chunkBucket == null) {
                    // Invalid chunk bucket parameters, warn about bucket spec.
                    return Collections.emptyList();
                }

                buckets.add(chunkBucket);
            }
        }

        if (player) {
            buckets.add(new PlayerBucket(plugin, !playerOffline));
        }

        return buckets;
    }

    private @Nullable ChunkBucket getChunkBucket(CommandSender sender, PseudoJson bucketData) {
        World world = null;
        Integer chunkX = null;
        Integer chunkZ = null;
        int chunkRadius = 5;
        boolean chunkLoad = false;
        Map<String, String> mappings = bucketData.getMappings();

        if (!(sender instanceof Player)) {
            if (mappings.size() < 3) {
                // Console must specify world, x center, z center.
                return null;
            }
        } else {
            Player senderPlayer = (Player) sender;
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

        return new ChunkBucket(world, chunkX, chunkZ, chunkRadius, chunkLoad);
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
