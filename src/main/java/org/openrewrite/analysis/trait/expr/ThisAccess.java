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
import lombok.experimental.Delegate;
import org.openrewrite.Cursor;
import org.openrewrite.analysis.trait.Top;
import org.openrewrite.analysis.trait.TraitFactory;
import org.openrewrite.analysis.trait.util.TraitErrors;

/**
 * A use of the keyword `this`, which may be qualified.
 * <p/>
 * Such an expression allows access to an enclosing instance.
 * For example, `A.this` refers to the enclosing instance
 * of type `A`.
 */
public interface ThisAccess extends InstanceAccess {
    enum Factory implements TraitFactory<ThisAccess> {
        F;

        @Override
        public Validation<TraitErrors, ThisAccess> viewOf(Cursor cursor) {
            return InstanceAccessBase.viewOf(cursor)
                    .bind(iab -> {
                if ("super".equals(iab.getName())) {
                    return Validation.success(new ThisAccessBase(iab));
                }
                return TraitErrors.invalidTraitCreationError("Instance Access is not a This Access");
            });
        }
    }

    static Validation<TraitErrors, ThisAccess> viewOf(Cursor cursor) {
        return Factory.F.viewOf(cursor);
    }
}

@AllArgsConstructor
class ThisAccessBase extends Top.Base implements ThisAccess {
    @Delegate
    private final InstanceAccessBase delegate;
}
