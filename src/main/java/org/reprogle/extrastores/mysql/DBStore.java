package org.reprogle.extrastores.mysql;

import dev.dejvokep.boostedyaml.YamlDocument;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.Nullable;
import org.reprogle.extrastores.ExtraStores;
import org.reprogle.extrastores.mysql.cache.PlayerCache;
import org.reprogle.extrastores.mysql.cache.RegionCache;
import org.reprogle.extrastores.mysql.listeners.PlayerDataListener;
import org.reprogle.extrastores.mysql.listeners.RegionLoadListener;
import org.reprogle.extrastores.mysql.records.CachedRegion;
import org.reprogle.honeypot.common.storageproviders.*;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

@HoneypotStore(name = "mysql")
public final class DBStore extends StorageProvider implements AutoCloseable {

    private final ExtraStores plugin;
    private final YamlDocument config;
    private ExecutorService dbExecutor;

    private final AtomicLong tempRegionIds = new AtomicLong(-1L);

    private RegionCache regionCache;
    private PlayerCache playerCache;
    private MySqlGateway gateway;

    @Getter
    private RegionLoadListener regionLoadListener;

    @Getter
    private PlayerDataListener playerDataListener;

    public DBStore(ExtraStores plugin) throws IOException, SQLException {
        this.plugin = plugin;
        this.config = YamlDocument.create(
            new File(plugin.getDataFolder(), "config.yml"),
            plugin.getResource("config.yml")
        );
    }

    public boolean prepare() throws SQLException {
        if (config.getBoolean("mysql.enabled", false) == false) {
            plugin.getLogger().warning("MySQL is not enabled in the config, will not register 'mysql' storage provider");
            return false;
        }

        this.gateway = new MySqlGateway(config);
        this.regionCache = new RegionCache();
        this.playerCache = new PlayerCache();

        int poolSize = Math.max(2, config.getInt("mysql.worker-threads", 4));
        this.dbExecutor = Executors.newFixedThreadPool(poolSize, runnable -> {
            Thread thread = new Thread(runnable, "ExtraStores-MySQL");
            thread.setDaemon(true);
            return thread;
        });

        gateway.createTables();

        this.regionLoadListener = new RegionLoadListener(
            plugin,
            gateway,
            regionCache,
            dbExecutor
        );

        this.playerDataListener = new PlayerDataListener(
            plugin,
            gateway,
            playerCache,
            dbExecutor
        );

        for (Player player : Bukkit.getOnlinePlayers()) {
            playerDataListener.loadPlayerDataAsync(player);
        }

        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                regionLoadListener.loadChunk(chunk);
            }
        }

        return true;
    }

    @Override
    public void close() {
        HandlerList.unregisterAll(regionLoadListener);
        HandlerList.unregisterAll(playerDataListener);
        dbExecutor.shutdown();
    }

    @Override
    public void createHoneypotBlock(Block block, String action) {
        CachedRegion tempRegion = CachedRegion.singleBlock(
            tempRegionIds.getAndDecrement(),
            block.getWorld().getName(),
            action,
            block.getX(),
            block.getY(),
            block.getZ()
        );

        regionCache.put(tempRegion);

        dbExecutor.execute(() -> {
            try {
                long id = gateway.insertRegion(tempRegion);
                CachedRegion persisted = new CachedRegion(
                    id,
                    tempRegion.world(),
                    tempRegion.action(),
                    tempRegion.minX(),
                    tempRegion.maxX(),
                    tempRegion.minY(),
                    tempRegion.maxY(),
                    tempRegion.minZ(),
                    tempRegion.maxZ()
                );

                regionCache.removeById(tempRegion.id());
                regionCache.put(persisted);
            } catch (SQLException exception) {
                regionCache.removeById(tempRegion.id());
                plugin.getLogger().warning(
                    "Failed to persist honeypot block at " + block.getLocation() + ": " + exception.getMessage()
                );
            }
        });
    }

    @Override
    public void removeHoneypotBlock(Block block) {
        String world = block.getWorld().getName();
        int x = block.getX();
        int y = block.getY();
        int z = block.getZ();

        List<CachedRegion> removed = regionCache.removeExactSingleBlock(world, x, y, z);
        if (removed.isEmpty()) {
            return;
        }

        dbExecutor.execute(() -> {
            for (CachedRegion region : removed) {
                if (region.id() <= 0L) {
                    continue;
                }

                try {
                    gateway.deleteRegionById(region.id());
                } catch (SQLException exception) {
                    plugin.getLogger().warning(
                        "Failed to delete honeypot region id " + region.id() + ": " + exception.getMessage()
                    );
                }
            }
        });
    }

    @Override
    public boolean isHoneypotBlock(Block block) {
        return regionCache.findContaining(block) != null;
    }

    @Override
    public HoneypotBlockObject getHoneypotBlock(Block block) {
        CachedRegion region = regionCache.findContaining(block);
        if (region == null) {
            return null;
        }

        return new HoneypotBlockObject(
            block.getWorld().getName(),
            block.getX(),
            block.getY(),
            block.getZ(),
            region.action()
        );
    }

    @Override
    public String getAction(Block block) {
        CachedRegion region = regionCache.findContaining(block);
        return region == null ? "" : region.action();
    }

    @Override
    public void deleteAllHoneypotBlocks(@Nullable World world) {
        if (world == null) {
            regionCache.clearAll();
        } else {
            regionCache.clearWorld(world.getName());
        }

        dbExecutor.execute(() -> {
            try {
                if (world == null) {
                    gateway.deleteAllRegions();
                } else {
                    gateway.deleteAllRegions(world.getName());
                }
            } catch (SQLException exception) {
                plugin.getLogger().warning("Failed deleting honeypot blocks: " + exception.getMessage());
            }
        });
    }

    @Override
    public List<HoneypotBlockObject> getAllHoneypots(@Nullable World world) {
        try {
            List<CachedRegion> regions = gateway.loadAllRegions();

            List<HoneypotBlockObject> out = new ArrayList<>(regions.size());
            for (CachedRegion region : regions) {
                out.add(new HoneypotBlockObject(
                    region.world(),
                    region.minX(),
                    region.minY(),
                    region.minZ(),
                    region.action()
                ));
            }

            return out;
        } catch (SQLException exception) {
            plugin.getLogger().warning("Failed to synchronously load all honeypots: " + exception.getMessage());
            return List.of();
        }
    }

    @Override
    public List<HoneypotBlockObject> getNearbyHoneypots(Location location, int radius) {
        List<CachedRegion> regions = regionCache.getNearby(location, radius);
        List<HoneypotBlockObject> out = new ArrayList<>(regions.size());

        for (CachedRegion region : regions) {
            out.add(new HoneypotBlockObject(
                region.world(),
                region.minX(),
                region.minY(),
                region.minZ(),
                region.action()
            ));
        }

        return out;
    }

    @Override
    public void addPlayer(Player player, int blocksBroken) {
        UUID uuid = player.getUniqueId();
        String uuidString = uuid.toString();

        playerCache.setPlayer(new HoneypotPlayerObject(uuid, blocksBroken));

        dbExecutor.execute(() -> {
            try {
                gateway.upsertPlayer(uuidString, blocksBroken);
            } catch (SQLException exception) {
                plugin.getLogger().warning(
                    "Failed to persist honeypot player " + player.getName() + ": " + exception.getMessage()
                );
            }
        });
    }

    @Override
    public void setPlayerCount(Player player, int blocksBroken) {
        UUID uuid = player.getUniqueId();
        String uuidString = uuid.toString();

        playerCache.setPlayer(new HoneypotPlayerObject(uuid, blocksBroken));

        dbExecutor.execute(() -> {
            try {
                gateway.upsertPlayer(uuidString, blocksBroken);
            } catch (SQLException exception) {
                plugin.getLogger().warning(
                    "Failed to update honeypot player count for " + player.getName() + ": " + exception.getMessage()
                );
            }
        });
    }

    @Override
    public int getCount(Player player) {
        HoneypotPlayerObject object = playerCache.getPlayer(player.getUniqueId());
        if (object != null) {
            return object.getBlocksBroken();
        }

        try {
            int count = gateway.loadPlayerCount(player.getUniqueId().toString());
            playerCache.setPlayer(new HoneypotPlayerObject(player.getUniqueId(), count));
            return count;
        } catch (SQLException exception) {
            plugin.getLogger().warning(
                "Failed to read honeypot player count for " + player.getName() + ": " + exception.getMessage()
            );
            return 0;
        }
    }

    @Override
    public int getCount(OfflinePlayer offlinePlayer) {
        HoneypotPlayerObject object = playerCache.getPlayer(offlinePlayer.getUniqueId());
        if (object != null) {
            return object.getBlocksBroken();
        }

        try {
            return gateway.loadPlayerCount(offlinePlayer.getUniqueId().toString());
        } catch (SQLException exception) {
            plugin.getLogger().warning(
                "Failed to read honeypot player count for " + offlinePlayer.getUniqueId() + ": " + exception.getMessage()
            );
            return 0;
        }
    }

    @Override
    public void deleteAllHoneypotPlayers() {
        playerCache.clearPlayers();

        dbExecutor.execute(() -> {
            try {
                gateway.deleteAllPlayers();
            } catch (SQLException exception) {
                plugin.getLogger().warning("Failed to delete all honeypot players: " + exception.getMessage());
            }
        });
    }

    @Override
    public void addPlayerHistory(Player player, HoneypotBlockObject honeypotBlockObject, String type) {
        HoneypotPlayerHistoryObject history = new HoneypotPlayerHistoryObject(
            OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            player,
            honeypotBlockObject,
            type
        );

        playerCache.addHistory(player.getUniqueId(), history);

        dbExecutor.execute(() -> {
            try {
                gateway.insertHistory(history);
            } catch (SQLException exception) {
                plugin.getLogger().warning(
                    "Failed to insert honeypot history for " + player.getName() + ": " + exception.getMessage()
                );
            }
        });
    }

    @Override
    public List<HoneypotPlayerHistoryObject> getPlayerHistory(Player player) {
        List<HoneypotPlayerHistoryObject> cached = playerCache.getHistory(player.getUniqueId());
        if (cached != null) {
            return new ArrayList<>(cached);
        }

        try {
            List<HoneypotPlayerHistoryObject> loaded = gateway.loadPlayerHistory(player.getUniqueId().toString());
            playerCache.setHistory(player.getUniqueId(), loaded);
            return new ArrayList<>(loaded);
        } catch (SQLException exception) {
            plugin.getLogger().warning(
                "Failed to load honeypot history for " + player.getName() + ": " + exception.getMessage()
            );
            return List.of();
        }
    }

    @Override
    public void deletePlayerHistory(Player player, int... indexes) {
        UUID uuid = player.getUniqueId();

        List<HoneypotPlayerHistoryObject> current;
        List<HoneypotPlayerHistoryObject> cached = playerCache.getHistory(uuid);
        if (cached != null) {
            current = new ArrayList<>(cached);
        } else {
            try {
                current = gateway.loadPlayerHistory(uuid.toString());
            } catch (SQLException exception) {
                plugin.getLogger().warning(
                    "Failed to load honeypot history for delete operation for " + player.getName() + ": "
                        + exception.getMessage()
                );
                return;
            }
        }

        if (indexes == null || indexes.length == 0) {
            playerCache.setHistory(uuid, List.of());

            dbExecutor.execute(() -> {
                try {
                    gateway.deletePlayerHistory(uuid.toString());
                } catch (SQLException exception) {
                    plugin.getLogger().warning(
                        "Failed to delete all honeypot history for " + player.getName() + ": "
                            + exception.getMessage()
                    );
                }
            });

            return;
        }

        int count = Math.max(0, indexes[0]);
        if (count == 0 || current.isEmpty()) {
            return;
        }

        int deleteCount = Math.min(count, current.size());
        List<HoneypotPlayerHistoryObject> toDelete = new ArrayList<>(current.subList(0, deleteCount));

        int[] removeIndexes = new int[deleteCount];
        for (int i = 0; i < deleteCount; i++) {
            removeIndexes[i] = i;
        }
        playerCache.removeHistoryIndexes(uuid, removeIndexes);

        dbExecutor.execute(() -> {
            for (HoneypotPlayerHistoryObject history : toDelete) {
                try {
                    gateway.deleteHistoryRow(history);
                } catch (SQLException exception) {
                    plugin.getLogger().warning(
                        "Failed to delete honeypot history row for " + player.getName() + ": "
                            + exception.getMessage()
                    );
                }
            }
        });
    }

    @Override
    public void deleteAllHistory() {
        playerCache.clearHistories();

        dbExecutor.execute(() -> {
            try {
                gateway.deleteAllHistory();
            } catch (SQLException exception) {
                plugin.getLogger().warning("Failed to delete all honeypot history: " + exception.getMessage());
            }
        });
    }
}