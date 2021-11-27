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

package com.lishid.openinv.search.match;

import java.util.Collection;
import java.util.HashSet;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;

/**
 * A collection of multiple filters used to match items.
 */
public class ItemMatcher {

    private final Collection<MatchableItem> baseOptions;
    private final Collection<MatchableMeta> metaOptions;

    public ItemMatcher(
            Collection<MatchableItem> baseOptions,
            Collection<MatchableMeta> metaOptions) {
        this.baseOptions = new HashSet<>(baseOptions);
        this.metaOptions = new HashSet<>(metaOptions);
    }

    public boolean matches(@Nullable ItemStack other) {
        // Ensure item exists.
        if (other == null || other.getType() == Material.AIR) {
            return false;
        }

        // Match options against base item.
        for (MatchableItem baseOption : baseOptions) {
            if (!baseOption.matches(other)) {
                return false;
            }
        }

        // If no meta options exist, item matches.
        if (metaOptions.isEmpty()) {
            return true;
        }

        // Require item meta for meta options.
        ItemMeta meta;
        if (!other.hasItemMeta() || (meta = other.getItemMeta()) == null) {
            return false;
        }

        // Match meta-based options.
        for (MatchableMeta metaOption : metaOptions) {
            if (!metaOption.matches(meta)) {
                return false;
            }
        }

        return true;
    }

    public boolean isEmpty() {
        return baseOptions.isEmpty() && metaOptions.isEmpty();
    }

}
