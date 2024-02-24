/*
 * Copyright 2024 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.analysis.constantfold;

import fj.data.Option;
import lombok.AllArgsConstructor;
import org.openrewrite.Cursor;
import org.openrewrite.Incubating;
import org.openrewrite.Tree;
import org.openrewrite.analysis.dataflow.DataFlowNode;
import org.openrewrite.analysis.trait.Top;
import org.openrewrite.analysis.trait.expr.Expr;
import org.openrewrite.analysis.trait.expr.Literal;
import org.openrewrite.analysis.trait.expr.VarAccess;
import org.openrewrite.analysis.trait.variable.Variable;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.J;

import java.util.concurrent.atomic.AtomicReference;

@Incubating(since = "2.4.0")
public class ConstantFold {

    /**
     * Find the constant value that is being assigned to a variable, if any.
     * Otherwise, return the {@link J} itself.
     */
    public static Option<J> findConstantJ(Cursor cursor) {
        return findConstantExpr(cursor)
                .bind(expr -> {
                    TopFinderVisitor topFinder = new TopFinderVisitor(expr);
                    AtomicReference<J> found = new AtomicReference<>();
                    topFinder.visit(
                            cursor.dropParentUntil(J.CompilationUnit.class::isInstance).<Tree>getValue(),
                            found
                    );
                    return Option.some(found.get());
                });
    }

    @AllArgsConstructor
    private static final class TopFinderVisitor extends JavaVisitor<AtomicReference<J>> {
        private final Top top;

        @Override
        public J preVisit(J tree, AtomicReference<J> p) {
            if (top.getId().equals(tree.getId())) {
                stopAfterPreVisit();
                if (p.get() != null) {
                    throw new IllegalStateException("Multiple top-level trees found for " + top);
                }
                p.set(tree);
            }
            return super.preVisit(tree, p);
        }
    }

    /**
     * Find the constant expression that is being assigned to a variable, if any.
     * Otherwise, return the expression itself.
     */
    public static Option<Expr> findConstantExpr(Cursor cursor) {
        return DataFlowNode
                .of(cursor)
                .bind(ConstantFold::findConstantExpr);
    }

    /**
     * Find the constant expression that is being assigned to a variable, if any.
     * Otherwise, return the expression itself.
     */
    public static Option<Expr> findConstantExpr(DataFlowNode node) {
        return node
                // If the DataFlow node is a VarAccess
                .asExpr(VarAccess.class)
                // Get the variable that is being accessed
                .map(VarAccess::getVariable)
                // Get the assigned values to that variable
                .map(Variable::getAssignedValues)
                // If there is more than one assigned values,
                // we can't determine which one is the one we are looking for
                .filter(values -> values.size() == 1)
                // Get the single value
                .map(values -> values.iterator().next())
                // If the value is an expression other than a var access, just return it
                .orElse(() -> node.asExpr(Expr.class));
    }

    public static Option<Literal> findConstantLiteral(Cursor cursor) {
        return DataFlowNode
                .of(cursor)
                .bind(ConstantFold::findConstantLiteral);
    }

    public static Option<Literal> findConstantLiteral(DataFlowNode node) {
        return findConstantExpr(node)
                .filter(Literal.class::isInstance)
                .map(Literal.class::cast);
    }

    public static <T> Option<T> findConstantLiteralValue(Cursor cursor, Class<T> type) {
        validateTypeIsPrimitiveType(type);
        return DataFlowNode
                .of(cursor)
                .bind(n -> findConstantLiteralValue(n, type));
    }

    public static <T> Option<T> findConstantLiteralValue(DataFlowNode node, Class<T> type) {
        validateTypeIsPrimitiveType(type);
        return findConstantLiteral(node)
                .bind(Literal::getValue)
                .filter(type::isInstance)
                .map(type::cast);
    }

    private static void validateTypeIsPrimitiveType(Class<?> type) {
        if (!type.isPrimitive() && type != String.class) {
            throw new IllegalArgumentException("Type must be a primitive or String type");
        }
    }
}
