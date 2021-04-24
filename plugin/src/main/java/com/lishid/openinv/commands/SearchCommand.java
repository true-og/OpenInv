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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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

        Map<String, Map<String, String>> argsToDetails = new HashMap<>();

        for (String arg : args) {
            arg = arg.toLowerCase(Locale.ROOT);
            argsToDetails.put(arg, getData(arg));
        }

        Collection<SearchBucket> searchBuckets = getSearchBuckets(sender, argsToDetails);

        if (searchBuckets.isEmpty()) {
            plugin.sendMessage(sender, "messages.error.search.invalidInventory");
            return true;
        }

        ItemMatcher matcher = getMatcher(argsToDetails);

        if (matcher == null) {
            plugin.sendMessage(sender, "messages.error.search.invalidMatcher");
            return true;
        }

        new Search(matcher, searchBuckets).schedule(sender, plugin);
        return true;
    }

    private Map<String, String> getData(String arg) {
        arg = arg.toLowerCase(Locale.ROOT);
        String[] matchData = getPseudoJson(arg);

        Map<String, String> data = new HashMap<>();
        for (String matchDatum : matchData) {
            String[] datum = matchDatum.split(":");
            if (datum.length >= 2) {
                data.put(datum[0], datum[1]);
            }
        }
        return data;
    }

    private @Nullable ItemMatcher getMatcher(Map<String, Map<String, String>> args) {
        List<MatchMetaOption> matchMetaOptions = new ArrayList<>();
        Material type = null;
        Integer minAmount = null;

        for (Map.Entry<String, Map<String, String>> arg : args.entrySet()) {
            if (arg.getKey().startsWith("type")) {
                String typeName = arg.getValue().entrySet().stream().findFirst().map(Map.Entry::getValue).orElse(null);
                Material material = StringConverters.toMaterial(typeName);
                if (material == null) {
                    // Invalid material, warn about matcher spec.
                    return null;
                }
                type = material;
                continue;
            }

            if (arg.getKey().startsWith("amount")) {
                String minAmountString = arg.getValue().entrySet().stream().findFirst().map(Map.Entry::getValue).orElse(null);
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

            if (arg.getKey().startsWith("enchant")) {
                Enchantment enchantment = null;
                Integer enchantLevel = null;
                for (Map.Entry<String, String> entry : arg.getValue().entrySet()) {
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

    private Collection<SearchBucket> getSearchBuckets(CommandSender sender, Map<String, Map<String, String>> args) {
        List<SearchBucket> buckets = new ArrayList<>();

        // Only one player bucket - offline option will still search online players.
        boolean player = false;
        boolean playerOffline = false;

        for (Map.Entry<String, Map<String, String>> arg : args.entrySet()) {
            if (arg.getKey().startsWith("player") && Permissions.SEARCH_PLAYERS_ONLINE.hasPermission(sender)) {
                player = true;
                if (playerOffline) {
                    continue;
                }
                Optional<Boolean> optional = arg.getValue().entrySet().stream()
                        .filter(entry -> entry.getKey().contains("offline"))
                        .findFirst()
                        .map(Map.Entry::getValue)
                        .map(Boolean::parseBoolean);
                if (optional.orElse(false)) {
                    playerOffline = Permissions.SEARCH_PLAYERS_OFFLINE.hasPermission(sender);
                }
                continue;
            }
            if (arg.getKey().startsWith("chunk") && Permissions.SEARCH_CHUNKS_LOADED.hasPermission(sender)) {
                ChunkBucket chunkBucket = getChunkBucket(sender, arg.getValue());

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

    private @Nullable ChunkBucket getChunkBucket(CommandSender sender, Map<String, String> bucketData) {
        World world = null;
        Integer chunkX = null;
        Integer chunkZ = null;
        int chunkRadius = 5;
        boolean chunkLoad = false;

        if (!(sender instanceof Player)) {
            if (bucketData.size() < 3) {
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

        for (Map.Entry<String, String> entry : bucketData.entrySet()) {
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

    /**
     * Helper method for parsing JSON-like syntax attached to parameters.
     *
     * <p>Ex: A ChunkBucket can be expressed <code>chunks{world:world_name,x:0,z:0,radius:10,load:true}</code>. The
     * starting "chunks" identifies it as a ChunkBucket and the content in braces contains the construction detail.
     * However, a simpler detail, such as a MatchOption, may be specified <code>type:IRON_SWORD</code> where "type"
     * is both the identifier of the MatchOption and the key of the sole value.
     *
     * <p>A best-effort will be made to match other start and finish demarcation techniques.
     *
     * @param data the data string
     * @return the data mappings
     */
    private String[] getPseudoJson(String data) {
        // Try for curly braces first.
        int open = data.indexOf('{');
        int close = data.indexOf('}');

        // If not present, try for brackets.
        if (open < 0) {
            open = data.indexOf('[');
            close = data.indexOf(']');
        }

        // Last try, parentheses.
        if (open < 0) {
            open = data.indexOf('(');
            close = data.indexOf(')');
        }

        // Use only bracketed content
        if (open >= 0 && close > open) {
            data = data.substring(open + 1, close);
        }

        return data.split(",");
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
