package io.github.aliamanticarpa.relationgraph.core.annotations;

import io.github.aliamanticarpa.relationgraph.core.relations.RelationType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Relation {
    RelationType type();               // Тип связи (ONE_TO_ONE, ONE_TO_MANY, MANY_TO_ONE, MANY_TO_MANY)
    boolean joinTable() default false; // Использовать промежуточную таблицу (для MANY_TO_MANY всегда true)
    String name() default "";          // Имя промежуточной таблицы (по умолчанию: snake_case имени класса связи)
    String sourceColumn() default "";  // Колонка связи со стороны S (по умолчанию: <snake_case(S)>_id)
    String targetColumn() default "";  // Колонка связи со стороны T (по умолчанию: <snake_case(T)>_id)
    String sourcePk() default "id";    // Имя PK в таблице S (по умолчанию: "id")
    String targetPk() default "id";    // Имя PK в таблице T (по умолчанию: "id")
}
