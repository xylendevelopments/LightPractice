package gg.lightpractice.database;

import gg.lightpractice.config.ConfigFile;

/**
 * Immutable view over {@code database.yml}. Settings are re-read on reload, an already established
 * connection is only replaced when the connection details actually changed.
 */
public final class DatabaseSettings {

    private final String uri;
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private final String authenticationDatabase;
    private final boolean ssl;
    private final int poolSize;
    private final int threads;
    private final int connectTimeoutMillis;
    private final int serverSelectionTimeoutMillis;
    private final boolean kickOnFailure;
    private final long saveIntervalTicks;
    private final int historyPageSize;
    private final int historyRetention;
    private final String profileCollection;
    private final String matchCollection;
    private final String kitCollection;
    private final String tournamentCollection;
    private final String banCollection;
    private final boolean storeKits;

    private DatabaseSettings(ConfigFile file) {
        this.uri = trim(file.getString("connection.uri", ""));
        this.host = trim(file.getString("connection.host", "127.0.0.1"));
        this.port = clamp(file.getInt("connection.port", 27017), 1, 65535);
        this.database = trim(file.getString("connection.database", "lightpractice"));
        this.username = trim(file.getString("connection.username", ""));
        this.password = file.getString("connection.password", "");
        this.authenticationDatabase = trim(file.getString("connection.authentication-database", "admin"));
        this.ssl = file.getBoolean("connection.ssl", false);
        this.poolSize = clamp(file.getInt("connection.pool-size", 10), 1, 200);
        this.threads = clamp(file.getInt("connection.threads", 2), 1, 16);
        this.connectTimeoutMillis = clamp(file.getInt("connection.connect-timeout-millis", 5000), 500, 60000);
        this.serverSelectionTimeoutMillis =
                clamp(file.getInt("connection.server-selection-timeout-millis", 5000), 500, 60000);
        this.kickOnFailure = file.getBoolean("behaviour.kick-on-failure", true);
        this.saveIntervalTicks = clamp(file.getLong("behaviour.save-interval-ticks", 6000L), 600L, 720000L);
        this.historyPageSize = clamp(file.getInt("behaviour.history-page-size", 5), 1, 25);
        this.historyRetention = clamp(file.getInt("behaviour.history-retention-days", 90), 1, 3650);
        this.profileCollection = name(file.getString("collections.profiles", "profiles"));
        this.matchCollection = name(file.getString("collections.matches", "matches"));
        this.kitCollection = name(file.getString("collections.kits", "kits"));
        this.tournamentCollection = name(file.getString("collections.tournaments", "tournaments"));
        this.banCollection = name(file.getString("collections.bans", "bans"));
        this.storeKits = file.getBoolean("behaviour.store-kits-in-database", false);
    }

    public static DatabaseSettings load(ConfigFile file) {
        return new DatabaseSettings(file);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String name(String value) {
        String trimmed = trim(value);
        return trimmed.isEmpty() ? "lightpractice" : trimmed;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    /** Builds the connection string used by the driver, honouring an explicit URI when supplied. */
    public String connectionString() {
        if (!uri.isEmpty()) {
            return uri;
        }
        StringBuilder builder = new StringBuilder("mongodb://");
        if (!username.isEmpty()) {
            builder.append(encode(username)).append(':').append(encode(password)).append('@');
        }
        builder.append(host).append(':').append(port).append('/');
        builder.append("?retryWrites=true");
        builder.append("&maxPoolSize=").append(poolSize);
        builder.append("&connectTimeoutMS=").append(connectTimeoutMillis);
        builder.append("&serverSelectionTimeoutMS=").append(serverSelectionTimeoutMillis);
        if (!username.isEmpty() && !authenticationDatabase.isEmpty()) {
            builder.append("&authSource=").append(encode(authenticationDatabase));
        }
        if (ssl) {
            builder.append("&ssl=true");
        }
        return builder.toString();
    }

    private static String encode(String value) {
        try {
            return java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20");
        } catch (java.io.UnsupportedEncodingException exception) {
            return value;
        }
    }

    /** Connection string with the password masked, safe to print in logs. */
    public String maskedConnectionString() {
        String value = connectionString();
        int scheme = value.indexOf("://");
        int at = value.indexOf('@');
        if (scheme < 0 || at < 0 || at < scheme) {
            return value;
        }
        return value.substring(0, scheme + 3) + "***:***@" + value.substring(at + 1);
    }

    public String host() {
        return host;
    }

    public int port() {
        return port;
    }

    public String database() {
        return database;
    }

    public boolean usesUri() {
        return !uri.isEmpty();
    }

    public int threads() {
        return threads;
    }

    public boolean kickOnFailure() {
        return kickOnFailure;
    }

    public long saveIntervalTicks() {
        return saveIntervalTicks;
    }

    public int historyPageSize() {
        return historyPageSize;
    }

    public int historyRetention() {
        return historyRetention;
    }

    public String profileCollection() {
        return profileCollection;
    }

    public String matchCollection() {
        return matchCollection;
    }

    public String kitCollection() {
        return kitCollection;
    }

    public String tournamentCollection() {
        return tournamentCollection;
    }

    public String banCollection() {
        return banCollection;
    }

    public boolean storeKits() {
        return storeKits;
    }

    /** True when two settings objects would connect to the same place. */
    public boolean sameTarget(DatabaseSettings other) {
        if (other == null) {
            return false;
        }
        return connectionString().equals(other.connectionString()) && database.equals(other.database);
    }
}
