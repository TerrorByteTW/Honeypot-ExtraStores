package org.reprogle.extrastores;

import org.bukkit.plugin.java.JavaPlugin;

import org.reprogle.honeypot.Registry;

public class ExtraStores extends JavaPlugin {

    public static ExtraStores plugin;

    // This is how you would register behavior providers
    @Override
    public void onLoad() {
        Registry.getStorageManagerRegistry().register(new PDCStore(this));
    }

    @Override
    public void onEnable() {
        plugin = this;
        this.getLogger().info("Honeypot Extra Stores has loaded and registered successfully.");
    }

    @Override
    public void onDisable() {
        this.getLogger().info("Honeypot Extra Stores has been unloaded successfully.");
    }
}
