package io.github.aliamanticarpa.relationgraph.core.relations;

import java.util.ArrayList;
import java.util.List;

public final class RelationComposer {

    private RelationComposer() {}

    public static <A, B, C> TableRelation<A, C> sequence(TableRelation<A, B> first, TableRelation<B, C> second) {
        return new TableRelation<>() {
            @Override public Class<A> source() { return first.source(); }
            @Override public Class<C> target() { return second.target(); }

            @Override
            public List<List<JoinStep>> branches() {
                List<List<JoinStep>> result = new ArrayList<>();
                for (List<JoinStep> b1 : first.branches()) {
                    for (List<JoinStep> b2 : second.branches()) {
                        List<JoinStep> merged = new ArrayList<>(b1.size() + b2.size());
                        merged.addAll(b1);
                        merged.addAll(b2);
                        result.add(merged);
                    }
                }
                return result;
            }

            @Override
            public TableRelation<C, A> reverse() {
                return sequence(second.reverse(), first.reverse());
            }
        };
    }

    public static <S, T> TableRelation<S, T> parallel(TableRelation<S, T> first, TableRelation<S, T> second) {
        return new TableRelation<>() {
            @Override public Class<S> source() { return first.source(); }
            @Override public Class<T> target() { return second.target(); }

            @Override
            public List<List<JoinStep>> branches() {
                List<List<JoinStep>> all = new ArrayList<>(first.branches());
                all.addAll(second.branches());
                return all;
            }

            @Override
            public TableRelation<T, S> reverse() {
                return parallel(first.reverse(), second.reverse());
            }
        };
    }

    @SafeVarargs
    public static <S, T> TableRelation<S, T> anyOf(TableRelation<S, T>... relations) {
        if (relations == null || relations.length == 0) {
            throw new IllegalArgumentException("anyOf requires at least one relation");
        }
        TableRelation<S, T> result = relations[0];
        for (int i = 1; i < relations.length; i++) {
            result = parallel(result, relations[i]);
        }
        return result;
    }
}
