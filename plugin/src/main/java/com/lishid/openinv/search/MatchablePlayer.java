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

import com.lishid.openinv.OpenInv;
import com.lishid.openinv.util.MessagePart;
import java.util.UUID;
import java.util.function.Supplier;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;

public class MatchablePlayer implements Matchable {

    private final OpenInv plugin;
    private final OfflinePlayer offlinePlayer;

    public MatchablePlayer(OpenInv plugin, OfflinePlayer offlinePlayer) {
        this.plugin = plugin;
        this.offlinePlayer = offlinePlayer;
    }

    @Override
    public @NotNull MatchResult match(ItemMatcher matcher) {

        Player player;
        if (offlinePlayer instanceof Player) {
            player = (Player) offlinePlayer;
        } else {
            player = plugin.loadPlayer(offlinePlayer);
        }

        if (player == null) {
            return MatchResult.NO_MATCH;
        }

        boolean matchInv = false;
        boolean matchEnder = false;

        PlayerInventory playerInventory = player.getInventory();

        // Check inventory. Modern implementations include armor and off hand in contents.
        for (ItemStack content : playerInventory.getContents()) {
            if (matcher.matches(content)) {
                matchInv = true;
                break;
            }
        }

        // If no matches are found yet, check cursor just in case.
        if (!matchInv && matcher.matches(player.getOpenInventory().getCursor())) {
            matchInv = true;
        }

        // Check ender chest.
        for (ItemStack content : player.getEnderChest().getContents()) {
            if (matcher.matches(content)) {
                matchEnder = true;
                break;
            }
        }

        if (!matchInv && !matchEnder) {
            return MatchResult.NO_MATCH;
        }

        String name = player.getName();
        UUID uuid = player.getUniqueId();

        if (matchInv && matchEnder) {
            return new MatchResult(new MessagePart[] {
                    new MessagePart(() -> getBoth(name, uuid), () -> name + " (inv, ender)")
            });
        }

        Supplier<BaseComponent> componentSupplier;
        Supplier<String> stringSupplier;

        if (matchInv) {
            componentSupplier = () -> getSingle(name, () -> getInvClick(uuid), () -> getInvHover(name));
            stringSupplier = () -> name + " (inv)";
        } else {
            componentSupplier = () -> getSingle(name, () -> getEnderClick(uuid), () -> getEnderHover(name));
            stringSupplier = () -> name + " (ender)";
        }

        return new MatchResult(new MessagePart[] { new MessagePart(componentSupplier, stringSupplier) });
    }

    private static BaseComponent getBoth(String name, UUID uuid) {
        BaseComponent component = new TextComponent(name + " (inv,");
        component.setClickEvent(getInvClick(uuid));
        component.setHoverEvent(getInvHover(name));

        BaseComponent enderComponent = new TextComponent(" ender)");
        enderComponent.setClickEvent(getEnderClick(uuid));
        enderComponent.setHoverEvent(getEnderHover(name));

        component.addExtra(enderComponent);
        return component;
    }

    private static BaseComponent getSingle(String name, Supplier<ClickEvent> click, Supplier<HoverEvent> hover) {
        BaseComponent component = new TextComponent(name);
        component.setClickEvent(click.get());
        component.setHoverEvent(hover.get());
        return component;
    }

    private static ClickEvent getInvClick(UUID uuid) {
        return getClick("/openinv:openinv ", uuid);
    }

    private static HoverEvent getInvHover(String name) {
        return getHover("/openinv ", name);
    }

    private static ClickEvent getEnderClick(UUID uuid) {
        return getClick("/openinv:openender ", uuid);
    }

    private static HoverEvent getEnderHover(String name) {
        return getHover("/openender ", name);
    }

    private static ClickEvent getClick(String command, UUID uuid) {
        // Actual command run is not exposed to user, so there's no reason not to use UUID and be precise.
        return new ClickEvent(ClickEvent.Action.RUN_COMMAND, command + uuid);
    }

    private static HoverEvent getHover(String command, String name) {
        BaseComponent[] text = new BaseComponent[] { new TextComponent(command + name) };
        return new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(text));
    }

}
