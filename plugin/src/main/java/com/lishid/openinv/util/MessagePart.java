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

import java.util.List;
import java.util.function.Supplier;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MessagePart {

    private final Supplier<BaseComponent> playerProvider;
    private final Supplier<String> consoleProvider;
    private @Nullable BaseComponent playerComponent;
    private @Nullable String consoleComponent;

    public MessagePart(@NotNull Supplier<@NotNull BaseComponent> playerProvider) {
        this(playerProvider, () -> unwrapComponent(playerProvider.get()));
    }

    public MessagePart(
            @NotNull Supplier<@NotNull BaseComponent> playerProvider,
            @NotNull Supplier<@NotNull String> consoleProvider) {
        this.playerProvider = playerProvider;
        this.consoleProvider = consoleProvider;
    }

    public BaseComponent forPlayer() {
        if (this.playerComponent == null) {
            this.playerComponent = playerProvider.get();
        }
        return this.playerComponent;
    }

    public String forConsole() {
        if (consoleComponent == null) {
            consoleComponent = consoleProvider.get();
        }
        return consoleComponent;
    }

    private static String unwrapComponent(BaseComponent component) {
        StringBuilder builder = new StringBuilder();
        ClickEvent clickEvent = component.getClickEvent();

        if (clickEvent != null) {
            // Prefer click events, i.e. "Check out our GitHub" with a link -> raw link
            builder.append(clickEvent.getValue());
        } else if (component instanceof TextComponent) {
            builder.append(((TextComponent) component).getText());
        }

        List<BaseComponent> extra = component.getExtra();
        if (extra != null) {
            for (BaseComponent extraComponent : extra) {
                builder.append(unwrapComponent(extraComponent));
            }
        }

        return builder.toString();
    }

}
