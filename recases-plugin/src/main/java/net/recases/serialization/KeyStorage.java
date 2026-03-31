package net.recases.serialization;

import java.io.Closeable;

public interface KeyStorage extends Closeable {

    void initialize();

    int getCaseAmount(PlayerKey playerKey, String caseName);

    void setCaseAmount(PlayerKey playerKey, String caseName, int amount);

    int changeCaseAmount(PlayerKey playerKey, String caseName, int delta);

    boolean isEmpty();

    @Override
    void close();
}

