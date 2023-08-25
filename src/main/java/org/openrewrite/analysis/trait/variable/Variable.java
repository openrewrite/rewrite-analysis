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

import fj.data.Option;
import fj.data.Validation;
import org.openrewrite.Cursor;
import org.openrewrite.analysis.trait.Element;
import org.openrewrite.analysis.trait.TraitFactory;
import org.openrewrite.analysis.trait.expr.Expr;
import org.openrewrite.analysis.trait.expr.VarAccess;
import org.openrewrite.analysis.trait.util.TraitErrors;
import org.openrewrite.java.tree.Flag;
import org.openrewrite.java.tree.JavaType;

import java.util.Collection;

/**
 * A variable is a field, a local variable or a parameter.
 */
public interface Variable extends Element {

    Option<JavaType> getType();

    /**
     * Gets all access to this variable.
     */
    Collection<VarAccess> getVarAccesses();

    Collection<Expr> getAssignedValues();

    Collection<Flag> getFlags();

    enum Factory implements TraitFactory<Variable> {
        F;

        @Override
        public Validation<TraitErrors, Variable> viewOf(Cursor cursor) {
            return TraitFactory.findFirstViewOf(
                    cursor,
                    LocalScopeVariable.Factory.F,
                    Field.Factory.F
            );
        }
    }

    static Validation<TraitErrors, Variable> viewOf(Cursor cursor) {
        return Factory.F.viewOf(cursor);
    }

}
