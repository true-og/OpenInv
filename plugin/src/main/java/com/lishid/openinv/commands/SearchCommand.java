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
import com.lishid.openinv.commands.search.ChunkSearchOption;
import com.lishid.openinv.commands.search.CompletableOption;
import com.lishid.openinv.commands.search.ItemAmountOption;
import com.lishid.openinv.commands.search.ItemEnchantOption;
import com.lishid.openinv.commands.search.ItemTypeOption;
import com.lishid.openinv.commands.search.PlayerSearchOption;
import com.lishid.openinv.commands.search.PseudoOption;
import com.lishid.openinv.search.Search;
import com.lishid.openinv.search.bucket.SearchBucket;
import com.lishid.openinv.search.match.ItemMatcher;
import com.lishid.openinv.search.match.MatchableItem;
import com.lishid.openinv.search.match.MatchableMeta;
import com.lishid.openinv.util.PseudoJson;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SearchCommand implements TabExecutor {

    private final OpenInv plugin;
    private final Collection<String> activeSearches = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private final Collection<CompletableOption<SearchBucket>> completableSearchBuckets = new HashSet<>();
    private final Collection<CompletableOption<MatchableItem>> completableMatchOptions = new HashSet<>();
    private final Collection<CompletableOption<MatchableMeta>> completableMatchMetaOptions = new HashSet<>();

    public SearchCommand(OpenInv plugin) {
        this.plugin = plugin;

        this.completableSearchBuckets.add(new ChunkSearchOption());
        this.completableSearchBuckets.add(new PlayerSearchOption(plugin));
        this.completableMatchOptions.add(new ItemAmountOption());
        this.completableMatchOptions.add(new ItemTypeOption());
        this.completableMatchMetaOptions.add(new ItemEnchantOption());
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
            arg = arg.toLowerCase(Locale.ROOT);
            try {
                arguments.add(PseudoJson.fromString(arg));
            } catch (IllegalArgumentException e) {
                plugin.sendMessage(sender, "messages.error.search.invalidSchema", "%value%", arg);
                return true;
            }
        }

        Collection<SearchBucket> searchBuckets = getSearchBuckets(sender, arguments);

        if (searchBuckets.isEmpty()) {
            plugin.sendMessage(sender, "messages.error.search.invalidInventory");
            return true;
        }

        ItemMatcher matcher = getMatcher(sender, arguments);

        if (matcher == null) {
            plugin.sendMessage(sender, "messages.error.search.invalidMatcher");
            return true;
        }

        activeSearches.add(id);

        new Search(matcher, searchBuckets, () -> activeSearches.remove(id)).schedule(sender, plugin);

        return true;
    }

    private @Nullable ItemMatcher getMatcher(@NotNull CommandSender sender, @NotNull Collection<PseudoJson> args) {
        List<PseudoOption<MatchableItem>> matchItemOptions = new ArrayList<>();
        List<PseudoOption<MatchableMeta>> matchMetaOptions = new ArrayList<>();


        for (PseudoJson arg : args) {
            addMatchOptions(completableMatchOptions, matchItemOptions, sender, arg);
            addMatchOptions(completableMatchMetaOptions, matchMetaOptions, sender, arg);
        }

        // Must have a match option. Null entries indicate a parsing error.
        if (matchItemOptions.isEmpty()
                || matchItemOptions.stream().anyMatch(Objects::isNull)
                || matchMetaOptions.stream().anyMatch(Objects::isNull)) {
            return null;
        }

        return new ItemMatcher(
                matchItemOptions.stream().map(PseudoOption::option).toList(),
                matchMetaOptions.stream().map(PseudoOption::option).toList());
    }

    private <T> void addMatchOptions(
            @NotNull Collection<CompletableOption<T>> completableOptions,
            @NotNull Collection<PseudoOption<T>> options,
            @NotNull CommandSender sender,
            @NotNull PseudoJson arg) {
        for (CompletableOption<T> option : completableOptions) {
            if (option.matches(sender, arg)) {
                PseudoOption<T> parsed = option.parse(sender, arg);
                if (parsed == null) {
                    options.add(null);
                    continue;
                }

                if (option.isUnique()) {
                    String uniqueId = option.getName();

                    // Match existing options.
                    List<PseudoOption<T>> matches = options.stream()
                            .filter(Objects::nonNull)
                            .filter(bucket -> bucket.pseudoJson().getIdentifier().equals(uniqueId))
                            .collect(Collectors.toList());

                    // Remove all existing options.
                    options.removeAll(matches);

                    // Merge with existing options.
                    for (PseudoOption<T> match : matches) {
                        PseudoOption<T> newParsed = option.merge(sender, match, parsed);
                        if (newParsed != null) {
                            parsed = newParsed;
                        }
                    }
                }
                options.add(parsed);
            }
        }
    }

    private Collection<SearchBucket> getSearchBuckets(
            @NotNull CommandSender sender,
            @NotNull Collection<PseudoJson> args) {
        List<PseudoOption<SearchBucket>> buckets = new ArrayList<>();

        for (PseudoJson arg : args) {
            addMatchOptions(completableSearchBuckets, buckets, sender, arg);
        }

        return buckets.stream().map(PseudoOption::option).collect(Collectors.toList());
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args) {

        List<PseudoJson> arguments = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i].toLowerCase(Locale.ROOT);
            try {
                arguments.add(PseudoJson.fromString(arg));
            } catch (IllegalArgumentException e) {
                if (i == args.length - 1) {
                    // If last argument cannot be parsed, do not complete.
                    return List.of();
                }
                // Otherwise, ignore formatting issues during tab completion.
            }

        }

        // Ensure an argument exists.
        if (arguments.isEmpty()) {
            arguments.add(new PseudoJson(""));
        }

        // Remove last indexed argument, it is being completed.
        PseudoJson last = arguments.remove(arguments.size() - 1);

        // Due to generics, can't just use generic list of options to start with.
        Collection<PseudoOption<SearchBucket>> buckets = new HashSet<>();
        Collection<PseudoOption<MatchableItem>> items = new HashSet<>();
        Collection<PseudoOption<MatchableMeta>> metas = new HashSet<>();
        for (PseudoJson arg : arguments) {
            addMatchOptions(completableSearchBuckets, buckets, sender, arg);
            addMatchOptions(completableMatchOptions, items, sender, arg);
            addMatchOptions(completableMatchMetaOptions, metas, sender, arg);
        }
        Collection<PseudoOption<?>> options = new HashSet<>();
        options.addAll(buckets);
        options.addAll(items);
        options.addAll(metas);

        // Suggest all completable options' completions.
        return Stream.concat(completableSearchBuckets.stream(),
                Stream.concat(completableMatchOptions.stream(),
                        completableMatchMetaOptions.stream()))
                // Don't suggest unique options that are already present.
                .filter(option ->
                        option.isUnique() && options.stream().anyMatch(pseudoOption ->
                                pseudoOption.pseudoJson().getIdentifier().equals(option.getName())))
                .flatMap(option -> option.suggestOptions(sender, last).stream())
                .distinct()
                .toList();
    }

}
