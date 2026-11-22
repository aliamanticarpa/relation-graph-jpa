package io.github.aliamanticarpa.relationgraph.core.relations;

public record JoinStep(
        Class<?> source,
        Class<?> target,
        Class<?> relationClass,
        String relationTable,     // Имя промежуточной таблицы (null или игнорируется, если usesJoinTable == false)
        boolean usesJoinTable,    // true = через таблицу-посредник (M:N), false = прямой JOIN по FK (1:N, N:1, 1:1)
        RelationType type,
        String sourceColumn,      // Для usesJoinTable=true: колонка в relationTable; для false: колонка в source-таблице
        String targetColumn,      // Для usesJoinTable=true: колонка в relationTable; для false: колонка в target-таблице
        String sourcePk,          // PK в source-таблице (используется при usesJoinTable=true)
        String targetPk           // PK в target-таблице (используется при usesJoinTable=true)
) {
    // Разворачивает один шаг в обратную сторону
    public JoinStep reversed() {
        return new JoinStep(
                target,             // Бывшая цель стала источником
                source,             // Бывший источник стал целью
                relationClass,
                relationTable,
                usesJoinTable,
                type.reversed(),
                targetColumn,       // Меняем колонки местами
                sourceColumn,
                targetPk,           // Меняем PK местами
                sourcePk
        );
    }
}
