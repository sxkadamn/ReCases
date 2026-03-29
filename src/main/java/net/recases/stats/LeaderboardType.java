package net.recases.stats;

public enum LeaderboardType {
    OPENS("opens"),
    RARE("rare"),
    GUARANTEED("guaranteed");

    private final String id;

    LeaderboardType(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public static LeaderboardType fromId(String value) {
        if (value == null) {
            return null;
        }

        for (LeaderboardType type : values()) {
            if (type.id.equalsIgnoreCase(value)) {
                return type;
            }
        }
        return null;
    }
}

