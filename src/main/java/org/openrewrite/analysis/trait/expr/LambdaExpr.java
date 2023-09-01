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
 * Lambda expressions are represented by their implicit class instance creation expressions,
 * which instantiate an anonymous class that overrides the unique method designated by their
 * functional interface type. The parameters of the lambda expression correspond to the
 * parameters of the overriding method, and the lambda body corresponds to the body of the
 * overriding method (enclosed by a return statement and a block in the case of lambda
 * expressions whose body is an expression).
 */
public interface LambdaExpr extends FunctionalExpr {
    enum Factory implements TraitFactory<LambdaExpr> {
        F;

        @Override
        public Validation<TraitErrors, LambdaExpr> viewOf(Cursor cursor) {
            if (cursor.getValue() instanceof J.Lambda) {
                return LambdaExprBase.viewOf(cursor).map(m -> m);
            }
            return TraitErrors.invalidTraitCreationType(LambdaExpr.class, cursor, J.Lambda.class);
        }
    }

    static Validation<TraitErrors, LambdaExpr> viewOf(Cursor cursor) {
        return LambdaExpr.Factory.F.viewOf(cursor);
    }
}

@AllArgsConstructor
class LambdaExprBase extends Top.Base implements LambdaExpr {
    private final Cursor cursor;
    private final J.Lambda lambda;

    @Override
    public boolean matches(InvocationMatcher callMatcher) {
        return callMatcher.matches(lambda);
    }

    @Override
    public Option<JavaType.Method> getMethodType() {
        return Option.none();
    }

    static Validation<TraitErrors, LambdaExprBase> viewOf(Cursor cursor) {
        return Validation.success(new LambdaExprBase(cursor, cursor.getValue()));
    }

    @Override
    public UUID getId() {
        return lambda.getId();
    }
}
