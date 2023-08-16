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
package org.openrewrite.analysis;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.openrewrite.Cursor;
import org.openrewrite.Incubating;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.*;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * The most basic version of a {@link MethodMatcher} that allows implementers to craft custom matching logic.
 */
@Incubating(since = "8.1.3")
@FunctionalInterface
public interface InvocationMatcher {

    /**
     * Whether the method invocation or constructor matches the criteria of this matcher.
     *
     * @param type The type of the method invocation or constructor.
     * @return True if the invocation or constructor matches the criteria of this matcher.
     */
    boolean matches(@Nullable JavaType.Method type);

    default boolean matches(@Nullable MethodCall methodCall) {
        if (methodCall == null) {
            return false;
        }
        return matches(methodCall.getMethodType());
    }

    /**
     * Whether the method invocation or constructor matches the criteria of this matcher.
     *
     * @param maybeMethod Any {@link Expression} that might be a method invocation or constructor.
     * @return True if the invocation or constructor matches the criteria of this matcher.
     */
    default boolean matches(@Nullable Expression maybeMethod) {
        return maybeMethod instanceof MethodCall && matches(((MethodCall) maybeMethod).getMethodType());
    }

    static InvocationMatcher from(Collection<? extends InvocationMatcher> matchers) {
        if (matchers.isEmpty()) {
            return expression -> false;
        }
        if (matchers.size() == 1) {
            return e -> matchers.iterator().next().matches(e);
        }
        return expression -> matchers.stream().anyMatch(matcher -> matcher.matches(expression));
    }

    static InvocationMatcher fromMethodMatcher(MethodMatcher methodMatcher) {
        return methodMatcher::matches;
    }

    static InvocationMatcher fromMethodMatchers(MethodMatcher... matchers) {
        return fromMethodMatchers(Arrays.asList(matchers));
    }

    static InvocationMatcher fromMethodMatchers(Collection<? extends MethodMatcher> matchers) {
        return from(matchers.stream().map(InvocationMatcher::fromMethodMatcher).collect(Collectors.toSet()));
    }

    default AdvancedInvocationMatcher advanced() {
        return new AdvancedInvocationMatcher(this);
    }

    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    final class AdvancedInvocationMatcher {
        private InvocationMatcher matcher;

        public boolean isSelect(Cursor cursor) {
            return asExpression(cursor, expression -> {
                assert expression == cursor.getValue() : "expression != cursor.getValue()";
                J.MethodInvocation maybeMethodInvocation =
                        cursor.getParentOrThrow().firstEnclosing(J.MethodInvocation.class);
                return maybeMethodInvocation != null &&
                       maybeMethodInvocation.getSelect() == expression &&
                       matcher.matches(maybeMethodInvocation); // Do the matcher.matches(...) last as this can be expensive
            });
        }

        public boolean isAnyArgument(Cursor cursor) {
            return asExpression(
                    cursor,
                    expression -> nearestMethodCall(cursor).map(call -> call.getArguments().contains(expression)
                               && matcher.matches(call)).orElse(false)
            );
        }

        public boolean isFirstParameter(Cursor cursor) {
            return isParameter(cursor, 0);
        }

        public boolean isParameter(Cursor cursor, int parameterIndex) {
            if (parameterIndex < 0) {
                throw new IllegalArgumentException("parameterIndex < 0");
            }
            return asExpression(cursor, expression -> nearestMethodCall(cursor).map(call -> {
                List<Expression> arguments = call.getArguments();
                if (parameterIndex >= arguments.size()) {
                    return false;
                }
                if (doesMethodHaveVarargs(call)) {
                    // The varargs parameter is the last one, so we need to check if the expression is the last
                    // parameter or any further argument
                    final int finalParameterIndex =
                            getType(call)
                                    .map(JavaType.Method::getParameterTypes)
                                    .map(List::size)
                                    .map(size -> size - 1)
                                    .orElse(-1);
                    if (finalParameterIndex == parameterIndex) {
                        List<Expression> varargs = arguments.subList(finalParameterIndex, arguments.size());
                        return varargs.contains(expression) &&
                                matcher.matches(call); // Do the matcher.matches(...) last as this can be expensive
                    }
                }
                return arguments.get(parameterIndex) == expression &&
                        matcher.matches(call); // Do the matcher.matches(...) last as this can be expensive
            }).orElse(false));
        }

        private static boolean doesMethodHaveVarargs(MethodCall expression) {
            return getType(expression).map(type -> type.hasFlags(Flag.Varargs)).orElse(false);
        }

        private static Optional<JavaType.Method> getType(MethodCall expression) {
            return Optional.ofNullable(expression.getMethodType());
        }

        private static Optional<MethodCall> nearestMethodCall(Cursor cursor) {
            J closestJ = cursor.getParentTreeCursor().getValue();
            if (closestJ instanceof MethodCall) {
                return Optional.of((MethodCall) closestJ);
            }
            return Optional.empty();
        }

        private static boolean asExpression(Cursor cursor, Predicate<Expression> expressionPredicate) {
            return cursor.getValue() instanceof Expression && expressionPredicate.test((Expression) cursor.getValue());
        }
    }
}
