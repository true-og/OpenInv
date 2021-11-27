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

package com.lishid.openinv.util;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

/**
 * A data container for JSON-esque expressions.
 *
 * <p>Ex: A ChunkBucket can be expressed <code>chunks{world:world_name,x:0,z:0,radius:10,load:true}</code>. The
 * starting "chunks" identifies it as a ChunkBucket and the content in braces contains the construction detail.
 * However, a simpler detail, such as a MatchableItem, may be specified <code>type:IRON_SWORD</code> where "type"
 * is both the identifier of the MatchableItem and the key of the sole value.
 *
 * <p>A best-effort will be made to match other start and finish demarcation techniques.
 */
public class PseudoJson {

    private static final char[] BRACE_OPEN = new char[] { '{', '[', '(' };
    private static final char[] BRACE_CLOSE = new char[] { '}', ']', ')' };
    private static final Set<Character> RESERVED_CHARACTERS = new HashSet<>();

    static {
        for (char brace : BRACE_OPEN) {
            RESERVED_CHARACTERS.add(brace);
        }
        for (char brace : BRACE_CLOSE) {
            RESERVED_CHARACTERS.add(brace);
        }
        RESERVED_CHARACTERS.add(',');
        RESERVED_CHARACTERS.add(':');
    }

    private final String identifier;
    private final Map<String, String> mappings;

    public PseudoJson(@NotNull String identifier) {
        this(identifier, new HashMap<>());
    }

    public PseudoJson(@NotNull String identifier, @NotNull Map<String, String> mappings) {
        this.identifier = identifier;
        mappings.forEach(PseudoJson::validate);
        this.mappings = mappings;
    }

    public @NotNull String getIdentifier() {
        return identifier;
    }

    public @NotNull Optional<String> put(@NotNull String key, @NotNull String value) {
        validate(key, value);

        return Optional.ofNullable(this.mappings.put(key, value));
    }

    public @NotNull Optional<String> get(@NotNull String key) {
        return Optional.ofNullable(this.mappings.get(key));
    }

    public @NotNull Map<String, String> getMappings() {
        return Collections.unmodifiableMap(mappings);
    }

    public Optional<String> remove(@NotNull String key) {
        return Optional.ofNullable(this.mappings.get(key));
    }

    public String asString() {
        StringBuilder builder = new StringBuilder(identifier);

        builder.append('{');

        for (Iterator<Map.Entry<String, String>> iterator = mappings.entrySet().iterator(); iterator.hasNext();) {
            Map.Entry<String, String> entry = iterator.next();
            builder.append(entry.getKey()).append(':').append(entry.getValue());
            if (iterator.hasNext()) {
                builder.append(',');
            }
        }

        builder.append('}');

        return builder.toString();
    }

    @Override
    public String toString() {
        return asString();
    }

    private static void validate(String key, String value) {
        if (RESERVED_CHARACTERS.stream().anyMatch(character -> key.indexOf(character) >= 0 || value.indexOf(character) >= 0)) {
            throw new IllegalArgumentException(String.format("Primitive pseudo-JSON reserves characters %s", RESERVED_CHARACTERS));
        }
    }

    public static PseudoJson fromString(String pseudoJson) {
        int open = -1;
        for (char brace : BRACE_OPEN) {
            open = pseudoJson.indexOf(brace);
            if (open >= 0) {
                break;
            }
        }

        String identifier = getIdentifier(pseudoJson, open);

        int close = -1;
        for (char brace : BRACE_CLOSE) {
            close = pseudoJson.lastIndexOf(brace);
            if (close >= 0) {
                break;
            }
        }

        if (open >= 0) {
            if (close <= open) {
                // Ignore bad ending brace.
                pseudoJson = pseudoJson.substring(open + 1);
            } else {
                // Use only bracketed content
                pseudoJson = pseudoJson.substring(open + 1, close);
            }
        }

        return new PseudoJson(identifier, getMappings(pseudoJson));
    }

    private static String getIdentifier(String pseudoJson, int braceOpen) {
        int identifierEnd;
        if (braceOpen >= 0) {
            identifierEnd = braceOpen;
        } else {
            // No quote, use first mapping.
            identifierEnd = pseudoJson.indexOf(':');
            if (identifierEnd == -1) {
                // No mappings, use whole string.
                return pseudoJson;
            }
        }

        return pseudoJson.substring(0, identifierEnd);
    }

    private static Map<String, String> getMappings(String pseudoJson) {
        String[] mappings = pseudoJson.split(",");

        Map<String, String> data = new HashMap<>();
        for (String mapping : mappings) {
            String[] datum = mapping.split(":");
            if (datum.length >= 2) {
                data.put(datum[0], datum[1]);
            }
        }

        return data;
    }

}
