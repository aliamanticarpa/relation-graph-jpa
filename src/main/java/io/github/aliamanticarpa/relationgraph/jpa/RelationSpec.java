package io.github.aliamanticarpa.relationgraph.jpa;

import io.github.aliamanticarpa.relationgraph.core.relations.JoinStep;
import io.github.aliamanticarpa.relationgraph.core.relations.TableRelation;
import io.github.aliamanticarpa.relationgraph.core.utils.NamingUtils;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class RelationSpec<S, T> {

    private static final Pattern SAFE_SQL_IDENTIFIER = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");
    private static final Map<FieldCacheKey, String> FIELD_CACHE = new ConcurrentHashMap<>();

    private record FieldCacheKey(Class<?> entityClass, String sqlColumnName) {}

    private final TableRelation<S, T> relation;

    private RelationSpec(TableRelation<S, T> relation) {
        this.relation = Objects.requireNonNull(relation, "Relation must not be null");
    }

    public static <S, T> RelationSpec<S, T> of(TableRelation<S, T> relation) {
        return new RelationSpec<>(relation);
    }

    /**
     * Builds a Specification checking that at least one related target entity exists.
     */
    public Specification<S> exists() {
        return whereTarget(null);
    }

    /**
     * Shortcut for filtering target entities by field equality (e.g., whereTargetEq("id", 42L)).
     */
    public Specification<S> whereTargetEq(String targetFieldName, Object value) {
        Objects.requireNonNull(targetFieldName, "Target field name must not be null");
        return whereTarget((target, query, cb) ->
                value == null ? cb.isNull(target.get(targetFieldName)) : cb.equal(target.get(targetFieldName), value)
        );
    }

    /**
     * Shortcut for filtering target entities by an IN clause (e.g., whereTargetIn("id", List.of(42L, 99L))).
     */
    public Specification<S> whereTargetIn(String targetFieldName, Collection<?> values) {
        Objects.requireNonNull(targetFieldName, "Target field name must not be null");
        if (values == null || values.isEmpty()) {
            return (root, query, cb) -> cb.disjunction();
        }
        return whereTarget((target, query, cb) -> target.get(targetFieldName).in(values));
    }

    /**
     * Builds a Specification with a custom JPA Criteria predicate applied to the target entity.
     */
    public Specification<S> whereTarget(TargetPredicate<T> targetFilter) {
        return (Root<S> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
            List<List<JoinStep>> branches = relation.branches();
            if (branches.isEmpty()) {
                return cb.disjunction();
            }

            List<Predicate> branchPredicates = new ArrayList<>(branches.size());

            for (List<JoinStep> branch : branches) {
                Subquery<Integer> subquery = query.subquery(Integer.class);
                subquery.select(cb.literal(1));

                From<?, ?> currentFrom = root;
                List<Predicate> stepConditions = new ArrayList<>();

                for (JoinStep step : branch) {
                    Root<?> targetRoot = subquery.from(step.target());

                    if (step.usesJoinTable()) {
                        String sourceJavaField = resolveJavaFieldName(step.source(), step.sourcePk());
                        String targetJavaField = resolveJavaFieldName(step.target(), step.targetPk());

                        String joinTable = validateIdentifier(step.relationTable());
                        String linkSourceCol = validateIdentifier(step.sourceColumn());
                        String linkTargetCol = validateIdentifier(step.targetColumn());

                        Expression<Boolean> linkExists = cb.function(
                                "relation_link_exists",
                                Boolean.class,
                                cb.literal(joinTable),
                                cb.literal(linkSourceCol),
                                currentFrom.get(sourceJavaField),
                                cb.literal(linkTargetCol),
                                targetRoot.get(targetJavaField)
                        );
                        stepConditions.add(cb.isTrue(linkExists));
                    } else {
                        String sourceJavaField = resolveJavaFieldName(step.source(), step.sourceColumn());
                        String targetJavaField = resolveJavaFieldName(step.target(), step.targetColumn());

                        stepConditions.add(cb.equal(currentFrom.get(sourceJavaField), targetRoot.get(targetJavaField)));
                    }

                    currentFrom = targetRoot;
                }

                if (targetFilter != null) {
                    @SuppressWarnings("unchecked")
                    From<?, T> finalTarget = (From<?, T>) currentFrom;
                    Predicate customPredicate = targetFilter.toPredicate(finalTarget, query, cb);
                    if (customPredicate != null) {
                        stepConditions.add(customPredicate);
                    }
                }

                subquery.where(cb.and(stepConditions.toArray(new Predicate[0])));
                branchPredicates.add(cb.exists(subquery));
            }

            return branchPredicates.size() == 1
                    ? branchPredicates.get(0)
                    : cb.or(branchPredicates.toArray(new Predicate[0]));
        };
    }

    private static String validateIdentifier(String identifier) {
        if (identifier == null || !SAFE_SQL_IDENTIFIER.matcher(identifier).matches()) {
            throw new IllegalArgumentException("Invalid or unsafe SQL identifier: '" + identifier + "'");
        }
        return identifier;
    }

    private static String resolveJavaFieldName(Class<?> entityClass, String sqlColumnName) {
        return FIELD_CACHE.computeIfAbsent(new FieldCacheKey(entityClass, sqlColumnName), key -> {
            Class<?> current = key.entityClass();
            String colName = key.sqlColumnName();

            while (current != null && current != Object.class) {
                for (Field field : current.getDeclaredFields()) {
                    Column col = field.getAnnotation(Column.class);
                    if (col != null && !col.name().isEmpty() && col.name().equalsIgnoreCase(colName)) {
                        return field.getName();
                    }
                    if (NamingUtils.toSnakeCase(field.getName()).equalsIgnoreCase(colName)) {
                        return field.getName();
                    }
                    if ("id".equalsIgnoreCase(colName) && field.isAnnotationPresent(Id.class)) {
                        return field.getName();
                    }
                }
                current = current.getSuperclass();
            }

            throw new IllegalArgumentException(
                    "No Java field found in entity " + key.entityClass().getName()
                            + " matching SQL column '" + colName + "'"
            );
        });
    }
}
