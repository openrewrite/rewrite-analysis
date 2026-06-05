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
import org.openrewrite.java.tree.Flag;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static java.util.Collections.unmodifiableList;

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
            if (cursor.getValue() instanceof J.Lambda) {
                // A lambda expression's implicit method corresponds to the single abstract method
                // of its functional interface; the lambda's parameters are the parameters of this
                // method and the lambda body is the method body. See {@code LambdaExpr#asMethod()}.
                return Validation.success(new LambdaMethod(
                        cursor,
                        cursor.getValue()
                ));
            }
            return TraitErrors.invalidTraitCreationType(Method.class, cursor, J.MethodDeclaration.class);
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

    @Getter(lazy = true, onMethod_ =@Override)
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
        new ParameterCollector(cursor).visit(methodDeclaration.getParameters(), parameters);
        return unmodifiableList(parameters);
    }
}

/**
 * Collects the {@link Parameter}s declared under a callable. The parent cursor must be set to the
 * callable so that each parameter resolves with the correct cursor path.
 */
class ParameterCollector extends JavaVisitor<List<Parameter>> {
    ParameterCollector(Cursor callableCursor) {
        setCursor(callableCursor);
    }

    @Override
    public J visitVariable(J.VariableDeclarations.NamedVariable variable, List<Parameter> parameters) {
        parameters.add(Parameter.viewOf(getCursor()).on(TraitErrors::doThrow));
        return variable;
    }
}

/**
 * The implicit {@link Method} corresponding to a lambda expression. Modeled after CodeQL's
 * {@code LambdaExpr.asMethod()}: the method corresponds to the single abstract method of the
 * lambda's functional interface, the lambda's parameters are the parameters of this method,
 * and the lambda body is the method body.
 */
@AllArgsConstructor
class LambdaMethod extends Top.Base implements Method {
    Cursor cursor;

    J.Lambda lambda;

    @Getter(lazy = true, onMethod_ = @Override)
    private final List<Parameter> parameters = collectParameters(cursor, lambda);

    @Getter(lazy = true)
    private final Option<JavaType.Method> sam = findSam(lambda);

    @Override
    public String getName() {
        // Fall back to a synthetic name (cf. "<clinit>"/"<obinit>") when the SAM is unresolved.
        return getSam().map(JavaType.Method::getName).orSome("<lambda>");
    }

    @Override
    public Option<JavaType> getReturnType() {
        return getSam().map(JavaType.Method::getReturnType);
    }

    @Override
    public Option<JavaType.Method> getMethodType() {
        return getSam();
    }

    @Override
    public UUID getId() {
        return lambda.getId();
    }

    private static List<Parameter> collectParameters(Cursor cursor, J.Lambda lambda) {
        assert cursor.getValue() == lambda;
        List<Parameter> parameters = new ArrayList<>(lambda.getParameters().getParameters().size());
        // Visit the Lambda.Parameters wrapper (not its inner list) so each parameter's cursor path
        // is NamedVariable -> VariableDeclarations -> Lambda.Parameters -> Lambda, matching the LST.
        new ParameterCollector(cursor).visit(lambda.getParameters(), parameters);
        return unmodifiableList(parameters);
    }

    /**
     * Resolves the single abstract method (SAM) of the lambda's functional interface type.
     * Returns {@link Option#none()} when the type information is missing or the SAM cannot be
     * unambiguously determined, allowing callers to fall back gracefully.
     */
    private static Option<JavaType.Method> findSam(J.Lambda lambda) {
        JavaType type = lambda.getType();
        if (!(type instanceof JavaType.FullyQualified)) {
            return Option.none();
        }
        int arity = 0;
        for (J parameter : lambda.getParameters().getParameters()) {
            if (!(parameter instanceof J.Empty)) {
                arity++;
            }
        }
        JavaType.Method found = null;
        for (JavaType.Method method : ((JavaType.FullyQualified) type).getMethods()) {
            // The SAM is neither static nor a default method, and matches the lambda's arity.
            if (method.hasFlags(Flag.Static) || method.hasFlags(Flag.Default)) {
                continue;
            }
            if (method.getParameterTypes().size() != arity) {
                continue;
            }
            if (found != null) {
                // Ambiguous; fall back gracefully.
                return Option.none();
            }
            found = method;
        }
        return Option.fromNull(found);
    }
}
