package org.reprogle.extrastores.mysql.listeners;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.reprogle.extrastores.ExtraStores;
import org.reprogle.extrastores.mysql.Coordinates;
import org.reprogle.extrastores.mysql.MySqlGateway;
import org.reprogle.extrastores.mysql.cache.RegionCache;
import org.reprogle.extrastores.mysql.records.CachedRegion;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

public final class RegionLoadListener implements Listener {

    private final ExtraStores plugin;
    private final MySqlGateway gateway;
    private final RegionCache regionCache;
    private final Executor dbExecutor;

    public RegionLoadListener(
        ExtraStores plugin,
        MySqlGateway gateway,
        RegionCache regionCache,
        Executor dbExecutor
    ) {
        this.plugin = plugin;
        this.gateway = gateway;
        this.regionCache = regionCache;
        this.dbExecutor = dbExecutor;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChunkLoad(ChunkLoadEvent event) {
        loadChunk(event.getChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        String worldName = chunk.getWorld().getName();
        long chunkKey = Coordinates.chunkKey(chunk.getX(), chunk.getZ());

        regionCache.unloadChunk(worldName, chunkKey);
    }

    public void loadChunk(Chunk chunk) {
        World world = chunk.getWorld();
        String worldName = world.getName();
        long chunkKey = Coordinates.chunkKey(chunk.getX(), chunk.getZ());

        if (!regionCache.beginChunkLoad(worldName, chunkKey)) {
            return;
        }

        final int minBlockX = chunk.getX() << 4;
        final int maxBlockX = minBlockX + 15;
        final int minBlockY = world.getMinHeight();
        final int maxBlockY = world.getMaxHeight() - 1;
        final int minBlockZ = chunk.getZ() << 4;
        final int maxBlockZ = minBlockZ + 15;

        CompletableFuture
            .supplyAsync(() -> {
                try {
                    return gateway.queryRegions(
                        worldName,
                        minBlockX,
                        maxBlockX,
                        minBlockY,
                        maxBlockY,
                        minBlockZ,
                        maxBlockZ
                    );
                } catch (SQLException exception) {
                    throw new CompletionException(exception);
                }
            }, dbExecutor)
            .thenAccept(regions -> {
                if (!regionCache.isChunkStillActive(worldName, chunkKey)) {
                    regionCache.finishChunkLoad(worldName, chunkKey);
                    regionCache.unloadChunk(worldName, chunkKey);
                    return;
                }

                List<CachedRegion> filtered = new ArrayList<>();
                for (CachedRegion region : regions) {
                    if (region.coversChunk(chunkKey)) {
                        filtered.add(region);
                    }
                }

                regionCache.putAll(filtered);
                regionCache.finishChunkLoad(worldName, chunkKey);
            })
            .exceptionally(throwable -> {
                regionCache.finishChunkLoad(worldName, chunkKey);
                regionCache.unloadChunk(worldName, chunkKey);

                plugin.getLogger().warning(
                    "Failed to async load honeypot regions for chunk "
                        + worldName + " [" + chunk.getX() + ", " + chunk.getZ() + "]: "
                        + throwable.getMessage()
                );
                return null;
            });
    }
}