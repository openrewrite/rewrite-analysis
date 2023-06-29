package org.openrewrite.analysis.trait.expr;

import fj.data.Validation;
import lombok.AllArgsConstructor;
import org.openrewrite.Cursor;
import org.openrewrite.analysis.InvocationMatcher;
import org.openrewrite.analysis.trait.TraitFactory;
import org.openrewrite.analysis.trait.util.TraitErrors;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public interface MethodAccess extends Expr, Call {
    String getSimpleName();

    List<Expression> getArguments();

    boolean matches(InvocationMatcher callMatcher);

    boolean matches(MethodMatcher methodMatcher);

    enum Factory implements TraitFactory<MethodAccess> {
        F;
        @Override
        public Validation<TraitErrors, MethodAccess> viewOf(Cursor cursor) {
            if (cursor.getValue() instanceof J.MethodInvocation) {
                return MethodAccessBase.viewOf(cursor).map(m -> m);
            }
            return TraitErrors.invalidTraitCreationType(MethodAccess.class, cursor, J.MethodInvocation.class);
        }
    }

    static Validation<TraitErrors, MethodAccess> viewOf(Cursor cursor) {
        return MethodAccess.Factory.F.viewOf(cursor);
    }
}

@AllArgsConstructor
class MethodAccessBase implements MethodAccess {
    private final Cursor cursor;
    private final J.MethodInvocation methodInvocation;

    public String getSimpleName() {
        return methodInvocation.getSimpleName();
    }

    public List<Expression> getArguments() {
        return methodInvocation.getArguments();
    }

    public boolean matches(InvocationMatcher callMatcher) {
        return callMatcher.matches(methodInvocation);
    }

    public boolean matches(MethodMatcher methodMatcher) {
        return methodMatcher.matches(methodInvocation);
    }

    static Validation<TraitErrors, MethodAccessBase> viewOf(Cursor cursor) {
        Objects.requireNonNull(cursor, "cursor must not be null");
        return Validation.success(new MethodAccessBase(cursor, cursor.getValue()));
    }

    @Override
    public UUID getId() {
        return methodInvocation.getId();
    }
}
