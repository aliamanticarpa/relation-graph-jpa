package io.github.aliamanticarpa.relationgraph.core.relations;

import java.util.List;

public abstract class CompositeRelation<S, T> implements TableRelation<S, T> {

    private final TableRelation<S, T> delegate;

    protected CompositeRelation(TableRelation<S, T> delegate) {
        this.delegate = delegate;
    }

    @Override public Class<S> source() { return delegate.source(); }
    @Override public Class<T> target() { return delegate.target(); }
    @Override public List<List<JoinStep>> branches() { return delegate.branches(); }
    @Override public TableRelation<T, S> reverse() { return delegate.reverse(); }

    @SafeVarargs
    protected static <S, T> TableRelation<S, T> anyOf(TableRelation<S, T>... relations) {
        return RelationComposer.anyOf(relations);
    }
}
