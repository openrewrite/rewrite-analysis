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

import fj.data.Option;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.openrewrite.Cursor;
import org.openrewrite.analysis.trait.expr.Expr;
import org.openrewrite.analysis.trait.expr.ExprParent;
import org.openrewrite.analysis.trait.variable.Parameter;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.function.Function;

import static java.util.Objects.requireNonNull;

@AllArgsConstructor(access = AccessLevel.PACKAGE)
public abstract class DataFlowNode {
    @Getter
    final Cursor cursor;

    public abstract Option<Expr> asExpr();

    public <E extends Expr> Option<E> asExpr(Class<E> clazz) {
        return asExpr().filter(clazz::isInstance).map(clazz::cast);
    }

    public <E extends ExprParent> Option<E> asExprParent(Class<E> clazz) {
        return asExpr().filter(clazz::isInstance).map(clazz::cast);
    }

    public abstract Option<Parameter> asParameter();

    abstract <T> T map(Function<Expr, T> whenExpression, Function<Parameter, T> whenParameter);

    public static Option<DataFlowNode> of(Cursor cursor) {
        if (cursor.getValue() instanceof Expression) {
            return Expr.viewOf(cursor).map(expr -> (DataFlowNode) new ExpressionDataFlowNode(cursor, expr)).toOption();
        } else if (cursor.getValue() instanceof J.VariableDeclarations.NamedVariable) {
            return Parameter.viewOf(cursor).map(parameter -> (DataFlowNode) new ParameterDataFlowNode(cursor, parameter)).toOption();
        } else {
            return Option.none();
        }
    }

    public static DataFlowNode ofOrThrow(Cursor cursor, String message) {
        Option<DataFlowNode> maybeNode = of(cursor);
        if (maybeNode.isNone()) {
            throw new RuntimeException(message);
        }
        return maybeNode.some();
    }
}

class ExpressionDataFlowNode extends DataFlowNode {
    private final Expr expression;
    ExpressionDataFlowNode(Cursor cursor, Expr expression) {
        super(cursor);
        this.expression = requireNonNull(expression, "expression");
    }

    @Override
    public Option<Expr> asExpr() {
        return Option.some(expression);
    }

    @Override
    public Option<Parameter> asParameter() {
        return Option.none();
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
    public Option<Expr> asExpr() {
        return Option.none();
    }

    @Override
    public Option<Parameter> asParameter() {
        return Option.some(parameter);
    }

    @Override
    <T> T map(Function<Expr, T> whenExpression, Function<Parameter, T> whenParameter) {
        requireNonNull(whenExpression, "whenExpression");
        requireNonNull(whenParameter, "whenParameter");
        return whenParameter.apply(parameter);
    }
}
