package net.recases.serialization;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public abstract class AbstractSqlKeyStorage implements KeyStorage {

    protected abstract String getJdbcUrl();

    protected abstract String getUsername();

    protected abstract String getPassword();

    protected abstract String createTableSql();

    protected abstract String upsertSql();

    protected abstract String rowCountSql();

    protected Connection openConnection() throws SQLException {
        String username = getUsername();
        if (username == null || username.isEmpty()) {
            return DriverManager.getConnection(getJdbcUrl());
        }
        return DriverManager.getConnection(getJdbcUrl(), username, getPassword());
    }

    @Override
    public void initialize() {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(createTableSql())) {
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to initialize SQL storage", exception);
        }
    }

    @Override
    public int getCaseAmount(PlayerKey playerKey, String caseName) {
        String sql = "SELECT amount FROM recases_keys WHERE player_id = ? AND case_name = ?";
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerKey.getUniqueId().toString());
            statement.setString(2, normalizeCaseName(caseName));
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Math.max(0, resultSet.getInt("amount"));
                }
            }
            return 0;
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to read case amount", exception);
        }
    }

    @Override
    public void setCaseAmount(PlayerKey playerKey, String caseName, int amount) {
        int normalizedAmount = Math.max(0, amount);
        if (normalizedAmount == 0) {
            deleteCaseAmount(playerKey, caseName);
            return;
        }

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(upsertSql())) {
            statement.setString(1, playerKey.getUniqueId().toString());
            statement.setString(2, playerKey.getPlayerName());
            statement.setString(3, normalizeCaseName(caseName));
            statement.setInt(4, normalizedAmount);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to write case amount", exception);
        }
    }

    @Override
    public boolean isEmpty() {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(rowCountSql());
             ResultSet resultSet = statement.executeQuery()) {
            return !resultSet.next() || resultSet.getInt(1) == 0;
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to inspect SQL storage", exception);
        }
    }

    @Override
    public void close() {
    }

    private void deleteCaseAmount(PlayerKey playerKey, String caseName) {
        String sql = "DELETE FROM recases_keys WHERE player_id = ? AND case_name = ?";
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerKey.getUniqueId().toString());
            statement.setString(2, normalizeCaseName(caseName));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to delete case amount", exception);
        }
    }

    protected String normalizeCaseName(String caseName) {
        return caseName.toLowerCase();
    }
}

