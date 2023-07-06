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
import org.openrewrite.analysis.trait.Top;
import org.openrewrite.analysis.trait.TraitFactory;
import org.openrewrite.analysis.trait.util.TraitErrors;
import org.openrewrite.java.tree.J;

import java.util.UUID;

/**
 * A common super-class to represent constant literals.
 */
public interface Literal extends Expr {
    Option<Object> getValue();

    enum Factory implements TraitFactory<Literal> {
        F;
        @Override
        public Validation<TraitErrors, Literal> viewOf(Cursor cursor) {
            if (cursor.getValue() instanceof J.Literal) {
                return LiteralBase.viewOf(cursor).map(l -> l);
            }
            return TraitErrors.invalidTraitCreationType(Literal.class, cursor, J.Literal.class);
        }
    }

    static Validation<TraitErrors, Literal> viewOf(Cursor cursor) {
        return Literal.Factory.F.viewOf(cursor);
    }
}

@AllArgsConstructor
class LiteralBase extends Top.Base implements Literal {
    private final Cursor cursor;
    private final J.Literal literal;

    public Option<Object> getValue() {
        return Option.fromNull(literal.getValue());
    }

    static Validation<TraitErrors, LiteralBase> viewOf(Cursor cursor) {
        return Validation.success(new LiteralBase(cursor, cursor.getValue()));
    }

    @Override
    public UUID getId() {
        return literal.getId();
    }
}
