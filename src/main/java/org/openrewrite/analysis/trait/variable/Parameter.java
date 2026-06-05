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
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import org.openrewrite.Cursor;
import org.openrewrite.analysis.trait.Top;
import org.openrewrite.analysis.trait.TraitFactory;
import org.openrewrite.analysis.trait.expr.Expr;
import org.openrewrite.analysis.trait.expr.VarAccess;
import org.openrewrite.analysis.trait.member.Method;
import org.openrewrite.analysis.trait.util.TraitErrors;
import org.openrewrite.analysis.util.FlagUtil;
import org.openrewrite.java.tree.Flag;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.util.Collection;
import java.util.UUID;

import static java.util.Collections.emptySet;

/**
 * Represents a parameter in a {@link org.openrewrite.java.tree.J.MethodDeclaration} or
 * {@link org.openrewrite.java.tree.J.NewClass}.
 */
public interface Parameter extends LocalScopeVariable {

    @Override
    Method getCallable();

    /**
     * The zero indexed position of the parameter in the method declaration.
     */
    int getPosition();

    boolean isVarArgs();

    enum Factory implements TraitFactory<Parameter> {
        F;
        @Override
        public Validation<TraitErrors, Parameter> viewOf(Cursor c) {
            if (c.getValue() instanceof J.VariableDeclarations.NamedVariable) {
                Cursor variableDeclarationsCursor = c.getParentTreeCursor();
                Cursor maybeCallableCursor = variableDeclarationsCursor.getParentTreeCursor();
                // A lambda parameter is declared on the lambda's implicit method (its single
                // abstract method), not on the lambda itself. The cursor path for such a parameter
                // is NamedVariable -> VariableDeclarations -> J.Lambda.Parameters -> J.Lambda.
                if (maybeCallableCursor.getValue() instanceof J.Lambda.Parameters) {
                    maybeCallableCursor = maybeCallableCursor.getParentTreeCursor();
                }
                Cursor callableCursor = maybeCallableCursor;
                return Method.viewOf(callableCursor).map(callable -> new ParameterBase(
                        c,
                        c.getValue(),
                        variableDeclarationsCursor.getValue(),
                        callable,
                        callableCursor
                ));
            }
            return TraitErrors.invalidTraitCreationType(Parameter.class, c, J.VariableDeclarations.NamedVariable.class);
        }
    }

    static Validation<TraitErrors, Parameter> viewOf(Cursor c) {
        return Factory.F.viewOf(c);
    }
}

@AllArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
class ParameterBase extends Top.Base implements Parameter {
    Cursor cursor;
    J.VariableDeclarations.NamedVariable namedVariable;
    J.VariableDeclarations variableDeclarations;

    @Getter(onMethod_ =@Override)
    Method callable;

    /** The cursor of the enclosing callable, either a {@link J.MethodDeclaration} or a {@link J.Lambda}. */
    Cursor callableCursor;

    @Override
    public UUID getId() {
        return namedVariable.getId();
    }

    @Override
    public String getName() {
        return namedVariable.getSimpleName();
    }

    @Override
    public int getPosition() {
        return callable.getParameters().indexOf(this);
    }

    @Override
    public boolean isVarArgs() {
        if (this.namedVariable.getVariableType() == null) {
            // Lambda parameters may have no resolved variable type; they can never be varargs.
            return false;
        }
        return this.namedVariable.getVariableType().hasFlags(Flag.Varargs);
    }

    @Override
    public Option<JavaType> getType() {
        return Option.fromNull(namedVariable.getType());
    }

    @Override
    public Collection<VarAccess> getVarAccesses() {
        return bodyScope()
                .map(scope -> VarAccess.findAllInScope(scope, this))
                .orSome(emptySet());
    }

    @Override
    public Collection<Expr> getAssignedValues() {
        return bodyScope()
                .map(scope -> VariableUtil.findAssignedValues(scope, this))
                .orSome(emptySet());
    }

    @Override
    public Collection<Flag> getFlags() {
        return FlagUtil.fromModifiers(variableDeclarations.getModifiers());
    }

    /**
     * The cursor pointing at the body in which this parameter is in scope: a method body or a
     * lambda body. Empty for abstract methods, which have no body.
     */
    private Option<Cursor> bodyScope() {
        Object callableTree = callableCursor.getValue();
        if (callableTree instanceof J.MethodDeclaration) {
            J.Block body = ((J.MethodDeclaration) callableTree).getBody();
            return body == null ? Option.none() : Option.some(new Cursor(callableCursor, body));
        }
        if (callableTree instanceof J.Lambda) {
            return Option.some(new Cursor(callableCursor, ((J.Lambda) callableTree).getBody()));
        }
        return Option.none();
    }
}
