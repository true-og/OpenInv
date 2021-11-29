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

import com.lishid.openinv.search.match.MatchResult;
import com.lishid.openinv.search.match.Matchable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

public class ChunkBucket implements SearchBucket {

    private final World world;
    private final boolean load;
    private final List<ChunkCoord> chunks;
    protected int index = -1;
    private Boolean paper = null;

    public ChunkBucket(@NotNull World world, int chunkX, int chunkZ, int radius, boolean load) {
        this.world = world;
        this.load = load;
        this.chunks = new ArrayList<>();
        // Order chunks based (loosely) on proximity.
        spiralRectangle(radius, (deltaX, deltaZ) -> chunks.add(new ChunkCoord(chunkX + deltaX, chunkZ + deltaZ)));
    }

    @Override
    public @NotNull Matchable next() throws IndexOutOfBoundsException {
        ChunkCoord chunkCoord = chunks.get(++index);

        if (!world.isChunkGenerated(chunkCoord.x(), chunkCoord.z())) {
            return Matchable.EMPTY;
        }

        if (!load && !world.isChunkLoaded(chunkCoord.x(), chunkCoord.z())) {
            return Matchable.EMPTY;
        }

        // If async chunk loading is not available, load chunk when checking.
        if (!isPaper()) {
            return new MatchableChunk(world, chunkCoord.x(), chunkCoord.z());
        }

        CompletableFuture<Chunk> chunkAt = world.getChunkAtAsync(chunkCoord.x(), chunkCoord.z());

        Chunk chunk;
        try {
            chunk = chunkAt.get();
        } catch (InterruptedException | ExecutionException e) {
            return matcher -> MatchResult.NO_MATCH;
        }

        return new MatchableChunk(chunk);
    }

    private boolean isPaper() {
        if (paper == null) {
            try {
                // Check for Paper.
                Class.forName("com.destroystokyo.paper.PaperConfig");
                paper = true;
            } catch (ClassNotFoundException e) {
                paper = false;
            }
        }

        return paper;
    }

    @Override
    public boolean hasNext() {
        return index < chunks.size() - 1;
    }

    @Override
    public int size() {
        return chunks.size();
    }

    /**
     * Produce a series of integer coordinates starting at 0, 0 that extend outward to form a centered square,
     * producing closer proximity results sooner than a traditional double for loop from min to max.
     *
     * @param radius the radius of the square
     * @param coordConsumer the method consuming the values produced
     */
    private static void spiralRectangle(int radius, BiConsumer<Integer, Integer> coordConsumer) {
        for (int x = 0; x <= radius; ++x) {
            for (int z = 0; z <= radius; ++z) {
                coordConsumer.accept(x, z);
                if (x != 0) {
                    coordConsumer.accept(-x, z);
                    if (z != 0) {
                        coordConsumer.accept(-x, -z);
                    }
                }
                if (z != 0) {
                    coordConsumer.accept(x, -z);
                }
            }
        }
    }

}
