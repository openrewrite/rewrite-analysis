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

import fj.data.Option;
import fj.data.Validation;
import lombok.AllArgsConstructor;
import org.openrewrite.Cursor;
import org.openrewrite.analysis.InvocationMatcher;
import org.openrewrite.analysis.trait.Top;
import org.openrewrite.analysis.trait.TraitFactory;
import org.openrewrite.analysis.trait.util.TraitErrors;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A method access is an invocation of a method with a list of arguments.
 */
public interface MethodAccess extends Expr, Call {
    String getSimpleName();

    List<Expr> getArguments();

    @Override
    boolean matches(InvocationMatcher callMatcher);

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
class MethodAccessBase extends Top.Base implements MethodAccess {
    private final Cursor cursor;
    private final J.MethodInvocation methodInvocation;

    @Override
    public String getSimpleName() {
        return methodInvocation.getSimpleName();
    }

    @Override
    public List<Expr> getArguments() {
        return methodInvocation
                .getArguments()
                .stream()
                .map(e -> Expr.viewOf(new Cursor(cursor, e)).on(TraitErrors::doThrow))
                .collect(Collectors.toList());
    }

    @Override
    public boolean matches(InvocationMatcher callMatcher) {
        return callMatcher.matches(methodInvocation);
    }

    @Override
    public Option<JavaType.Method> getMethodType() {
        return Option.fromNull(methodInvocation.getMethodType());
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
