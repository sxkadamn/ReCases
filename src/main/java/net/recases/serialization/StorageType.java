package net.recases.serialization;

public enum StorageType {
    SQLITE,
    MYSQL;

    public static StorageType fromConfig(String value) {
        if (value == null) {
            return SQLITE;
        }

        for (StorageType type : values()) {
            if (type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }

        throw new IllegalArgumentException("Unsupported storage type: " + value);
    }
}

