package org.reprogle.extrastores.mysql.cache;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.jetbrains.annotations.Nullable;
import org.reprogle.extrastores.mysql.Coordinates;
import org.reprogle.extrastores.mysql.records.CachedRegion;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class RegionCache {

    private final ConcurrentMap<Long, CachedRegion> regionsById = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ConcurrentMap<Long, Set<Long>>> regionIdsByWorldChunk = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Set<Long>> loadedChunksByWorld = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Set<Long>> loadingChunksByWorld = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Object> worldLocks = new ConcurrentHashMap<>();

    public boolean beginChunkLoad(String world, long chunkKey) {
        Object lock = worldLocks.computeIfAbsent(world, k -> new Object());

        synchronized (lock) {
            Set<Long> loaded = loadedChunksByWorld.computeIfAbsent(world, k -> ConcurrentHashMap.newKeySet());
            Set<Long> loading = loadingChunksByWorld.computeIfAbsent(world, k -> ConcurrentHashMap.newKeySet());

            if (loaded.contains(chunkKey) || loading.contains(chunkKey)) {
                return false;
            }

            loading.add(chunkKey);
            return true;
        }
    }

    public boolean isChunkStillActive(String world, long chunkKey) {
        Object lock = worldLocks.computeIfAbsent(world, k -> new Object());

        synchronized (lock) {
            Set<Long> loaded = loadedChunksByWorld.get(world);
            Set<Long> loading = loadingChunksByWorld.get(world);

            return (loaded != null && loaded.contains(chunkKey))
                || (loading != null && loading.contains(chunkKey));
        }
    }

    public void finishChunkLoad(String world, long chunkKey) {
        Object lock = worldLocks.computeIfAbsent(world, k -> new Object());

        synchronized (lock) {
            Set<Long> loaded = loadedChunksByWorld.computeIfAbsent(world, k -> ConcurrentHashMap.newKeySet());
            Set<Long> loading = loadingChunksByWorld.get(world);

            loaded.add(chunkKey);

            if (loading != null) {
                loading.remove(chunkKey);
                if (loading.isEmpty()) {
                    loadingChunksByWorld.remove(world, loading);
                }
            }

            cleanupWorldStateIfEmpty(world);
        }
    }

    public void unloadChunk(String world, long chunkKey) {
        Object lock = worldLocks.computeIfAbsent(world, k -> new Object());

        synchronized (lock) {
            Set<Long> loaded = loadedChunksByWorld.get(world);
            if (loaded != null) {
                loaded.remove(chunkKey);
                if (loaded.isEmpty()) {
                    loadedChunksByWorld.remove(world, loaded);
                }
            }

            Set<Long> loading = loadingChunksByWorld.get(world);
            if (loading != null) {
                loading.remove(chunkKey);
                if (loading.isEmpty()) {
                    loadingChunksByWorld.remove(world, loading);
                }
            }

            ConcurrentMap<Long, Set<Long>> worldMap = regionIdsByWorldChunk.get(world);
            if (worldMap != null) {
                Set<Long> candidateRegionIds = worldMap.remove(chunkKey);
                if (candidateRegionIds != null && !candidateRegionIds.isEmpty()) {
                    for (Long regionId : candidateRegionIds) {
                        CachedRegion region = regionsById.get(regionId);
                        if (region == null) {
                            continue;
                        }

                        boolean stillReferencedByLoadedChunk = false;
                        for (Long coveredChunk : region.coveredChunks()) {
                            Set<Long> stillLoaded = loadedChunksByWorld.get(world);
                            if (stillLoaded != null && stillLoaded.contains(coveredChunk)) {
                                stillReferencedByLoadedChunk = true;
                                break;
                            }
                        }

                        if (!stillReferencedByLoadedChunk) {
                            regionsById.remove(regionId);

                            for (Long coveredChunk : region.coveredChunks()) {
                                Set<Long> ids = worldMap.get(coveredChunk);
                                if (ids != null) {
                                    ids.remove(regionId);
                                    if (ids.isEmpty()) {
                                        worldMap.remove(coveredChunk, ids);
                                    }
                                }
                            }
                        }
                    }
                }

                if (worldMap.isEmpty()) {
                    regionIdsByWorldChunk.remove(world, worldMap);
                }
            }

            cleanupWorldStateIfEmpty(world);
        }
    }

    public void put(CachedRegion region) {
        CachedRegion previous = regionsById.putIfAbsent(region.id(), region);
        if (previous != null) {
            return;
        }

        ConcurrentMap<Long, Set<Long>> worldMap =
            regionIdsByWorldChunk.computeIfAbsent(region.world(), k -> new ConcurrentHashMap<>());

        for (long chunkKey : region.coveredChunks()) {
            worldMap.computeIfAbsent(chunkKey, k -> ConcurrentHashMap.newKeySet()).add(region.id());
        }
    }

    public void putAll(Collection<CachedRegion> regions) {
        for (CachedRegion region : regions) {
            put(region);
        }
    }

    public @Nullable CachedRegion findContaining(Block block) {
        String world = block.getWorld().getName();
        ConcurrentMap<Long, Set<Long>> worldMap = regionIdsByWorldChunk.get(world);
        if (worldMap == null) {
            return null;
        }

        long chunkKey = Coordinates.chunkKey(block.getX() >> 4, block.getZ() >> 4);

        Set<Long> loaded = loadedChunksByWorld.get(world);
        if (loaded == null || !loaded.contains(chunkKey)) {
            return null;
        }

        Set<Long> ids = worldMap.get(chunkKey);
        if (ids == null || ids.isEmpty()) {
            return null;
        }

        for (Long id : ids) {
            CachedRegion region = regionsById.get(id);
            if (region != null && region.contains(block.getX(), block.getY(), block.getZ())) {
                return region;
            }
        }

        return null;
    }

    public List<CachedRegion> getNearby(Location location, int radius) {
        World world = location.getWorld();
        if (world == null) {
            return List.of();
        }

        String worldName = world.getName();
        ConcurrentMap<Long, Set<Long>> worldMap = regionIdsByWorldChunk.get(worldName);
        Set<Long> loaded = loadedChunksByWorld.get(worldName);

        if (worldMap == null || loaded == null || loaded.isEmpty()) {
            return List.of();
        }

        int centerChunkX = location.getBlockX() >> 4;
        int centerChunkZ = location.getBlockZ() >> 4;
        Set<Long> seenIds = new HashSet<>();
        List<CachedRegion> out = new ArrayList<>();

        for (int chunkX = centerChunkX - radius; chunkX <= centerChunkX + radius; chunkX++) {
            for (int chunkZ = centerChunkZ - radius; chunkZ <= centerChunkZ + radius; chunkZ++) {
                long chunkKey = Coordinates.chunkKey(chunkX, chunkZ);
                if (!loaded.contains(chunkKey)) {
                    continue;
                }

                Set<Long> ids = worldMap.get(chunkKey);
                if (ids == null) {
                    continue;
                }

                for (Long id : ids) {
                    if (seenIds.add(id)) {
                        CachedRegion region = regionsById.get(id);
                        if (region != null) {
                            out.add(region);
                        }
                    }
                }
            }
        }

        out.sort(Comparator.comparingLong(CachedRegion::id));
        return out;
    }

    public List<CachedRegion> removeExactSingleBlock(String world, int x, int y, int z) {
        ConcurrentMap<Long, Set<Long>> worldMap = regionIdsByWorldChunk.get(world);
        if (worldMap == null) {
            return List.of();
        }

        long chunkKey = Coordinates.chunkKey(x >> 4, z >> 4);
        Set<Long> ids = worldMap.get(chunkKey);
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        List<CachedRegion> removed = new ArrayList<>();
        for (Long id : new ArrayList<>(ids)) {
            CachedRegion region = regionsById.get(id);
            if (region != null && region.isExactSingleBlock(world, x, y, z)) {
                removeById(id);
                removed.add(region);
            }
        }

        return removed;
    }

    public void removeById(long id) {
        CachedRegion region = regionsById.remove(id);
        if (region == null) {
            return;
        }

        ConcurrentMap<Long, Set<Long>> worldMap = regionIdsByWorldChunk.get(region.world());
        if (worldMap == null) {
            return;
        }

        for (long chunkKey : region.coveredChunks()) {
            Set<Long> ids = worldMap.get(chunkKey);
            if (ids == null) {
                continue;
            }

            ids.remove(id);
            if (ids.isEmpty()) {
                worldMap.remove(chunkKey, ids);
            }
        }

        if (worldMap.isEmpty()) {
            regionIdsByWorldChunk.remove(region.world(), worldMap);
        }
    }

    public void clearWorld(String world) {
        Object lock = worldLocks.computeIfAbsent(world, k -> new Object());

        synchronized (lock) {
            List<Long> idsToRemove = new ArrayList<>();
            for (CachedRegion region : regionsById.values()) {
                if (region.world().equals(world)) {
                    idsToRemove.add(region.id());
                }
            }

            for (Long id : idsToRemove) {
                regionsById.remove(id);
            }

            regionIdsByWorldChunk.remove(world);
            loadedChunksByWorld.remove(world);
            loadingChunksByWorld.remove(world);
            worldLocks.remove(world);
        }
    }

    public void clearAll() {
        regionsById.clear();
        regionIdsByWorldChunk.clear();
        loadedChunksByWorld.clear();
        loadingChunksByWorld.clear();
        worldLocks.clear();
    }

    private void cleanupWorldStateIfEmpty(String world) {
        ConcurrentMap<Long, Set<Long>> worldMap = regionIdsByWorldChunk.get(world);
        Set<Long> loaded = loadedChunksByWorld.get(world);
        Set<Long> loading = loadingChunksByWorld.get(world);

        boolean noRegions = worldMap == null || worldMap.isEmpty();
        boolean noLoaded = loaded == null || loaded.isEmpty();
        boolean noLoading = loading == null || loading.isEmpty();

        if (noRegions && noLoaded && noLoading) {
            worldLocks.remove(world);
        }
    }
}