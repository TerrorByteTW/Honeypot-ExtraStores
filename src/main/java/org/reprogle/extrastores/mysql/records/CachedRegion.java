package org.reprogle.extrastores.mysql.records;

import org.reprogle.extrastores.mysql.Coordinates;

import java.util.HashSet;
import java.util.Set;

public record CachedRegion(
    long id,
    String world,
    String action,
    int minX,
    int maxX,
    int minY,
    int maxY,
    int minZ,
    int maxZ
) {
    public static CachedRegion singleBlock(long id, String world, String action, int x, int y, int z) {
        return new CachedRegion(id, world, action, x, x, y, y, z, z);
    }

    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX
            && y >= minY && y <= maxY
            && z >= minZ && z <= maxZ;
    }

    public boolean isExactSingleBlock(String world, int x, int y, int z) {
        return this.world.equals(world)
            && minX == x && maxX == x
            && minY == y && maxY == y
            && minZ == z && maxZ == z;
    }

    public Set<Long> coveredChunks() {
        int minChunkX = minX >> 4;
        int maxChunkX = maxX >> 4;
        int minChunkZ = minZ >> 4;
        int maxChunkZ = maxZ >> 4;

        Set<Long> out = new HashSet<>();
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                out.add(Coordinates.chunkKey(chunkX, chunkZ));
            }
        }

        return out;
    }

    public boolean coversAnyChunk(Set<Long> chunks) {
        int minChunkX = minX >> 4;
        int maxChunkX = maxX >> 4;
        int minChunkZ = minZ >> 4;
        int maxChunkZ = maxZ >> 4;

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (chunks.contains(Coordinates.chunkKey(chunkX, chunkZ))) {
                    return true;
                }
            }
        }

        return false;
    }

    public String toFootprintWkt() {
        int polyMinX = minX;
        int polyMaxX = maxX + 1;
        int polyMinZ = minZ;
        int polyMaxZ = maxZ + 1;

        return "POLYGON(("
            + polyMinX + " " + polyMinZ + ", "
            + polyMaxX + " " + polyMinZ + ", "
            + polyMaxX + " " + polyMaxZ + ", "
            + polyMinX + " " + polyMaxZ + ", "
            + polyMinX + " " + polyMinZ
            + "))";
    }

    public boolean coversChunk(long chunkKey) {
        int chunkX = Coordinates.chunkX(chunkKey);
        int chunkZ = Coordinates.chunkZ(chunkKey);

        int minChunkX = minX >> 4;
        int maxChunkX = maxX >> 4;
        int minChunkZ = minZ >> 4;
        int maxChunkZ = maxZ >> 4;

        return chunkX >= minChunkX && chunkX <= maxChunkX
            && chunkZ >= minChunkZ && chunkZ <= maxChunkZ;
    }
}