package io.github.aliamanticarpa.relationgraph.jpa;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Predicate;

@FunctionalInterface
public interface TargetPredicate<T> {
    Predicate toPredicate(From<?, T> targetRoot, CriteriaQuery<?> query, CriteriaBuilder cb);
}
