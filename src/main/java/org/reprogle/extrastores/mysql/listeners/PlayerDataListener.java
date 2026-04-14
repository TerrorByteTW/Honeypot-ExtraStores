package org.reprogle.extrastores.mysql.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.reprogle.extrastores.ExtraStores;
import org.reprogle.extrastores.mysql.MySqlGateway;
import org.reprogle.extrastores.mysql.cache.PlayerCache;
import org.reprogle.honeypot.common.storageproviders.HoneypotPlayerHistoryObject;
import org.reprogle.honeypot.common.storageproviders.HoneypotPlayerObject;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class PlayerDataListener implements Listener {

    private final ExtraStores plugin;
    private final MySqlGateway gateway;
    private final PlayerCache playerCache;
    private final Executor dbExecutor;

    public PlayerDataListener(
        ExtraStores plugin,
        MySqlGateway gateway,
        PlayerCache playerCache,
        Executor dbExecutor
    ) {
        this.plugin = plugin;
        this.gateway = gateway;
        this.playerCache = playerCache;
        this.dbExecutor = dbExecutor;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        loadPlayerDataAsync(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        playerCache.unload(event.getPlayer().getUniqueId());
    }

    public void loadPlayerDataAsync(Player player) {
        UUID uuid = player.getUniqueId();
        String uuidString = uuid.toString();

        CompletableFuture.runAsync(() -> {
            try {
                int blocksBroken = gateway.loadPlayerCount(uuidString);
                List<HoneypotPlayerHistoryObject> history = gateway.loadPlayerHistory(uuidString);

                playerCache.setPlayer(new HoneypotPlayerObject(uuid, blocksBroken));
                playerCache.setHistory(uuid, history);
            } catch (SQLException exception) {
                plugin.getLogger().warning(
                    "Failed to load honeypot player data for " + player.getName() + ": " + exception.getMessage()
                );
            }
        }, dbExecutor);
    }
}