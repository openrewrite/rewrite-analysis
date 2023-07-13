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
package org.openrewrite.analysis.trait.member;
import fj.data.Option;
import fj.data.Validation;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.openrewrite.Cursor;
import org.openrewrite.analysis.trait.Top;
import org.openrewrite.analysis.trait.TraitFactory;
import org.openrewrite.analysis.trait.util.TraitErrors;
import org.openrewrite.analysis.trait.variable.Parameter;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** A method is a particular kind of callable. */
public interface Method extends Callable {

    enum Factory implements TraitFactory<Method> {
        F;

        @Override
        public Validation<TraitErrors, Method> viewOf(Cursor cursor) {
            if (cursor.getValue() instanceof J.MethodDeclaration) {
                return Validation.success(new MethodDeclarationMethod(
                        cursor,
                        cursor.getValue()
                ));
            }
            return TraitErrors.invalidTraitCreationType(Callable.class, cursor, J.MethodDeclaration.class);
        }
    }

    static Validation<TraitErrors, Method> viewOf(Cursor cursor) {
        return Factory.F.viewOf(cursor);
    }
}
@AllArgsConstructor
class MethodDeclarationMethod extends Top.Base implements Method {
    Cursor cursor;

    J.MethodDeclaration methodDeclaration;

    @Getter(lazy = true, onMethod = @__(@Override))
    private final List<Parameter> parameters = collectParameters(cursor, methodDeclaration);

    @Override
    public String getName() {
        return methodDeclaration.getSimpleName();
    }

    @Override
    public Option<JavaType> getReturnType() {
        if (methodDeclaration.getReturnTypeExpression() == null) {
            return Option.some(JavaType.Primitive.Void);
        }
        return Option.fromNull(methodDeclaration.getReturnTypeExpression().getType());
    }

    @Override
    public Option<JavaType.Method> getMethodType() {
        return Option.fromNull(methodDeclaration.getMethodType());
    }

    @Override
    public UUID getId() {
        return methodDeclaration.getId();
    }

    private static List<Parameter> collectParameters(Cursor cursor, J.MethodDeclaration methodDeclaration) {
        assert cursor.getValue() == methodDeclaration;
        List<Parameter> parameters = new ArrayList<>(methodDeclaration.getParameters().size());
        new JavaVisitor<List<Parameter>>() {
            {
                // Correctly set the parent cursor for the parameters
                setCursor(cursor);
            }

            @Override
            public J visitVariable(J.VariableDeclarations.NamedVariable variable, List<Parameter> parameters) {
                parameters.add(Parameter.viewOf(getCursor()).on(TraitErrors::doThrow));
                return variable;
            }
        }.visit(methodDeclaration.getParameters(), parameters);
        return Collections.unmodifiableList(parameters);
    }
}
