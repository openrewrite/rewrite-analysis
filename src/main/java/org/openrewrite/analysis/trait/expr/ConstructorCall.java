package org.openrewrite.analysis.trait.expr;

import fj.data.Validation;
import org.openrewrite.Cursor;
import org.openrewrite.analysis.trait.TraitFactory;
import org.openrewrite.analysis.trait.util.TraitErrors;

public interface ConstructorCall extends Call {
    enum Factory implements TraitFactory<ConstructorCall> {
        F;

        @Override
        public Validation<TraitErrors, ConstructorCall> viewOf(Cursor cursor) {
            return TraitFactory.findFirstViewOf(
                    cursor,
                    ClassInstanceExpr.Factory.F
            );
        }
    }

    static Validation<TraitErrors, ConstructorCall> viewOf(Cursor cursor) {
        return ConstructorCall.Factory.F.viewOf(cursor);
    }
}
