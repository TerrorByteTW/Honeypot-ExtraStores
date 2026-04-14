package org.reprogle.extrastores.mysql.cache;
import org.jetbrains.annotations.Nullable;
import org.reprogle.honeypot.common.storageproviders.HoneypotPlayerHistoryObject;
import org.reprogle.honeypot.common.storageproviders.HoneypotPlayerObject;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class PlayerCache {

    private final ConcurrentMap<UUID, HoneypotPlayerObject> players = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, CopyOnWriteArrayList<HoneypotPlayerHistoryObject>> histories =
        new ConcurrentHashMap<>();

    public void setPlayer(HoneypotPlayerObject player) {
        players.put(player.getUUID(), player);
    }

    public @Nullable HoneypotPlayerObject getPlayer(UUID uuid) {
        return players.get(uuid);
    }

    public void setHistory(UUID uuid, List<HoneypotPlayerHistoryObject> history) {
        histories.put(uuid, new CopyOnWriteArrayList<>(history));
    }

    public @Nullable List<HoneypotPlayerHistoryObject> getHistory(UUID uuid) {
        return histories.get(uuid);
    }

    public void addHistory(UUID uuid, HoneypotPlayerHistoryObject history) {
        histories.computeIfAbsent(uuid, k -> new CopyOnWriteArrayList<>()).add(history);
    }

    public void removeHistoryIndexes(UUID uuid, int[] indexes) {
        CopyOnWriteArrayList<HoneypotPlayerHistoryObject> list = histories.get(uuid);
        if (list == null || indexes.length == 0) {
            return;
        }

        int[] sorted = Arrays.stream(indexes).distinct().sorted().toArray();
        for (int i = sorted.length - 1; i >= 0; i--) {
            int index = sorted[i];
            if (index >= 0 && index < list.size()) {
                list.remove(index);
            }
        }
    }

    public void unload(UUID uuid) {
        players.remove(uuid);
        histories.remove(uuid);
    }

    public void clearPlayers() {
        players.clear();
    }

    public void clearHistories() {
        histories.clear();
    }
}