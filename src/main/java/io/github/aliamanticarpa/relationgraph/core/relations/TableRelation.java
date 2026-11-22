package io.github.aliamanticarpa.relationgraph.core.relations;

import java.util.List;

public interface TableRelation<S, T> {
    Class<S> source();
    Class<T> target();
    List<List<JoinStep>> branches();
    TableRelation<T, S> reverse();

    // Следующий шаг цепочки: rel1.chain(rel2).chain(rel3)
    default <Next> TableRelation<S, Next> chain(TableRelation<T, Next> next) {
        return RelationComposer.sequence(this, next);
    }

    // Альтернативная ветка (OR): rel1.or(rel2)
    default TableRelation<S, T> or(TableRelation<S, T> alternative) {
        return RelationComposer.parallel(this, alternative);
    }
}
