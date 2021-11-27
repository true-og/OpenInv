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

import com.lishid.openinv.search.match.ItemMatcher;
import com.lishid.openinv.search.match.MatchResult;
import com.lishid.openinv.search.match.Matchable;
import com.lishid.openinv.util.MessagePart;
import java.util.ArrayList;
import java.util.List;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

class MatchableChunk implements Matchable {

    private Chunk chunk;
    private World world;
    private int chunkX;
    private int chunkZ;

    MatchableChunk(@NotNull Chunk chunk) {
        this.chunk = chunk;
    }

    MatchableChunk(@NotNull World world, int chunkX, int chunkZ) {
        this.world = world;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }

    private Chunk getChunk() {
        if (chunk == null) {
            chunk = world.getChunkAt(chunkX, chunkZ);
        }
        return chunk;
    }

    @Override
    public @NotNull MatchResult match(@NotNull ItemMatcher matcher) {
        List<BlockState> blocks = new ArrayList<>();

        for (BlockState tileEntity : getChunk().getTileEntities()) {
            if (!(tileEntity instanceof InventoryHolder)) {
                continue;
            }

            Inventory inventory = ((InventoryHolder) tileEntity).getInventory();

            for (ItemStack content : inventory.getContents()) {
                if (matcher.matches(content)) {
                    blocks.add(tileEntity);
                    break;
                }
            }
        }

        if (blocks.isEmpty()) {
            return MatchResult.NO_MATCH;
        }

        MessagePart[] matches = new MessagePart[blocks.size()];
        for (int i = 0; i < matches.length; ++i) {
            BlockState state = blocks.get(i);
            matches[i] = new MessagePart(() -> getComponent(state), () -> getString(state));
        }

        return new MatchResult(matches);
    }

    private static BaseComponent getComponent(BlockState state) {
        TextComponent component = new TextComponent(state.getType().name());

        String command = String.format(
                "/execute in %s minecraft:tp %s %s %s",
                state.getWorld().getName(),
                state.getX(),
                state.getY() + 1,
                state.getZ());
        component.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));

        String hover = String.format(
                "%s{world:%s,x:%s,y:%s,z:%s}\n\nClick to teleport.",
                state.getType().getKey(),
                state.getWorld().getName(),
                state.getX(),
                state.getY(),
                state.getZ());
        Text hoverText = new Text(new BaseComponent[] { new TextComponent(hover)});
        component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverText));

        return component;
    }

    private static String getString(BlockState state) {
        return String.format(
                "%s{world:%s,x:%s,y:%s,z:%s}",
                state.getType().getKey(),
                state.getWorld().getName(),
                state.getX(),
                state.getY(),
                state.getZ());
    }

}
