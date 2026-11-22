package io.github.aliamanticarpa.relationgraph.core.relations;

public enum RelationType {
    ONE_TO_ONE,
    ONE_TO_MANY,
    MANY_TO_ONE, // Добавили для корректного разворота 1:N
    MANY_TO_MANY;

    public RelationType reversed() {
        return switch (this) {
            case ONE_TO_MANY -> MANY_TO_ONE;
            case MANY_TO_ONE -> ONE_TO_MANY;
            default -> this;
        };
    }
}
