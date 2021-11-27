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

package com.lishid.openinv.search;

import com.lishid.openinv.OpenInv;
import com.lishid.openinv.search.bucket.SearchBucket;
import com.lishid.openinv.search.match.ItemMatcher;
import com.lishid.openinv.search.match.MatchResult;
import com.lishid.openinv.search.match.Matchable;
import com.lishid.openinv.util.MessagePart;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A search function.
 */
public class Search {

    public static final BaseComponent SEPARATOR = new TextComponent(", ");

    private final Set<MessagePart> matches = new HashSet<>();
    private final ItemMatcher matcher;
    private final double totalMatchables;
    private final Iterator<SearchBucket> bucketIterator;
    private final Runnable callback;
    private SearchBucket current = null;
    private int polls = 0;

    public Search(
            @NotNull ItemMatcher matcher,
            @NotNull Collection<@NotNull SearchBucket> buckets,
            @Nullable Runnable callback) {
        // Just in case, copy contents to new set.
        Set<SearchBucket> bucketSet = new HashSet<>(buckets);
        this.matcher = matcher;
        this.totalMatchables = bucketSet.stream().mapToInt(SearchBucket::size).sum();
        this.bucketIterator = bucketSet.iterator();
        this.callback = callback;
    }

    private boolean isComplete(OpenInv plugin) {
        if (matches.size() >= plugin.getSearchResultsMax()) {
            return true;
        }
        if (current != null && current.hasNext()) {
            return false;
        }
        while (bucketIterator.hasNext()) {
            current = bucketIterator.next();
            if (current.hasNext()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Search the configured amount of Matchables.
     *
     * <p>Matchables are assembled asynchronously, then
     *
     * @param plugin the OpenInv instance
     */
    private void poll(OpenInv plugin) {
        Collection<Matchable> syncMatchables = new ArrayList<>();
        // Assemble matchables async, then search synchronously.
        for (int i = 0; !isComplete(plugin) && i < plugin.getSearchPollsPerTick(); ++i) {
            ++polls;
            // Calling #isComplete will advance to next InventoryProvider as necessary.
            // Bucket matchables for searching synchronously.
            syncMatchables.add(current.next());
        }

        Future<Boolean> future = plugin.getServer().getScheduler().callSyncMethod(plugin, () -> {
            for (Matchable matchable : syncMatchables) {
                MatchResult match = matchable.match(matcher);
                if (match.isMatch()) {
                    matches.addAll(Arrays.asList(match.getMatch()));
                }
            }
            return true;
        });

        try {
            future.get();
        } catch (InterruptedException | ExecutionException e) {
            // Eat exception to prevent spam.
        }
    }

    private void sendProgress(CommandSender sender, OpenInv plugin) {
        if (!(sender instanceof Player) || polls % (10 * plugin.getSearchPollsPerTick()) != 1) {
            return;
        }
        double progress = 100 * Math.min(polls, totalMatchables) / totalMatchables;
        DecimalFormat percent = new DecimalFormat("00.0");
        plugin.sendSystemMessage((Player) sender, "messages.info.search.progress",
                "%progress%", percent.format(progress),
                "%matches%", String.valueOf(matches.size()));
    }

    private void sendResults(CommandSender sender, OpenInv plugin) {
        if (matches.isEmpty()) {
            String localizedMessage = plugin.getLocalizedMessage(sender, "messages.info.search.noMatches");
            if (localizedMessage == null) {
                localizedMessage = "No items found with search parameters.";
            }
            sender.sendMessage(localizedMessage);
            return;
        }

        String prefix = plugin.getLocalizedMessage(sender, "messages.info.search.matches");
        if (prefix == null) {
            prefix = "Locations of items matching parameters:";
        }

        if (sender instanceof Player) {
            TextComponent textComponent = new TextComponent(prefix);
            Iterator<MessagePart> iterator = matches.iterator();

            int total = 0;
            int searchResultsMax = plugin.getSearchResultsMax();
            while (iterator.hasNext()) {
                textComponent.addExtra(iterator.next().forPlayer());

                ++total;
                if (total >= searchResultsMax) {
                    break;
                }

                 if (iterator.hasNext()) {
                     textComponent.addExtra(SEPARATOR);
                 }
            }

            // Paper deprecated Spigot's message API.
            // This is a risky deprecation ignore - Spigot may eventually do the sane thing
            // and just add a method accepting components to the player directly.
            //noinspection deprecation
            sender.spigot().sendMessage(textComponent);
            return;
        }

        String message = matches.stream()
                .limit(plugin.getSearchResultsMax())
                .map(MessagePart::forConsole)
                .collect(Collectors.joining(", ", prefix, ""));
        sender.sendMessage(message);
    }

    public void schedule(CommandSender sender, OpenInv plugin) {
        new BukkitRunnable() {
            @Override
            public void run() {
                // Scheduler struggles to cancel async tasks with high frequency.
                if (isCancelled()) {
                    return;
                }

                poll(plugin);

                if (isComplete(plugin)) {
                    if (isCancelled()) {
                        return;
                    }

                    sendResults(sender, plugin);
                    cancel();

                    if (callback != null) {
                        callback.run();
                    }
                    return;
                }

                sendProgress(sender, plugin);
            }
        }.runTaskTimerAsynchronously(plugin, 0L, 1L);
    }

}
