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
import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.Incubating;
import org.openrewrite.Tree;
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
    boolean matches(JavaType.@Nullable Method type);

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
            // Avoid creating a new lambda for a single matcher
            return matchers.iterator().next();
        }
        return expression -> matchers.stream().anyMatch(matcher -> matcher.matches(expression));
    }

    /**
     * This method functions in the same way as <code>fromMethodMatcher(new MethodMatcher(methodName))</code>.
     */
    static InvocationMatcher fromMethodMatcher(String methodName) {
        return fromMethodMatcher(new MethodMatcher(methodName));
    }

    /**
     * This method functions in the same way as <code>fromMethodMatcher(new MethodMatcher(methodName, matchOverrides))</code>.
     */
    static InvocationMatcher fromMethodMatcher(String methodName, @Nullable Boolean matchOverrides) {
        return fromMethodMatcher(new MethodMatcher(methodName, matchOverrides));
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
        private final InvocationMatcher matcher;

        public boolean isSelect(Cursor cursor) {
            return asExpression(cursor, expression -> {
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
                    expression -> nearestMethodCall(cursor).map(call ->
                            call.getArguments().contains(expression)
                            && matcher.matches(call) // Do the matcher.matches(...) last as this can be expensive
                    ).orElse(false)
            );
        }

        /**
         * <b>IMPORTANT NOTE:</b> An argument is a value passed during function invocation.
         * <p>
         * By contrast, a parameter is a variable in a function definition.
         * It is a placeholder and hence does not have a concrete value.
         * <p>
         * This method looks at <b>arguments</b>, not parameters.
         * <p>
         * This method is most useful when looking for the first argument passed to a varargs method.
         */
        public boolean isFirstArgument(Cursor cursor) {
            return isArgument(cursor, 0);
        }

        /**
         * <b>IMPORTANT NOTE:</b> An argument is a value passed during function invocation.
         * <p>
         * By contrast, a parameter is a variable in a function definition.
         * It is a placeholder and hence does not have a concrete value.
         * <p>
         * This method looks at <b>arguments</b>, not parameters.
         * <p>
         * This method is most useful when looking for a given argument passed to a varargs method.
         */
        public boolean isArgument(Cursor cursor, int argumentIndex) {
            if (argumentIndex < 0) {
                throw new IllegalArgumentException("argumentIndex < 0");
            }
            return asExpression(cursor, expression -> nearestMethodCall(cursor).map(call -> {
                List<Expression> arguments = call.getArguments();
                if (argumentIndex >= arguments.size()) {
                    return false;
                }
                return arguments.get(argumentIndex) == expression &&
                       matcher.matches(call); // Do the matcher.matches(...) last as this can be expensive
            }).orElse(false));
        }

        /**
         * <b>IMPORTANT NOTE:</b> A parameter is a variable in a function definition.
         * It is a placeholder and hence does not have a concrete value.
         * <p>
         * By contrast, an argument is a value passed during function invocation.
         * <p>
         * This method looks at <b>parameters</b>, not arguments.
         */
        public boolean isFirstParameter(Cursor cursor) {
            return isParameter(cursor, 0);
        }

        /**
         * <b>IMPORTANT NOTE:</b> A parameter is a variable in a function definition.
         * It is a placeholder and hence does not have a concrete value.
         * <p>
         * By contrast, an argument is a value passed during function invocation.
         * <p>
         * This method looks at <b>parameters</b>, not arguments.
         */
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
            Tree closestJ = cursor.getParentTreeCursor().getValue();
            if (closestJ instanceof MethodCall) {
                return Optional.of((MethodCall) closestJ);
            }
            return Optional.empty();
        }

        private static boolean asExpression(Cursor cursor, Predicate<Expression> expressionPredicate) {
            return cursor.getValue() instanceof Expression && expressionPredicate.test(cursor.getValue());
        }
    }
}
