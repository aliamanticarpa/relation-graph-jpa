package io.github.aliamanticarpa.relationgraph.jpa;

import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.boot.model.FunctionContributor;
import org.hibernate.dialect.Dialect;
import org.hibernate.query.ReturnableType;
import org.hibernate.query.sqm.function.AbstractSqmSelfRenderingFunctionDescriptor;
import org.hibernate.query.sqm.function.FunctionKind;
import org.hibernate.query.sqm.produce.function.StandardArgumentsValidators;
import org.hibernate.query.sqm.produce.function.StandardFunctionReturnTypeResolvers;
import org.hibernate.sql.ast.SqlAstTranslator;
import org.hibernate.sql.ast.spi.SqlAppender;
import org.hibernate.sql.ast.tree.SqlAstNode;
import org.hibernate.sql.ast.tree.expression.QueryLiteral;
import org.hibernate.type.StandardBasicTypes;

import java.util.List;

public class RelationFunctionContributor implements FunctionContributor {

    @Override
    public void contributeFunctions(FunctionContributions functionContributions) {
        var booleanType = functionContributions.getTypeConfiguration()
                .getBasicTypeRegistry()
                .resolve(StandardBasicTypes.BOOLEAN);

        Dialect dialect = functionContributions.getDialect();

        functionContributions.getFunctionRegistry().register(
                "relation_link_exists",
                new AbstractSqmSelfRenderingFunctionDescriptor(
                        "relation_link_exists",
                        FunctionKind.NORMAL,
                        StandardArgumentsValidators.exactly(5),
                        StandardFunctionReturnTypeResolvers.invariant(booleanType),
                        null
                ) {
                    @Override
                    public void render(
                            SqlAppender sqlAppender,
                            List<? extends SqlAstNode> args,
                            ReturnableType<?> returnType,
                            SqlAstTranslator<?> translator
                    ) {
                        renderSql(sqlAppender, args, dialect, translator);
                    }

                    @Override
                    @SuppressWarnings("removal")
                    public void render(
                            SqlAppender sqlAppender,
                            List<? extends SqlAstNode> args,
                            SqlAstTranslator<?> translator
                    ) {
                        renderSql(sqlAppender, args, dialect, translator);
                    }
                }
        );
    }

    private static void renderSql(
            SqlAppender sqlAppender,
            List<? extends SqlAstNode> args,
            Dialect dialect,
            SqlAstTranslator<?> translator
    ) {
        sqlAppender.appendSql("exists (select 1 from ");
        appendQuotedIdentifier(sqlAppender, args.get(0), dialect, translator);
        sqlAppender.appendSql(" where ");
        appendQuotedIdentifier(sqlAppender, args.get(1), dialect, translator);
        sqlAppender.appendSql(" = ");
        args.get(2).accept(translator);
        sqlAppender.appendSql(" and ");
        appendQuotedIdentifier(sqlAppender, args.get(3), dialect, translator);
        sqlAppender.appendSql(" = ");
        args.get(4).accept(translator);
        sqlAppender.appendSql(")");
    }

    private static void appendQuotedIdentifier(
            SqlAppender appender,
            SqlAstNode node,
            Dialect dialect,
            SqlAstTranslator<?> translator
    ) {
        if (node instanceof QueryLiteral<?> literal && literal.getLiteralValue() instanceof String name) {
            appender.appendSql(dialect.openQuote());
            appender.appendSql(name);
            appender.appendSql(dialect.closeQuote());
        } else {
            node.accept(translator);
        }
    }
}
