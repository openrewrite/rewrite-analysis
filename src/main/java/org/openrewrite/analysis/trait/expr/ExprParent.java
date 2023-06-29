package org.openrewrite.analysis.trait.expr;

import fj.data.Validation;
import org.openrewrite.Cursor;
import org.openrewrite.analysis.trait.Top;
import org.openrewrite.analysis.trait.TraitFactory;
import org.openrewrite.analysis.trait.util.TraitErrors;

public interface ExprParent extends Top {
    enum Factory implements TraitFactory<ExprParent> {
        F;

        @Override
        public Validation<TraitErrors, ExprParent> viewOf(Cursor cursor) {
            return TraitFactory.findFirstViewOf(
                    cursor,
                    Call.Factory.F
            );
        }
    }

    static Validation<TraitErrors, ExprParent> viewOf(Cursor cursor) {
        return ExprParent.Factory.F.viewOf(cursor);
    }
}
