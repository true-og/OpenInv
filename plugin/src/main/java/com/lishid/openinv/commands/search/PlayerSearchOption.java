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

import com.lishid.openinv.OpenInv;
import com.lishid.openinv.search.PlayerBucket;
import com.lishid.openinv.util.Permissions;
import com.lishid.openinv.util.PseudoJson;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PlayerSearchOption implements CompletableOption<PlayerBucket> {

    private final OpenInv plugin;

    public PlayerSearchOption(OpenInv plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean matches(@NotNull CommandSender sender, @NotNull PseudoJson pseudoJson) {
        return pseudoJson.getIdentifier().startsWith("player") && Permissions.SEARCH_PLAYERS_ONLINE.hasPermission(sender);
    }

    @Override
    public @Nullable PseudoOption<PlayerBucket> parse(@NotNull CommandSender sender, @NotNull PseudoJson pseudoJson) {
        Optional<Boolean> optional = pseudoJson.getMappings().entrySet().stream()
                .filter(entry -> entry.getKey().contains("offline"))
                .findFirst()
                .map(Map.Entry::getValue)
                .map(Boolean::parseBoolean);

        return new PseudoOption<>(
                pseudoJson, new PlayerBucket(plugin,
                !(optional.orElse(false) && Permissions.SEARCH_PLAYERS_OFFLINE.hasPermission(sender))));
    }

    @Override
    public @NotNull Collection<String> suggestOptions(@NotNull CommandSender sender, @NotNull PseudoJson pseudoJson) {
        // TODO
        return null;
    }

    @Override
    public boolean isUnique() {
        return true;
    }

}
