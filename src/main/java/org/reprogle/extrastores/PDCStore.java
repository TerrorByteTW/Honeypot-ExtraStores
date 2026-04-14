package org.reprogle.extrastores;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;
import org.reprogle.honeypot.common.storageproviders.HoneypotBlockObject;
import org.reprogle.honeypot.common.storageproviders.HoneypotPlayerHistoryObject;
import org.reprogle.honeypot.common.storageproviders.HoneypotStore;
import org.reprogle.honeypot.common.storageproviders.StorageProvider;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@HoneypotStore(name = "datacontainer")
public class PDCStore extends StorageProvider {

    final ExtraStores plugin;

    public PDCStore(ExtraStores plugin) {
        this.plugin = plugin;
    }

    @Override
    public void createHoneypotBlock(Block block, String action) {
        block.getWorld().getPersistentDataContainer().set(formatKey(block), PersistentDataType.STRING, action);
    }

    @Override
    public void removeHoneypotBlock(Block block) {
        block.getWorld().getPersistentDataContainer().remove(formatKey(block));
    }

    @Override
    public boolean isHoneypotBlock(Block block) {
        return block.getWorld().getPersistentDataContainer().has(formatKey(block));
    }

    @Override
    public HoneypotBlockObject getHoneypotBlock(Block block) {
        return isHoneypotBlock(block) ? new HoneypotBlockObject(block, getAction(block)) : null;
    }

    @Override
    public String getAction(Block block) {
        return block.getWorld().getPersistentDataContainer().get(formatKey(block), PersistentDataType.STRING);
    }

    @Override
    public void deleteAllHoneypotBlocks(@Nullable World world) {
        if (world == null) return;
        Set<NamespacedKey> keys = world.getPersistentDataContainer().getKeys();

        keys.stream()
            .filter(key -> key.getKey().startsWith("honeypot-container-"))
            .forEach(key -> world.getPersistentDataContainer().remove(key));
    }

    @Override
    public List<HoneypotBlockObject> getAllHoneypots(@Nullable World world) {
        if (world == null) return List.of();

        List<HoneypotBlockObject> blocks = new ArrayList<>();
        Set<NamespacedKey> keys = world.getPersistentDataContainer().getKeys();

        keys.stream()
            .filter(key -> key.getKey().startsWith("honeypot-container-"))
            .forEach(key -> {
                String coordinatesRaw = key.getKey().split("honeypot-container-")[1];
                String coordinates = coordinatesRaw.replace("_", ", ");
                blocks.add(new HoneypotBlockObject(world.getName(), coordinates,
                    world.getPersistentDataContainer().get(key, PersistentDataType.STRING)));
            });

        return blocks;
    }

    @Override
    public List<HoneypotBlockObject> getNearbyHoneypots(Location location, int radius) {
        return getAllHoneypots(location.getWorld()).stream()
            .filter(honeypot -> honeypot.getLocation().distance(location) <= radius)
            .collect(Collectors.toList());
    }

    @Override
    public void addPlayer(Player player, int blocksBroken) {
        player.getPersistentDataContainer().set(new NamespacedKey(plugin, "player-count"), PersistentDataType.INTEGER, blocksBroken);
    }

    @Override
    public void setPlayerCount(Player player, int count) {
        player.getPersistentDataContainer().set(new NamespacedKey(plugin, "player-count"), PersistentDataType.INTEGER, count);
    }

    @Override
    public int getCount(Player player) {
        return player.getPersistentDataContainer().getOrDefault(new NamespacedKey(plugin, "player-count"), PersistentDataType.INTEGER, 0);
    }

    @Override
    public int getCount(OfflinePlayer offlinePlayer) {
        return offlinePlayer.getPersistentDataContainer().getOrDefault(new NamespacedKey(plugin, "player-count"), PersistentDataType.INTEGER, 0);
    }

    @Override
    public void deleteAllHoneypotPlayers() {
        plugin.getLogger().warning("PDC does not support deleting all players, as Honeypot data is stored in the Player object itself.");
    }

    @Override
    public void addPlayerHistory(Player player, HoneypotBlockObject honeypotBlockObject, String action) {
        try {
            List<byte[]> list = player.getPersistentDataContainer().get(new NamespacedKey(plugin, "player-history"), PersistentDataType.LIST.listTypeFrom(PersistentDataType.BYTE_ARRAY));

            if (list == null) list = new ArrayList<>();
            list.add(serialize(honeypotBlockObject));

            player.getPersistentDataContainer().set(new NamespacedKey(plugin, "player-history"), PersistentDataType.LIST.listTypeFrom(PersistentDataType.BYTE_ARRAY), list);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not add player history record!");
        }
    }

    @Override
    public List<HoneypotPlayerHistoryObject> getPlayerHistory(Player player) {
        List<byte[]> list = player.getPersistentDataContainer().get(new NamespacedKey(plugin, "player-history"), PersistentDataType.LIST.listTypeFrom(PersistentDataType.BYTE_ARRAY));
        if (list == null) list = new ArrayList<>();

        List<HoneypotPlayerHistoryObject> history = new ArrayList<>();
        list.forEach(bytes -> {
            try {
                history.add((HoneypotPlayerHistoryObject) deserialize(bytes));
            } catch (IOException | ClassNotFoundException e) {
                plugin.getLogger().severe("Could not deserialize player history record!");
            }
        });

        return history;
    }

    @Override
    public void deletePlayerHistory(Player player, int... ints) {
        if (ints != null) {
            plugin.getLogger().warning("PDC does not support deleting a specific number of player history records.");
            return;
        }
        player.getPersistentDataContainer().remove(new NamespacedKey(plugin, "player-history"));
    }

    @Override
    public void deleteAllHistory() {
        plugin.getLogger().warning("A staff member tried to remove all player history, but PDC does not support doing so as player history is stored in the player itself.");
    }

    public NamespacedKey formatKey(Block block) {
        String coordinates = block.getX() + "_" + block.getY() + "_" + block.getZ();
        return new NamespacedKey(plugin, "honeypot-container-" + coordinates);
    }

    public static byte[] serialize(Object obj) throws IOException {
        try (ByteArrayOutputStream b = new ByteArrayOutputStream()) {
            try (ObjectOutputStream o = new ObjectOutputStream(b)) {
                o.writeObject(obj);
            }
            return b.toByteArray();
        }
    }

    public static Object deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ByteArrayInputStream b = new ByteArrayInputStream(bytes)) {
            try (ObjectInputStream o = new ObjectInputStream(b)) {
                return o.readObject();
            }
        }
    }

}
