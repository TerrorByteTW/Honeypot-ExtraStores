package org.reprogle.extrastores.mysql;

import dev.dejvokep.boostedyaml.YamlDocument;
import org.reprogle.extrastores.mysql.records.CachedRegion;
import org.reprogle.honeypot.common.storageproviders.HoneypotBlockObject;
import org.reprogle.honeypot.common.storageproviders.HoneypotPlayerHistoryObject;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public final class MySqlGateway {

    private static final String CREATE_REGIONS_SQL = """
        CREATE TABLE IF NOT EXISTS honeypot_regions (
            id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
            world VARCHAR(64) NOT NULL,
            action VARCHAR(64) NOT NULL,
            min_x INT NOT NULL,
            max_x INT NOT NULL,
            min_y INT NOT NULL,
            max_y INT NOT NULL,
            min_z INT NOT NULL,
            max_z INT NOT NULL,
            footprint POLYGON NOT NULL,
            PRIMARY KEY (id),
            INDEX idx_world (world),
            INDEX idx_world_y (world, min_y, max_y),
            INDEX idx_world_bounds (world, min_x, max_x, min_y, max_y, min_z, max_z),
            SPATIAL INDEX sp_footprint (footprint)
        ) ENGINE=InnoDB
        """;

    private static final String CREATE_HISTORY_SQL = """
        CREATE TABLE IF NOT EXISTS honeypot_history (
            `datetime` VARCHAR(32) NOT NULL,
            `playerName` VARCHAR(64) NOT NULL,
            `playerUUID` VARCHAR(36) NOT NULL,
            `coordinates` VARCHAR(64) NOT NULL,
            `world` VARCHAR(64) NOT NULL,
            `type` VARCHAR(32) NOT NULL,
            `action` VARCHAR(64) NOT NULL,
            INDEX idx_player_history (`playerUUID`, `datetime`)
        ) ENGINE=InnoDB
        """;

    private static final String CREATE_PLAYERS_SQL = """
        CREATE TABLE IF NOT EXISTS honeypot_players (
            `playerUUID` VARCHAR(64) NOT NULL,
            `blocksBroken` INT NOT NULL,
            PRIMARY KEY (`playerUUID`)
        ) ENGINE=InnoDB
        """;

    private final String jdbcUrl;
    private final String jdbcUser;
    private final String jdbcPassword;

    public MySqlGateway(YamlDocument config) {
        String host = config.getString("mysql.host", "127.0.0.1");
        int port = config.getInt("mysql.port", 3306);
        String database = config.getString("mysql.database", "minecraft");
        this.jdbcUser = config.getString("mysql.username", "root");
        this.jdbcPassword = config.getString("mysql.password", "");
        String parameters = config.getString(
            "mysql.parameters",
            "useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
        );

        this.jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + database + "?" + parameters;
    }

    public void createTables() throws SQLException {
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(CREATE_REGIONS_SQL);
            statement.execute(CREATE_HISTORY_SQL);
            statement.execute(CREATE_PLAYERS_SQL);
        }
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, jdbcUser, jdbcPassword);
    }

    public List<CachedRegion> queryRegions(
        String world,
        int minX,
        int maxX,
        int minY,
        int maxY,
        int minZ,
        int maxZ
    ) throws SQLException {
        String sql = """
            SELECT id, world, action, min_x, max_x, min_y, max_y, min_z, max_z
            FROM honeypot_regions
            WHERE world = ?
              AND min_x <= ?
              AND max_x >= ?
              AND min_y <= ?
              AND max_y >= ?
              AND min_z <= ?
              AND max_z >= ?
            """;

        List<CachedRegion> out = new ArrayList<>();

        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, world);
            ps.setInt(2, maxX);
            ps.setInt(3, minX);
            ps.setInt(4, maxY);
            ps.setInt(5, minY);
            ps.setInt(6, maxZ);
            ps.setInt(7, minZ);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new CachedRegion(
                        rs.getLong("id"),
                        rs.getString("world"),
                        rs.getString("action"),
                        rs.getInt("min_x"),
                        rs.getInt("max_x"),
                        rs.getInt("min_y"),
                        rs.getInt("max_y"),
                        rs.getInt("min_z"),
                        rs.getInt("max_z")
                    ));
                }
            }
        }

        return out;
    }

    public List<CachedRegion> loadAllRegions() throws SQLException {
        String sql = """
            SELECT id, world, action, min_x, max_x, min_y, max_y, min_z, max_z
            FROM honeypot_regions
            ORDER BY id ASC
            """;

        List<CachedRegion> out = new ArrayList<>();

        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                out.add(new CachedRegion(
                    rs.getLong("id"),
                    rs.getString("world"),
                    rs.getString("action"),
                    rs.getInt("min_x"),
                    rs.getInt("max_x"),
                    rs.getInt("min_y"),
                    rs.getInt("max_y"),
                    rs.getInt("min_z"),
                    rs.getInt("max_z")
                ));
            }
        }

        return out;
    }

    public List<CachedRegion> loadAllRegions(String world) throws SQLException {
        String sql = """
            SELECT id, world, action, min_x, max_x, min_y, max_y, min_z, max_z
            FROM honeypot_regions
            WHERE world = ?
            ORDER BY id ASC
            """;

        List<CachedRegion> out = new ArrayList<>();

        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setString(1, world);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new CachedRegion(
                        rs.getLong("id"),
                        rs.getString("world"),
                        rs.getString("action"),
                        rs.getInt("min_x"),
                        rs.getInt("max_x"),
                        rs.getInt("min_y"),
                        rs.getInt("max_y"),
                        rs.getInt("min_z"),
                        rs.getInt("max_z")
                    ));
                }
            }
        }

        return out;
    }

    public long insertRegion(CachedRegion region) throws SQLException {
        String sql = """
            INSERT INTO honeypot_regions (
                world,
                action,
                min_x,
                max_x,
                min_y,
                max_y,
                min_z,
                max_z,
                footprint
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ST_GeomFromText(?, 0))
            """;

        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, region.world());
            ps.setString(2, region.action());
            ps.setInt(3, region.minX());
            ps.setInt(4, region.maxX());
            ps.setInt(5, region.minY());
            ps.setInt(6, region.maxY());
            ps.setInt(7, region.minZ());
            ps.setInt(8, region.maxZ());
            ps.setString(9, region.toFootprintWkt());

            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }

        throw new SQLException("No generated key returned when inserting honeypot region");
    }

    public void deleteRegionById(long id) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(
                 "DELETE FROM honeypot_regions WHERE id = ?"
             )) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    public void deleteAllRegions() throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement("DELETE FROM honeypot_regions")) {
            ps.executeUpdate();
        }
    }

    public void deleteAllRegions(String world) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(
                 "DELETE FROM honeypot_regions WHERE world = ?"
             )) {
            ps.setString(1, world);
            ps.executeUpdate();
        }
    }

    public void upsertPlayer(String playerUuidString, int blocksBroken) throws SQLException {
        String sql = """
            INSERT INTO honeypot_players (`playerUUID`, `blocksBroken`)
            VALUES (?, ?)
            ON DUPLICATE KEY UPDATE `blocksBroken` = VALUES(`blocksBroken`)
            """;

        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerUuidString);
            ps.setInt(2, blocksBroken);
            ps.executeUpdate();
        }
    }

    public int loadPlayerCount(String playerUuidString) throws SQLException {
        String sql = "SELECT `blocksBroken` FROM honeypot_players WHERE `playerUUID` = ?";

        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerUuidString);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("blocksBroken");
                }
            }
        }

        return 0;
    }

    public void deleteAllPlayers() throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement("DELETE FROM honeypot_players")) {
            ps.executeUpdate();
        }
    }

    public void insertHistory(HoneypotPlayerHistoryObject history) throws SQLException {
        String sql = """
            INSERT INTO honeypot_history (
                `datetime`,
                `playerName`,
                `playerUUID`,
                `coordinates`,
                `world`,
                `type`,
                `action`
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

        HoneypotBlockObject honeypot = history.getHoneypot();

        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, history.getDateTime());
            ps.setString(2, history.getPlayer());
            ps.setString(3, history.getUUID());
            ps.setString(4, honeypot.getCoordinates());
            ps.setString(5, honeypot.getWorld());
            ps.setString(6, history.getType());
            ps.setString(7, honeypot.getAction());
            ps.executeUpdate();
        }
    }

    public List<HoneypotPlayerHistoryObject> loadPlayerHistory(String playerUuidString) throws SQLException {
        String sql = """
            SELECT
                `datetime`,
                `playerName`,
                `playerUUID`,
                `coordinates`,
                `world`,
                `type`,
                `action`
            FROM honeypot_history
            WHERE `playerUUID` = ?
            ORDER BY `datetime` ASC
            """;

        List<HoneypotPlayerHistoryObject> out = new ArrayList<>();

        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerUuidString);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    HoneypotBlockObject honeypot = new HoneypotBlockObject(
                        rs.getString("world"),
                        rs.getString("coordinates"),
                        rs.getString("action")
                    );

                    out.add(new HoneypotPlayerHistoryObject(
                        rs.getString("datetime"),
                        rs.getString("playerName"),
                        rs.getString("playerUUID"),
                        honeypot,
                        rs.getString("type")
                    ));
                }
            }
        }

        return out;
    }

    public void deleteHistoryRow(HoneypotPlayerHistoryObject history) throws SQLException {
        String sql = """
            DELETE FROM honeypot_history
            WHERE `datetime` = ?
              AND `playerName` = ?
              AND `playerUUID` = ?
              AND `coordinates` = ?
              AND `world` = ?
              AND `type` = ?
              AND `action` = ?
            LIMIT 1
            """;

        HoneypotBlockObject honeypot = history.getHoneypot();

        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, history.getDateTime());
            ps.setString(2, history.getPlayer());
            ps.setString(3, history.getUUID());
            ps.setString(4, honeypot.getCoordinates());
            ps.setString(5, honeypot.getWorld());
            ps.setString(6, history.getType());
            ps.setString(7, honeypot.getAction());
            ps.executeUpdate();
        }
    }

    public void deleteAllHistory() throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement("DELETE FROM honeypot_history")) {
            ps.executeUpdate();
        }
    }

    public void deletePlayerHistory(String playerUuid) throws SQLException {
        String sql = "DELETE FROM honeypot_history WHERE playerUUID = ?";

        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerUuid);
            ps.executeUpdate();
        }
    }
}