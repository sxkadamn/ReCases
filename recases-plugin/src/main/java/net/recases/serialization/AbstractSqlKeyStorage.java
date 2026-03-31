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

    protected abstract String changeSql();

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
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(selectAmountSql())) {
            return getCaseAmount(statement, playerKey, caseName);
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
    public int changeCaseAmount(PlayerKey playerKey, String caseName, int delta) {
        String normalizedCaseName = normalizeCaseName(caseName);
        try (Connection connection = openConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(changeSql())) {
                statement.setString(1, playerKey.getUniqueId().toString());
                statement.setString(2, playerKey.getPlayerName());
                statement.setString(3, normalizedCaseName);
                statement.setInt(4, delta);
                statement.executeUpdate();
            }

            int updated;
            try (PreparedStatement statement = connection.prepareStatement(selectAmountSql())) {
                updated = getCaseAmount(statement, playerKey, normalizedCaseName);
            }
            if (updated <= 0) {
                deleteCaseAmount(connection, playerKey, normalizedCaseName);
                return 0;
            }
            return updated;
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to change case amount", exception);
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

    private int getCaseAmount(PreparedStatement statement, PlayerKey playerKey, String caseName) throws SQLException {
        statement.setString(1, playerKey.getUniqueId().toString());
        statement.setString(2, normalizeCaseName(caseName));
        try (ResultSet resultSet = statement.executeQuery()) {
            if (resultSet.next()) {
                return Math.max(0, resultSet.getInt("amount"));
            }
        }
        return 0;
    }

    private void deleteCaseAmount(PlayerKey playerKey, String caseName) {
        try (Connection connection = openConnection()) {
            deleteCaseAmount(connection, playerKey, caseName);
        } catch (SQLException exception) {
            throw new RuntimeException("Failed to delete case amount", exception);
        }
    }

    private void deleteCaseAmount(Connection connection, PlayerKey playerKey, String caseName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM recases_keys WHERE player_id = ? AND case_name = ?")) {
            statement.setString(1, playerKey.getUniqueId().toString());
            statement.setString(2, normalizeCaseName(caseName));
            statement.executeUpdate();
        }
    }

    private String selectAmountSql() {
        return "SELECT amount FROM recases_keys WHERE player_id = ? AND case_name = ?";
    }

    protected String normalizeCaseName(String caseName) {
        return caseName.toLowerCase();
    }
}
