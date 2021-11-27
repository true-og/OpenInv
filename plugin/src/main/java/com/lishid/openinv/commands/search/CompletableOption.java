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

import com.lishid.openinv.util.PseudoJson;
import java.util.Collection;
import java.util.Locale;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Interface defining behavior for an option.
 *
 * @param <T> the type of option represented
 */
public interface CompletableOption<T> {

    /**
     * Get unique identifier representing the option.
     *
     * @return the unique identifier
     */
    @NotNull String getName();

    /**
     * Check if the <code>CompletableOption</code> matches the given <code>PseudoJson</code>.
     *
     * @param sender the individual using the option
     * @param pseudoJson the pseudo-JSON element
     * @return true if the CompletableOption matches
     */
    default boolean matches(@NotNull CommandSender sender, @NotNull PseudoJson pseudoJson) {
        return pseudoJson.getIdentifier().toLowerCase(Locale.ROOT).startsWith(getName());
    }

    /**
     * Parse the option represented from the given <code>PseudoJson</code>.
     *
     * @param sender the individual using the option
     * @param pseudoJson the pseudo-JSON element
     * @return the parsed options
     */
    @Nullable PseudoOption<T> parse(@NotNull CommandSender sender, @NotNull PseudoJson pseudoJson);

    /**
     * Form a collection of viable permutations to suggest for the given <code>PseudoJson</code>.
     *
     * @param sender the individual to suggest options for
     * @param pseudoJson the parsed pseudo-JSON
     * @return the suggested permutations
     */
    @NotNull Collection<String> suggestOptions(
            @NotNull CommandSender sender,
            @NotNull PseudoJson pseudoJson);

    /**
     * Get whether or not multiple different copies of this <code>CompletableOption</code> are allowed.
     *
     * @return true if only a single instance is allowed
     */
    boolean isUnique();

    /**
     * Merge similar elements. Used to enforce {@link #isUnique()} despite multiples being provided.
     *
     * @param sender the individual using the options
     * @param first the first parsed option
     * @param second the second parsed option
     * @return a single element containing a best-effort merge of both
     */
    default @Nullable PseudoOption<T> merge(
            @NotNull CommandSender sender,
            @NotNull PseudoOption<?> first,
            @NotNull PseudoOption<?> second) {
        PseudoJson merge = new PseudoJson(first.pseudoJson().getIdentifier());

        // Use mappings of first for our base.
        first.pseudoJson().getMappings().forEach(merge::put);
        // Overwrite with second.
        second.pseudoJson().getMappings().forEach(merge::put);

        // Parse merged options.
        PseudoOption<T> parsed = parse(sender, merge);

        if (parsed != null) {
            return parsed;
        }

        // On parsing failure, try second instead.
        merge = new PseudoJson(second.pseudoJson().getIdentifier(), merge.getMappings());

        return parse(sender, merge);
    }

}
