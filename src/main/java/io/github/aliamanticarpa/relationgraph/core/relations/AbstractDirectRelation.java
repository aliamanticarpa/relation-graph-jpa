package io.github.aliamanticarpa.relationgraph.core.relations;

import io.github.aliamanticarpa.relationgraph.core.annotations.Relation;
import io.github.aliamanticarpa.relationgraph.core.utils.NamingUtils;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public abstract class AbstractDirectRelation<S, T> implements TableRelation<S, T> {
    private final Class<S> sourceClass;
    private final Class<T> targetClass;
    private final RelationType relationType;
    private final boolean usesJoinTable;
    private final String relationTableName;
    private final String sourceCol;
    private final String targetCol;
    private final String sourcePk;
    private final String targetPk;

    @SuppressWarnings("unchecked")
    protected AbstractDirectRelation() {
        Relation annotation = getClass().getAnnotation(Relation.class);
        if (annotation == null) {
            throw new IllegalStateException(
                    "Relation class " + getClass().getName() + " must be annotated with @Relation"
            );
        }

        Type superClass = getClass().getGenericSuperclass();
        while (!(superClass instanceof ParameterizedType)) {
            if (superClass instanceof Class<?> clazz) {
                superClass = clazz.getGenericSuperclass();
            } else {
                throw new IllegalStateException(
                        "Unable to resolve source and target entity types for " + getClass().getName()
                );
            }
        }

        ParameterizedType parameterizedType = (ParameterizedType) superClass;
        Type[] typeArguments = parameterizedType.getActualTypeArguments();

        this.sourceClass = (Class<S>) typeArguments[0];
        this.targetClass = (Class<T>) typeArguments[1];

        this.relationType = annotation.type();
        this.usesJoinTable = (this.relationType == RelationType.MANY_TO_MANY) || annotation.joinTable();
        this.sourcePk = annotation.sourcePk();
        this.targetPk = annotation.targetPk();

        String defaultSourceFk = NamingUtils.toSnakeCase(sourceClass.getSimpleName()) + "_id";
        String defaultTargetFk = NamingUtils.toSnakeCase(targetClass.getSimpleName()) + "_id";

        if (this.usesJoinTable) {
            this.relationTableName = annotation.name().isEmpty()
                    ? NamingUtils.toSnakeCase(getClass().getSimpleName())
                    : annotation.name();
            this.sourceCol = annotation.sourceColumn().isEmpty() ? defaultSourceFk : annotation.sourceColumn();
            this.targetCol = annotation.targetColumn().isEmpty() ? defaultTargetFk : annotation.targetColumn();
        } else {
            this.relationTableName = null;
            if (this.relationType == RelationType.MANY_TO_ONE) {
                this.sourceCol = annotation.sourceColumn().isEmpty() ? defaultTargetFk : annotation.sourceColumn();
                this.targetCol = annotation.targetColumn().isEmpty() ? this.targetPk : annotation.targetColumn();
            } else {
                this.sourceCol = annotation.sourceColumn().isEmpty() ? this.sourcePk : annotation.sourceColumn();
                this.targetCol = annotation.targetColumn().isEmpty() ? defaultSourceFk : annotation.targetColumn();
            }
        }
    }

    @Override public final Class<S> source() { return sourceClass; }
    @Override public final Class<T> target() { return targetClass; }
    public final RelationType type() { return relationType; }
    public final boolean usesJoinTable() { return usesJoinTable; }
    public final String tableName() { return relationTableName; }
    public final String linkSourceColumn() { return sourceCol; }
    public final String linkTargetColumn() { return targetCol; }

    @Override
    public List<List<JoinStep>> branches() {
        return List.of(List.of(new JoinStep(
                source(), target(), getClass(), tableName(), usesJoinTable, type(),
                linkSourceColumn(), linkTargetColumn(), sourcePk, targetPk
        )));
    }

    @Override
    public TableRelation<T, S> reverse() {
        TableRelation<S, T> current = this;
        return new TableRelation<>() {
            @Override public Class<T> source() { return current.target(); }
            @Override public Class<S> target() { return current.source(); }

            @Override
            public List<List<JoinStep>> branches() {
                List<List<JoinStep>> reversedBranches = new ArrayList<>();
                for (List<JoinStep> branch : current.branches()) {
                    List<JoinStep> reversedSteps = new ArrayList<>(branch.size());
                    for (int i = branch.size() - 1; i >= 0; i--) {
                        reversedSteps.add(branch.get(i).reversed());
                    }
                    reversedBranches.add(reversedSteps);
                }
                return reversedBranches;
            }

            @Override
            public TableRelation<S, T> reverse() {
                return current;
            }
        };
    }
}
