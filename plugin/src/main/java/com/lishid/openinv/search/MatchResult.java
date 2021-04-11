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

import com.lishid.openinv.util.MessagePart;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MatchResult {

    public static final MatchResult NO_MATCH = new MatchResult();

    private final MessagePart[] result;

    private MatchResult() {
        result = null;
    }

    public MatchResult(MessagePart @NotNull [] result) {
        this.result = result;
    }

    public boolean isMatch() {
        return result != null;
    }

    public MessagePart @Nullable [] getMatch() {
        return result;
    }

}
