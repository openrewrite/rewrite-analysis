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
import org.openrewrite.analysis.trait.TraitFactory;
import org.openrewrite.analysis.trait.util.TraitErrors;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.util.UUID;

public interface BinaryExpr extends Expr {
    J.Binary.Type getOperator();

    JavaType getType();

    Expr getLeft();

    Expr getRight();

    enum Factory implements TraitFactory<BinaryExpr> {
        F;
        @Override
        public Validation<TraitErrors, BinaryExpr> viewOf(Cursor cursor) {
            if (cursor.getValue() instanceof J.Binary) {
                return BinaryExprBase.viewOf(cursor).map(b -> b);
            }
            return TraitErrors.invalidTraitCreationType(Literal.class, cursor, J.Binary.class);
        }
    }

    static Validation<TraitErrors, BinaryExpr> viewOf(Cursor cursor) {
        return BinaryExpr.Factory.F.viewOf(cursor);
    }
}

@AllArgsConstructor
class BinaryExprBase implements BinaryExpr {
    private final Cursor cursor;
    private final J.Binary binary;

    public J.Binary.Type getOperator() {
        return binary.getOperator();
    }

    public JavaType getType() {
        return binary.getType();
    }

    public Expr getLeft() {
        return Expr.viewOf(new Cursor(cursor, binary.getLeft())).on(TraitErrors::doThrow);
    }

    public Expr getRight() {
        return Expr.viewOf(new Cursor(cursor, binary.getRight())).on(TraitErrors::doThrow);
    }

    static Validation<TraitErrors, BinaryExprBase> viewOf(Cursor cursor) {
        return Validation.success(new BinaryExprBase(cursor, cursor.getValue()));
    }

    @Override
    public UUID getId() {
        return binary.getId();
    }
}
