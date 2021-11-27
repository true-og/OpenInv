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

import com.github.jikoo.planarwrappers.util.StringConverters;
import com.lishid.openinv.search.match.ItemMatchables;
import com.lishid.openinv.search.match.MatchableMeta;
import com.lishid.openinv.util.PseudoJson;
import com.lishid.openinv.util.TabCompleter;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ItemEnchantOption implements CompletableOption<MatchableMeta> {

    @Override
    public @NotNull String getName() {
        return "enchant";
    }

    @Override
    public @Nullable PseudoOption<MatchableMeta> parse(@NotNull CommandSender sender, @NotNull PseudoJson pseudoJson) {
        Enchantment enchantment = null;
        Integer enchantLevel = null;
        for (Map.Entry<String, String> entry : pseudoJson.getMappings().entrySet()) {
            if (entry.getValue().isBlank()) {
                continue;
            }
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

        return new PseudoOption<>(pseudoJson, ItemMatchables.hasEnchant(enchantment, enchantLevel));
    }

    @Override
    public @NotNull Collection<String> suggestOptions(@NotNull CommandSender sender, @NotNull PseudoJson pseudoJson) {
        if (!pseudoJson.getIdentifier().equals(getName())) {
            if (!StringUtil.startsWithIgnoreCase(getName(), pseudoJson.getIdentifier())) {
                // If enchant is not a valid suggestion, suggest nothing.
                return Set.of();
            }

            // Suggest all options while unformed.
            Set<String> set = new HashSet<>();
            PseudoJson enchant = new PseudoJson(getName());
            set.add(enchant.asString());
            enchant.put("type", "");
            set.add(enchant.asString());
            enchant.put("level", "");
            set.add(enchant.asString());
            enchant.remove("type");
            set.add(enchant.asString());
            return set;
        }

        Collection<String> enchantments = null;
        Integer enchantLevel = null;
        for (Map.Entry<String, String> entry : pseudoJson.getMappings().entrySet()) {
            if (entry.getValue().isBlank()) {
                continue;
            }
            if (entry.getKey().indexOf('l') >= 0) {
                try {
                    enchantLevel = Integer.parseInt(entry.getValue());
                } catch (NumberFormatException e) {
                    // Level is present but cannot be parsed. Default 0.
                    enchantLevel = 0;
                }
            }
            if (entry.getKey().indexOf('t') >= 0) {
                Enchantment enchant = StringConverters.toEnchant(entry.getValue());
                if (enchant == null) {
                    // Enchantment is specified but can't be parsed, tab complete.
                    enchantments = TabCompleter.completeKeyed(entry.getValue(), Enchantment.values());
                } else {
                    enchantments = Set.of(entry.getValue());
                }
            }
        }

        PseudoJson enchant = new PseudoJson(getName());
        if (enchantLevel != null) {
            enchant.put("level", String.valueOf(enchantLevel));
        }

        if (enchantments == null) {
            return Set.of(enchant.asString());
        }

        return enchantments.stream().map(enchantment -> {
            // Reuse is fine, single-threaded.
            enchant.put("type", enchantment);
            return enchant;
        }).map(PseudoJson::asString).collect(Collectors.toSet());
    }

    @Override
    public boolean isUnique() {
        return false;
    }

}
