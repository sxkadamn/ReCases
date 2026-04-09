package net.recases.api.condition;

@FunctionalInterface
public interface ConditionHandler {

    boolean test(ConditionContext context, String[] arguments);
}
