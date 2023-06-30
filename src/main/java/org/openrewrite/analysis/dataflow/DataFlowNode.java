/*
 * Copyright 2023 the original author or authors.
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
package org.openrewrite.analysis.dataflow;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.openrewrite.Cursor;
import org.openrewrite.analysis.trait.expr.Expr;
import org.openrewrite.analysis.trait.expr.ExprParent;
import org.openrewrite.analysis.trait.util.TraitErrors;
import org.openrewrite.analysis.trait.variable.Parameter;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.Optional;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

@AllArgsConstructor(access = AccessLevel.PACKAGE)
public abstract class DataFlowNode {
    @Getter
    final Cursor cursor;

    public abstract Optional<Expr> asExpr();

    public <E extends Expr> Optional<E> asExpr(Class<E> clazz) {
        return asExpr().filter(clazz::isInstance).map(clazz::cast);
    }

    public <E extends ExprParent> Optional<E> asExprParent(Class<E> clazz) {
        return asExpr().filter(clazz::isInstance).map(clazz::cast);
    }

    abstract Optional<Parameter> asParameter();

    abstract <T> T map(Function<Expr, T> whenExpression, Function<Parameter, T> whenParameter);

    public static DataFlowNode of(Cursor cursor) {
        if (cursor.getValue() instanceof Expression) {
            return new ExpressionDataFlowNode(cursor, Expr.viewOf(cursor).on(TraitErrors::doThrow));
        } else if (cursor.getValue() instanceof J.VariableDeclarations.NamedVariable) {
            return new ParameterDataFlowNode(cursor, Parameter.viewOf(cursor).on(TraitErrors::doThrow));
        } else {
            throw new IllegalArgumentException("DataFlowNode can not be of type: " + cursor.getValue().getClass());
        }
    }
}

class ExpressionDataFlowNode extends DataFlowNode {
    private final Expr expression;
    ExpressionDataFlowNode(Cursor cursor, Expr expression) {
        super(cursor);
        this.expression = requireNonNull(expression, "expression");
    }

    @Override
    public Optional<Expr> asExpr() {
        return Optional.of(expression);
    }

    @Override
    Optional<Parameter> asParameter() {
        return Optional.empty();
    }

    @Override
    <T> T map(Function<Expr, T> whenExpression, Function<Parameter, T> whenParameter) {
        requireNonNull(whenExpression, "whenExpression");
        requireNonNull(whenParameter, "whenParameter");
        return whenExpression.apply(expression);
    }
}

class ParameterDataFlowNode extends DataFlowNode {
    private final Parameter parameter;
    ParameterDataFlowNode(Cursor cursor, Parameter parameter) {
        super(cursor);
        this.parameter = requireNonNull(parameter, "parameter");
    }

    @Override
    public Optional<Expr> asExpr() {
        return Optional.empty();
    }

    @Override
    Optional<Parameter> asParameter() {
        return Optional.of(parameter);
    }

    @Override
    <T> T map(Function<Expr, T> whenExpression, Function<Parameter, T> whenParameter) {
        requireNonNull(whenExpression, "whenExpression");
        requireNonNull(whenParameter, "whenParameter");
        return whenParameter.apply(parameter);
    }
}
