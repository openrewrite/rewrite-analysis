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
package org.openrewrite.analysis.trait.expr;

import fj.data.Validation;
import lombok.AllArgsConstructor;
import org.openrewrite.Cursor;
import org.openrewrite.analysis.trait.Top;
import org.openrewrite.analysis.trait.TraitFactory;
import org.openrewrite.analysis.trait.util.TraitErrors;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Javadoc;

import java.util.UUID;

/** A common super-class that represents all kinds of expressions. */
public interface Expr extends ExprParent {
    enum Factory implements TraitFactory<Expr> {
        F;

        @Override
        public Validation<TraitErrors, Expr> viewOf(Cursor cursor) {
            return TraitFactory.findFirstViewOf(
                    cursor,
                    InstanceAccess.Factory.F,
                    VarAccess.Factory.F,
                    Literal.Factory.F,
                    MethodAccess.Factory.F,
                    BinaryExpr.Factory.F,
                    ClassInstanceExpr.Factory.F,
                    c -> ExprFallback.viewOf(c).map(o -> o)
            );
        }
    }

    static Validation<TraitErrors, Expr> viewOf(Cursor cursor) {
        return Factory.F.viewOf(cursor);
    }
}

@AllArgsConstructor
class ExprFallback extends Top.Base implements Expr {
    Cursor cursor;
    Expression expression;

    @Override
    public UUID getId() {
        return expression.getId();
    }

    static Validation<TraitErrors, ExprFallback> viewOf(Cursor cursor) {
        if (cursor.getValue() instanceof J.Identifier && cursor.firstEnclosing(Javadoc.class) == null) {
            return TraitErrors.invalidTraitCreation(ExprFallback.class, "Identifiers are only Expr when they are VarAccess");
        }
        if (cursor.getValue() instanceof J.FieldAccess && cursor.firstEnclosing(J.Import.class) != null) {
            return TraitErrors.invalidTraitCreation(ExprFallback.class, "FieldAccess is in an import statement");
        }
        if (cursor.getValue() instanceof J.Primitive) {
            return TraitErrors.invalidTraitCreation(ExprFallback.class, "Primitives are only Expr when they are Literal");
        }
        if (cursor.getValue() instanceof Expression) {
            return Validation.success(new ExprFallback(cursor, cursor.getValue()));
        }
        return TraitErrors.invalidTraitCreationType(ExprFallback.class, cursor, Expression.class);
    }
}
