package net.recases.serialization;

import java.io.File;

public class SqliteKeyStorage extends AbstractSqlKeyStorage {

    private final File databaseFile;

    public SqliteKeyStorage(File databaseFile) {
        this.databaseFile = databaseFile;
    }

    @Override
    protected String getJdbcUrl() {
        return "jdbc:sqlite:" + databaseFile.getAbsolutePath();
    }

    @Override
    protected String getUsername() {
        return null;
    }

    @Override
    protected String getPassword() {
        return null;
    }

    @Override
    protected String createTableSql() {
        return "CREATE TABLE IF NOT EXISTS recases_keys (" +
                "player_id TEXT NOT NULL," +
                "player_name TEXT NOT NULL," +
                "case_name TEXT NOT NULL," +
                "amount INTEGER NOT NULL," +
                "PRIMARY KEY (player_id, case_name)" +
                ")";
    }

    @Override
    protected String upsertSql() {
        return "INSERT INTO recases_keys (player_id, player_name, case_name, amount) VALUES (?, ?, ?, ?) " +
                "ON CONFLICT(player_id, case_name) DO UPDATE SET player_name = excluded.player_name, amount = excluded.amount";
    }

    @Override
    protected String changeSql() {
        return "INSERT INTO recases_keys (player_id, player_name, case_name, amount) VALUES (?, ?, ?, ?) "
                + "ON CONFLICT(player_id, case_name) DO UPDATE SET player_name = excluded.player_name, amount = MAX(0, recases_keys.amount + excluded.amount)";
    }

    @Override
    protected String rowCountSql() {
        return "SELECT COUNT(*) FROM recases_keys WHERE amount > 0";
    }
}

