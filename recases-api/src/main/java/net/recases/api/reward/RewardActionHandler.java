package net.recases.api.reward;

public interface RewardActionHandler {

    void execute(RewardActionContext context, String[] arguments);

    default void rollback(RewardActionContext context, String[] arguments) {
    }
}
