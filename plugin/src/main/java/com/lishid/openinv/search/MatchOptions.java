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

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;

public class MatchOptions {

    static MatchOption isType(Material material) {
        return itemStack -> itemStack.getType() == material;
    }

    static MatchOption hasAmount(int minAmount) {
        return itemStack -> itemStack.getAmount() >= minAmount;
    }

    static MatchMetaOption hasEnchant(Enchantment enchantment, int minLevel) {
        return itemMeta -> {
            // Is the level unspecific?
            if (minLevel < 1) {
                return itemMeta.hasEnchant(enchantment);
            }
            return itemMeta.getEnchantLevel(enchantment) >= minLevel;
        };
    }

    static MatchMetaOption hasAnyEnchant(int minLevel) {
        return itemMeta -> {
            for (int enchantLevel : itemMeta.getEnchants().values()) {
                if (enchantLevel >= minLevel) {
                    return true;
                }
            }
            return false;
        };
    }

    private MatchOptions() {}

}
