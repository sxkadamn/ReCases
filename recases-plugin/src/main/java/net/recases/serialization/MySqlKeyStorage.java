package net.recases.serialization;

public class MySqlKeyStorage extends AbstractSqlKeyStorage {

    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private final boolean useSsl;

    public MySqlKeyStorage(String host, int port, String database, String username, String password, boolean useSsl) {
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
        this.useSsl = useSsl;
    }

    @Override
    protected String getJdbcUrl() {
        return "jdbc:mysql://" + host + ":" + port + "/" + database +
                "?useSSL=" + useSsl +
                "&characterEncoding=utf8" +
                "&serverTimezone=UTC" +
                "&autoReconnect=true";
    }

    @Override
    protected String getUsername() {
        return username;
    }

    @Override
    protected String getPassword() {
        return password;
    }

    @Override
    protected String createTableSql() {
        return "CREATE TABLE IF NOT EXISTS recases_keys (" +
                "player_id VARCHAR(36) NOT NULL," +
                "player_name VARCHAR(32) NOT NULL," +
                "case_name VARCHAR(64) NOT NULL," +
                "amount INT NOT NULL," +
                "PRIMARY KEY (player_id, case_name)" +
                ")";
    }

    @Override
    protected String upsertSql() {
        return "INSERT INTO recases_keys (player_id, player_name, case_name, amount) VALUES (?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE player_name = VALUES(player_name), amount = VALUES(amount)";
    }

    @Override
    protected String changeSql() {
        return "INSERT INTO recases_keys (player_id, player_name, case_name, amount) VALUES (?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE player_name = VALUES(player_name), amount = GREATEST(0, amount + VALUES(amount))";
    }

    @Override
    protected String rowCountSql() {
        return "SELECT COUNT(*) FROM recases_keys WHERE amount > 0";
    }
}

