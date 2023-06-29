package org.openrewrite.analysis.trait.expr;

import fj.data.Validation;
import lombok.AllArgsConstructor;
import org.openrewrite.Cursor;
import org.openrewrite.analysis.InvocationMatcher;
import org.openrewrite.analysis.trait.TraitFactory;
import org.openrewrite.analysis.trait.util.TraitErrors;
import org.openrewrite.java.tree.J;

import java.util.UUID;

public interface ClassInstanceExpr extends ConstructorCall, Expr {
    enum Factory implements TraitFactory<ClassInstanceExpr> {
        F;

        @Override
        public Validation<TraitErrors, ClassInstanceExpr> viewOf(Cursor cursor) {
            if (cursor.getValue() instanceof J.NewClass) {
                return ClassInstanceExprBase.viewOf(cursor).map(m -> m);
            }
            return TraitErrors.invalidTraitCreationType(ClassInstanceExpr.class, cursor, J.NewClass.class);
        }
    }

    static Validation<TraitErrors, ClassInstanceExpr> viewOf(Cursor cursor) {
        return ClassInstanceExpr.Factory.F.viewOf(cursor);
    }
}

@AllArgsConstructor
class ClassInstanceExprBase implements ClassInstanceExpr {
    private final Cursor cursor;
    private final J.NewClass newClass;

    public boolean matches(InvocationMatcher callMatcher) {
        return callMatcher.matches(newClass);
    }

    static Validation<TraitErrors, ClassInstanceExprBase> viewOf(Cursor cursor) {
        return Validation.success(new ClassInstanceExprBase(cursor, cursor.getValue()));
    }

    @Override
    public UUID getId() {
        return newClass.getId();
    }
}
