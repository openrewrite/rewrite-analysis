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
package org.openrewrite.analysis.trait.variable;

import fj.data.Validation;
import org.openrewrite.Cursor;
import org.openrewrite.analysis.trait.expr.Expr;
import org.openrewrite.analysis.trait.expr.VarAccess;
import org.openrewrite.analysis.trait.util.TraitErrors;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class VariableUtil {
    public static Collection<Expr> findAssignedValues(Cursor scope, Variable variable) {
        List<Expr> values = new ArrayList<>();
        new JavaVisitor<List<Expr>>() {
            @Override
            public J visitVariable(J.VariableDeclarations.NamedVariable var, List<Expr> exprs) {
                if (var.getInitializer() == null) {
                    return super.visitVariable(var, exprs);
                }
                Validation<TraitErrors, Variable> varDecl = Variable.viewOf(getCursor());
                Validation<TraitErrors, Expr> expr = Expr.viewOf(new Cursor(getCursor(), var.getInitializer()));
                if (varDecl.map(v -> v.equals(variable)).orSuccess(false)) {
                    exprs.add(expr.success());
                }
                return super.visitVariable(var, exprs);
            }

            @Override
            public J visitAssignment(J.Assignment assignment, List<Expr> exprs) {
                Validation<TraitErrors, VarAccess>[] varAccess = new Validation[]{VarAccess.viewOf(
                        new Cursor(getCursor(), Objects.requireNonNull(Expression.unwrap(assignment.getVariable())))
                )};
                Validation<TraitErrors, Expr> expr = Expr.viewOf(new Cursor(getCursor(), assignment.getAssignment()));

                if (!varAccess[0].isSuccess()) {
                    new JavaIsoVisitor<Integer>() {
                        @Override
                        public J.FieldAccess visitFieldAccess(J.FieldAccess fieldAccess, Integer x) {
                            varAccess[0] = VarAccess.viewOf(new Cursor(getCursor(), fieldAccess.getName()));
                            return fieldAccess;
                        }
                    }.visit(assignment.getVariable(), 0, getCursor());
                }

                if (varAccess[0].map(v -> v.getVariable().equals(variable)).orSuccess(false)) {
                    exprs.add(expr.success());
                }
                return super.visitAssignment(assignment, exprs);
            }
        }.visit(scope.getValue(), values, scope.getParentOrThrow());
        return values;
    }
}
