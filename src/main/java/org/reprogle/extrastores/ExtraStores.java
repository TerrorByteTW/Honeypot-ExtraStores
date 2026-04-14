package org.reprogle.extrastores;

import org.bukkit.plugin.java.JavaPlugin;

import org.reprogle.extrastores.mysql.DBStore;
import org.reprogle.honeypot.Registry;

import java.io.IOException;
import java.sql.SQLException;

public class ExtraStores extends JavaPlugin {

    private DBStore dbStore;

    // This is how you would register behavior providers
    @Override
    public void onLoad() {
        try {
            dbStore = new DBStore(this);
            if (dbStore.prepare()) {
                Registry.getStorageManagerRegistry().register(dbStore);
            } else {
                this.getLogger().warning("The only provider Honeypot ExtraStores registers is 'mysql', but it could not be registered. Disabling plugin...");
                this.getServer().getPluginManager().disablePlugin(this);
            }
        } catch (IOException e) {
            this.getLogger().severe("Could not register 'mysql' storage provider due to an error accessing config! This storage provider will be unavailable. If Honeypot is set to use this provider, it will fail to start in just a moment.");
            this.getLogger().severe("Raw error message: " + e.getMessage());
        } catch (SQLException e) {
            this.getLogger().severe("Could not register 'mysql' storage provider due to an issue with the database. If Honeypot is set to use this provider, it will fail to start in just a moment.");
            this.getLogger().severe("Raw error message: " + e.getMessage());
        }
    }

    @Override
    public void onEnable() {
        // It can only be null if it wasn't registered, and it isn't registered if the `prepare` method above doesn't clear
        if (Registry.getStorageManagerRegistry().getStorageProvider("mysql") != null) {
            this.getServer().getPluginManager().registerEvents(dbStore.getRegionLoadListener(), this);
            this.getServer().getPluginManager().registerEvents(dbStore.getPlayerDataListener(), this);
        }
        this.getLogger().info("Honeypot Extra Stores has loaded and registered successfully.");
    }

    @Override
    public void onDisable() {
        this.getLogger().info("Honeypot Extra Stores has been unloaded successfully.");
    }
}
