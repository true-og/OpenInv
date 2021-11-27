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
import com.lishid.openinv.search.match.MatchableItem;
import com.lishid.openinv.util.PseudoJson;
import com.lishid.openinv.util.TabCompleter;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ItemTypeOption implements CompletableOption<MatchableItem> {

    @Override
    public @NotNull String getName() {
        return "type";
    }

    @Override
    public @Nullable PseudoOption<MatchableItem> parse(@NotNull CommandSender sender, @NotNull PseudoJson pseudoJson) {
        String typeName = pseudoJson.getMappings().entrySet().stream().findFirst().map(Map.Entry::getValue).orElse(null);
        Material material = StringConverters.toMaterial(typeName);
        if (material == null) {
            // Invalid material, warn about matcher spec.
            return null;
        }
        return new PseudoOption<>(pseudoJson, ItemMatchables.isType(material));
    }

    @Override
    public @NotNull Collection<String> suggestOptions(@NotNull CommandSender sender, @NotNull PseudoJson pseudoJson) {
        if (!pseudoJson.getIdentifier().equals(getName())) {
            // Suggest name while unformed.
            if (StringUtil.startsWithIgnoreCase(getName(), pseudoJson.getIdentifier())) {
                return Set.of(getName() + ":");
            }
            // If name is not a valid suggestion, suggest nothing.
            return Set.of();
        }

        String typeName = pseudoJson.getMappings().entrySet().stream().findFirst().map(Map.Entry::getValue).orElse("");

        // Find and suggest matching types in full pseudojson.
        return TabCompleter.completeMaterial(typeName).stream().map(match -> {
            PseudoJson type = new PseudoJson(getName());
            type.put(getName(), match);
            return type;
        }).map(PseudoJson::asString).collect(Collectors.toSet());
    }

    @Override
    public boolean isUnique() {
        return true;
    }

}
