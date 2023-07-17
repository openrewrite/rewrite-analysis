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
import lombok.AllArgsConstructor;
import org.openrewrite.Cursor;
import org.openrewrite.analysis.trait.Top;
import org.openrewrite.analysis.trait.TraitFactory;
import org.openrewrite.analysis.trait.util.TraitErrors;
import org.openrewrite.java.tree.J;

import java.util.UUID;

/**
 * A return statement.
 */
public interface ReturnStmt extends Stmt {
    enum Factory implements TraitFactory<ReturnStmt> {
        F;
        @Override
        public Validation<TraitErrors, ReturnStmt> viewOf(Cursor cursor) {
            if (cursor.getValue() instanceof J.Return) {
                return ReturnStmtBase.viewOf(cursor).map(b -> b);
            }
            return TraitErrors.invalidTraitCreationType(ReturnStmt.class, cursor, J.Return.class);
        }
    }

    static Validation<TraitErrors, ReturnStmt> viewOf(Cursor cursor) {
        return ReturnStmt.Factory.F.viewOf(cursor);
    }
}

@AllArgsConstructor
class ReturnStmtBase extends Top.Base implements ReturnStmt {
    private final Cursor cursor;
    private final J.Return returnStmt;

    static Validation<TraitErrors, ReturnStmtBase> viewOf(Cursor cursor) {
        return Validation.success(new ReturnStmtBase(cursor, cursor.getValue()));
    }

    @Override
    public UUID getId() {
        return returnStmt.getId();
    }
}
