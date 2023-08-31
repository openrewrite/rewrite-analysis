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
import org.openrewrite.Cursor;
import org.openrewrite.analysis.trait.TraitFactory;
import org.openrewrite.analysis.trait.util.TraitErrors;

/**
 * A functional expression is either a lambda expression or a member reference expression.
 */
public interface FunctionalExpr extends ClassInstanceExpr {
    enum Factory implements TraitFactory<FunctionalExpr> {
        F;

        @Override
        public Validation<TraitErrors, FunctionalExpr> viewOf(Cursor cursor) {
            return TraitFactory.findFirstViewOf(
                    cursor,
                    LambdaExpr.Factory.F
            );
        }
    }

    static Validation<TraitErrors, FunctionalExpr> viewOf(Cursor cursor) {
        return FunctionalExpr.Factory.F.viewOf(cursor);
    }
}
