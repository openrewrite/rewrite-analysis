package org.openrewrite.analysis.trait.expr;

import fj.data.Validation;
import lombok.AllArgsConstructor;
import org.openrewrite.Cursor;
import org.openrewrite.analysis.trait.TraitFactory;
import org.openrewrite.analysis.trait.util.TraitErrors;
import org.openrewrite.java.tree.J;

import java.util.Optional;
import java.util.UUID;

public interface Literal extends Expr {
    Optional<Object> getValue();

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
class LiteralBase implements Literal {
    private final Cursor cursor;
    private final J.Literal literal;

    public Optional<Object> getValue() {
        return Optional.ofNullable(literal.getValue());
    }

    static Validation<TraitErrors, LiteralBase> viewOf(Cursor cursor) {
        return Validation.success(new LiteralBase(cursor, cursor.getValue()));
    }

    @Override
    public UUID getId() {
        return literal.getId();
    }
}
