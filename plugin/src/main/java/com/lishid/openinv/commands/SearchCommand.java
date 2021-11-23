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
import com.lishid.openinv.commands.search.ChunkSearchOption;
import com.lishid.openinv.commands.search.CompletableOption;
import com.lishid.openinv.commands.search.PlayerSearchOption;
import com.lishid.openinv.commands.search.PseudoOption;
import com.lishid.openinv.search.ItemMatcher;
import com.lishid.openinv.search.MatchMetaOption;
import com.lishid.openinv.search.MatchOption;
import com.lishid.openinv.search.MatchOptions;
import com.lishid.openinv.search.Search;
import com.lishid.openinv.search.SearchBucket;
import com.lishid.openinv.util.PseudoJson;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SearchCommand implements TabExecutor {

    private final OpenInv plugin;
    private final Collection<String> activeSearches = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private final Collection<CompletableOption<? extends SearchBucket>> completableSearchBuckets = new HashSet<>();
    private final Collection<CompletableOption<MatchOption>> completableMatchOptions = new HashSet<>();
    private final Collection<CompletableOption<MatchMetaOption>> completableMatchMetaOptions = new HashSet<>();

    public SearchCommand(OpenInv plugin) {
        this.plugin = plugin;

        this.completableSearchBuckets.add(new ChunkSearchOption());
        this.completableSearchBuckets.add(new PlayerSearchOption(plugin));
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

        String id = sender instanceof Player ? plugin.getPlayerID((Player) sender) : "CONSOLE";

        if (activeSearches.contains(id)) {
            plugin.sendMessage(sender, "messages.error.search.activeSearch");
            return true;
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

        activeSearches.add(id);

        new Search(matcher, searchBuckets, () -> activeSearches.remove(id)).schedule(sender, plugin);

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
        List<PseudoOption<? extends SearchBucket>> buckets = new ArrayList<>();

        for (PseudoJson arg : args) {
            for (CompletableOption<? extends SearchBucket> bucketOption : completableSearchBuckets) {
                if (bucketOption.matches(sender, arg)) {
                    PseudoOption<? extends SearchBucket> parsed = bucketOption.parse(sender, arg);
                    if (parsed == null) {
                        return Collections.emptyList();
                    }

                    if (bucketOption.isUnique()) {
                        Class<?> uniqueClazz = parsed.getClass();

                        // Match existing buckets.
                        List<PseudoOption<? extends SearchBucket>> matches = buckets.stream()
                                .filter(bucket -> bucket.getClass().equals(uniqueClazz))
                                .collect(Collectors.toList());

                        // Remove all existing buckets.
                        buckets.removeAll(matches);

                        // Merge with existing buckets.
                        for (PseudoOption<? extends SearchBucket> match : matches) {
                            PseudoOption<? extends SearchBucket> newParsed = bucketOption.merge(sender, match, parsed);
                            if (newParsed != null) {
                                parsed = newParsed;
                            }
                        }
                    }
                    buckets.add(parsed);
                }
            }
        }

        return buckets.stream().map(PseudoOption::getOption).collect(Collectors.toList());
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
