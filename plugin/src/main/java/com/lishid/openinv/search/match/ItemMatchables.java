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

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ItemMatchables {

    public static MatchableItem isType(@NotNull Material material) {
        return itemStack -> itemStack.getType() == material;
    }

    public static MatchableItem hasAmount(int minAmount) {
        return itemStack -> itemStack.getAmount() >= minAmount;
    }

    public static MatchableMeta hasEnchant(@Nullable Enchantment enchantment, @Nullable Integer minLevel) {
        return itemMeta -> {
            if (minLevel == null) {
                if (enchantment == null) {
                    // Level and enchantment not specified, any enchantment of any level matches.
                    return !itemMeta.getEnchants().isEmpty();
                }

                // Level not specified, any level of enchantment matches.
                return itemMeta.hasEnchant(enchantment);
            }

            if (enchantment != null) {
                // Level and enchantment are specified, enchantment must meet or exceed level.
                return itemMeta.getEnchantLevel(enchantment) >= minLevel;
            }

            // Enchantment not specified, any enchantment that meets or exceeds level matches.
            for (int enchantLevel : itemMeta.getEnchants().values()) {
                if (enchantLevel >= minLevel) {
                    return true;
                }
            }

            return false;
        };
    }

    private ItemMatchables() {}

}
