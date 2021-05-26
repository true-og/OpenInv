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
import org.jetbrains.annotations.NotNull;

/**
 * Data holder for completable options.
 *
 * @param <T> the type of data
 */
public class PseudoOption<T> {

    private final PseudoJson pseudoJson;
    private final T option;

    PseudoOption(PseudoJson pseudoJson, T option) {
        this.pseudoJson = pseudoJson;
        this.option = option;
    }

    /**
     * Get the <code>PseudoJson</code> representation of the data.
     *
     * @return the data
     */
    public @NotNull PseudoJson getPseudoJson() {
        return pseudoJson;
    }

    /**
     * Get the option parsed from the <code>PseudoJson</code>.
     *
     * @return the option
     */
    public @NotNull T getOption() {
        return option;
    }

}
