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

import com.lishid.openinv.search.match.ItemMatchables;
import com.lishid.openinv.search.match.MatchableItem;
import com.lishid.openinv.util.PseudoJson;
import com.lishid.openinv.util.TabCompleter;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.bukkit.command.CommandSender;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ItemAmountOption implements CompletableOption<MatchableItem> {

    @Override
    public @NotNull String getName() {
        return "amount";
    }

    @Override
    public @Nullable PseudoOption<MatchableItem> parse(@NotNull CommandSender sender, @NotNull PseudoJson pseudoJson) {
        String minAmountString = pseudoJson.getMappings().entrySet().stream().findFirst().map(Map.Entry::getValue).orElse(null);
        if (minAmountString == null) {
            // Amount not correctly specified. Warn about spec.
            return null;
        }
        int minAmount;
        try {
            minAmount = Integer.parseInt(minAmountString);
        } catch (NumberFormatException e) {
            // Amount can't be parsed. Warn about spec.
            return null;
        }

        return new PseudoOption<>(pseudoJson, ItemMatchables.hasAmount(minAmount));
    }

    @Override
    public @NotNull Collection<String> suggestOptions(@NotNull CommandSender sender, @NotNull PseudoJson pseudoJson) {
        // TODO combine with type
        if (!pseudoJson.getIdentifier().equals(getName())) {
            // Suggest name while unformed.
            if (StringUtil.startsWithIgnoreCase(getName(), pseudoJson.getIdentifier())) {
                return Set.of(getName() + ":");
            }
            // If name is not a valid suggestion, suggest nothing.
            return Set.of();
        }

        String minAmountString = pseudoJson.getMappings().entrySet().stream().findFirst().map(Map.Entry::getValue).orElse("");

        // Find and suggest name in full pseudojson.
        return TabCompleter.completeInteger(minAmountString).stream().map(match -> {
            PseudoJson amount = new PseudoJson(getName());
            amount.put(getName(), match);
            return amount;
        }).map(PseudoJson::asString).collect(Collectors.toSet());
    }

    @Override
    public boolean isUnique() {
        return true;
    }

}
