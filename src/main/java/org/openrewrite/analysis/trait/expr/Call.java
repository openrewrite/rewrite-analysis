package org.openrewrite.analysis.trait.expr;

import fj.data.Validation;
import org.openrewrite.Cursor;
import org.openrewrite.analysis.InvocationMatcher;
import org.openrewrite.analysis.trait.TraitFactory;
import org.openrewrite.analysis.trait.util.TraitErrors;

public interface Call extends ExprParent {
    boolean matches(InvocationMatcher callMatcher);

    enum Factory implements TraitFactory<Call> {
        F;
        @Override
        public Validation<TraitErrors, Call> viewOf(Cursor cursor) {
            // TODO: Missing method reference
            return TraitFactory.findFirstViewOf(
                    cursor,
                    MethodAccess.Factory.F,
                    ConstructorCall.Factory.F
            );
        }
    }

    static Validation<TraitErrors, Call> viewOf(Cursor cursor) {
        return Call.Factory.F.viewOf(cursor);
    }
}
