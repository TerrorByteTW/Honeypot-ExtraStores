package org.reprogle.extrastores;

import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.serialization.standard.StandardSerializer;
import dev.dejvokep.boostedyaml.serialization.standard.TypeAdapter;
import org.apache.commons.lang3.NotImplementedException;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;
import org.reprogle.honeypot.common.storageproviders.*;

import java.io.*;
import java.util.*;

@HoneypotStore(name = "datacontainer")
public class FileStore extends StorageProvider {

    final ExtraStores plugin;
    final YamlDocument store;

    public FileStore(ExtraStores plugin) throws IOException {
        this.plugin = plugin;
        this.store = YamlDocument.create(
            new File(plugin.getDataFolder(), "store.yml"),
            plugin.getResource("config.yml")
        );

        StandardSerializer.getDefault().register(HoneypotBlockObject.class, honeypotBlockObjectAdapter);
        StandardSerializer.getDefault().register(HoneypotPlayerHistoryObject.class, honeypotPlayerHistoryObjectAdapter);
        StandardSerializer.getDefault().register(HoneypotPlayerObject.class, honeypotPlayerObjectAdapter);
    }

    @Override
    public void createHoneypotBlock(Block block, String action) {
        store.set(formatKey(block), new HoneypotBlockObject(block, action));
    }

    @Override
    public void removeHoneypotBlock(Block block) {
        if (store.get(formatKey(block)) != null) store.remove(formatKey(block));
    }

    @Override
    public boolean isHoneypotBlock(Block block) {
        return store.get(formatKey(block)) != null;
    }

    @Override
    public HoneypotBlockObject getHoneypotBlock(Block block) {
        return isHoneypotBlock(block) ? new HoneypotBlockObject(block, getAction(block)) : null;
    }

    @Override
    public String getAction(Block block) {
        return ((HoneypotBlockObject) store.get(formatKey(block))).getAction();
    }

    @Override
    public void deleteAllHoneypotBlocks(@Nullable World world) {
        if (world == null) return;
        store.getSection("honeypots").getRoutes(false).forEach(store::remove);
    }

    @Override
    public List<HoneypotBlockObject> getAllHoneypots(@Nullable World world) {
        throw new NotImplementedException();
    }

    @Override
    public List<HoneypotBlockObject> getNearbyHoneypots(Location location, int radius) {
        throw new NotImplementedException();
    }

    @Override
    public void addPlayer(Player player, int blocksBroken) {
        throw new NotImplementedException();
    }

    @Override
    public void setPlayerCount(Player player, int count) {
        throw new NotImplementedException();
    }

    @Override
    public int getCount(Player player) {
        throw new NotImplementedException();
    }

    @Override
    public int getCount(OfflinePlayer offlinePlayer) {
        throw new NotImplementedException();
    }

    @Override
    public void deleteAllHoneypotPlayers() {
        throw new NotImplementedException();
    }

    @Override
    public void addPlayerHistory(Player player, HoneypotBlockObject honeypotBlockObject, String action) {
        throw new NotImplementedException();
    }

    @Override
    public List<HoneypotPlayerHistoryObject> getPlayerHistory(Player player) {
        throw new NotImplementedException();
    }

    @Override
    public void deletePlayerHistory(Player player, int... ints) {
        throw new NotImplementedException();
    }

    @Override
    public void deleteAllHistory() {
        throw new NotImplementedException();
    }

    public String formatKey(Block block) {
        return "honeypots." + block.getWorld().getName() + "." + block.getX() + "." + block.getY() + "." + block.getZ();
    }

    TypeAdapter<HoneypotBlockObject> honeypotBlockObjectAdapter = new TypeAdapter<HoneypotBlockObject>() {

        @Override
        public @NonNull Map<Object, Object> serialize(@NonNull HoneypotBlockObject object) {
            Map<Object, Object> map = new HashMap<>();
            map.put("worldName", object.getWorld());
            map.put("coordinates", object.getCoordinates());
            map.put("action", object.getAction());
            return map;
        }

        @Override
        public @NonNull HoneypotBlockObject deserialize(@NonNull Map<Object, Object> map) {
            String worldName = (String) map.get("worldName");
            String coordinates = (String) map.get("coordinates");
            String action = (String) map.get("action");
            return new HoneypotBlockObject(worldName, coordinates, action);
        }
    };

    TypeAdapter<HoneypotPlayerObject> honeypotPlayerObjectAdapter = new TypeAdapter<HoneypotPlayerObject>() {

        @Override
        public @NonNull Map<Object, Object> serialize(@NonNull HoneypotPlayerObject object) {
            Map<Object, Object> map = new HashMap<>();
            map.put("UUID", object.getUUID().toString());
            map.put("blocksBroken", object.getBlocksBroken());
            return map;
        }

        @Override
        public @NonNull HoneypotPlayerObject deserialize(@NonNull Map<Object, Object> map) {
            UUID uuid = UUID.fromString((String) map.get("UUID"));
            int blocksBroken = (int) map.get("blocksBroken");
            return new HoneypotPlayerObject(uuid, blocksBroken);
        }
    };

    TypeAdapter<HoneypotPlayerHistoryObject> honeypotPlayerHistoryObjectAdapter = new TypeAdapter<HoneypotPlayerHistoryObject>() {

        @Override
        public @NonNull Map<Object, Object> serialize(@NonNull HoneypotPlayerHistoryObject object) {
            Map<Object, Object> map = new HashMap<>();
            map.put("dateTime", object.getDateTime());
            map.put("player", object.getPlayer());
            map.put("UUID", object.getUUID());
            map.put("hbo", object.getHoneypot());
            map.put("type", object.getType());
            return map;
        }

        @Override
        public @NonNull HoneypotPlayerHistoryObject deserialize(@NonNull Map<Object, Object> map) {
            String dateTime = (String) map.get("dateTime");
            String player = (String) map.get("player");
            String uuid = (String) map.get("UUID");
            HoneypotBlockObject hbo = (HoneypotBlockObject) map.get("hbo");
            String type = (String) map.get("type");
            return new HoneypotPlayerHistoryObject(dateTime, player, uuid, hbo, type);
        }
    };

}
