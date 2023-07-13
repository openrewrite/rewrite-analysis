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

import fj.data.Option;
import fj.data.Validation;
import lombok.AllArgsConstructor;
import org.openrewrite.Cursor;
import org.openrewrite.analysis.InvocationMatcher;
import org.openrewrite.analysis.trait.Top;
import org.openrewrite.analysis.trait.TraitFactory;
import org.openrewrite.analysis.trait.util.TraitErrors;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.util.UUID;

/**
 * A class instance creation expression.
 */
public interface ClassInstanceExpr extends ConstructorCall, Expr {
    enum Factory implements TraitFactory<ClassInstanceExpr> {
        F;

        @Override
        public Validation<TraitErrors, ClassInstanceExpr> viewOf(Cursor cursor) {
            if (cursor.getValue() instanceof J.NewClass) {
                return ClassInstanceExprBase.viewOf(cursor).map(m -> m);
            }
            return TraitErrors.invalidTraitCreationType(ClassInstanceExpr.class, cursor, J.NewClass.class);
        }
    }

    static Validation<TraitErrors, ClassInstanceExpr> viewOf(Cursor cursor) {
        return ClassInstanceExpr.Factory.F.viewOf(cursor);
    }
}

@AllArgsConstructor
class ClassInstanceExprBase extends Top.Base implements ClassInstanceExpr {
    private final Cursor cursor;
    private final J.NewClass newClass;

    public boolean matches(InvocationMatcher callMatcher) {
        return callMatcher.matches(newClass);
    }

    @Override
    public Option<JavaType.Method> getMethodType() {
        return Option.fromNull(newClass.getMethodType());
    }

    static Validation<TraitErrors, ClassInstanceExprBase> viewOf(Cursor cursor) {
        return Validation.success(new ClassInstanceExprBase(cursor, cursor.getValue()));
    }

    @Override
    public UUID getId() {
        return newClass.getId();
    }
}
