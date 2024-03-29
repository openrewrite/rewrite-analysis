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
package org.openrewrite.analysis.trait.stmt;

import fj.data.Validation;
import org.openrewrite.Cursor;
import org.openrewrite.analysis.trait.Top;
import org.openrewrite.analysis.trait.TraitFactory;
import org.openrewrite.analysis.trait.member.Callable;
import org.openrewrite.analysis.trait.util.TraitErrors;

/**
 * A statement parent is any element that can have a statement as its child.
 */
public interface StmtParent extends Top {
    enum Factory implements TraitFactory<StmtParent> {
        F;

        @Override
        public Validation<TraitErrors, StmtParent> viewOf(Cursor cursor) {
            return TraitFactory.findFirstViewOf(
                    cursor,
                    Stmt.Factory.F,
                    Callable.Factory.F
            );
        }
    }

    static Validation<TraitErrors, StmtParent> viewOf(Cursor cursor) {
        return StmtParent.Factory.F.viewOf(cursor);
    }
}
