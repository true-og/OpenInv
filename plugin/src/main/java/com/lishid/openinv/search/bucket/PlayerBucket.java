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

package com.lishid.openinv.search.bucket;

import com.lishid.openinv.OpenInv;
import com.lishid.openinv.search.match.Matchable;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class PlayerBucket implements SearchBucket {

    private final OpenInv plugin;
    private final OfflinePlayer[] players;
    private int index = -1;

    public PlayerBucket(OpenInv plugin, boolean online) {
        this.plugin = plugin;
        if (online) {
            this.players = Bukkit.getOnlinePlayers().toArray(new Player[0]);
        } else {
            this.players = Bukkit.getOfflinePlayers();
        }
    }

    @Override
    public boolean hasNext() {
        return index < players.length - 1;
    }

    @Override
    public @NotNull Matchable next() {
        return new MatchablePlayer(plugin, players[++index]);
    }

    @Override
    public int size() {
        return 1;
    }

}
