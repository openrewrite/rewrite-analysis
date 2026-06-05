/*
 * Copyright 2022 the original author or authors.
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
package org.openrewrite.analysis.dataflow.internal.csv;

import lombok.Data;
import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.analysis.BasicInvocationMatcher;
import org.openrewrite.analysis.InvocationMatcher;
import org.openrewrite.java.tree.JavaType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

public interface GenericExternalModel extends BasicInvocationMatcher {

    /**
     * Builds an {@link InvocationMatcher} that matches any of {@code models}, indexed by method name.
     * <p>
     * A model can only match a call when its (effective) method name equals the call's name — see
     * {@link #matchesMethodName(String)} — so dispatching on {@link JavaType.Method#getName()} and only
     * checking the models registered under that name yields exactly the same result as scanning every
     * model, but turns the per-call cost from O(models of this flow shape) into O(1) plus the handful of
     * same-named models. This matters because the model set is large (tens of thousands of rows).
     */
    static InvocationMatcher indexedMatcher(Collection<? extends GenericExternalModel> models) {
        if (models.isEmpty()) {
            return type -> false;
        }
        if (models.size() == 1) {
            return models.iterator().next();
        }
        Map<String, List<GenericExternalModel>> byName = new HashMap<>();
        for (GenericExternalModel model : models) {
            // Constructors are invoked with the synthetic name "<constructor>".
            String name = model.isConstructor() ? "<constructor>" : model.getName();
            byName.computeIfAbsent(name, __ -> new ArrayList<>(1)).add(model);
        }
        return type -> {
            if (type == null) {
                return false;
            }
            List<GenericExternalModel> candidates = byName.get(type.getName());
            if (candidates == null) {
                return false;
            }
            for (GenericExternalModel candidate : candidates) {
                if (candidate.matches(type)) {
                    return true;
                }
            }
            return false;
        };
    }

    String getNamespace();
    String getType();
    boolean isSubtypes();
    String getName();
    String getSignature();

    String getArguments();

    default String getFullyQualifiedName() {
        if (getNamespace().isEmpty()) {
            return getType();
        }
        return getNamespace() + "." + getType();
    }

    default boolean isConstructor() {
        // If the type and the name are the same, then this the signature for a constructor
        return this.getType().equals(this.getName());
    }

    @Override
    default boolean isMatchOverrides() {
        return isSubtypes();
    }

    @Override
    default boolean matchesTargetTypeName(String fullyQualifiedTypeName) {
        return getFullyQualifiedName().equals(fullyQualifiedTypeName);
    }

    @Override
    default boolean matchesMethodName(String methodName) {
        if (isConstructor()) {
            return "<constructor>".equals(methodName);
        }
        return getName().equals(methodName);
    }

    @Override
    default boolean matchesParameterTypes(List<JavaType> parameterTypes) {
        if (getSignature().isEmpty()) {
            return true;
        }
        if ("()".equals(getSignature())) {
            return parameterTypes.isEmpty();
        }

        String[] signatureArray = getSignature().substring(1, getSignature().length() - 1).split(",");
        if (signatureArray.length != parameterTypes.size()) {
            return false;
        }
        return IntStream.range(0, parameterTypes.size()).allMatch(i -> Internal.matches(parameterTypes.get(i), signatureArray[i]));
    }

    default Optional<ArgumentRange> getArgumentRange() {
        return computeArgumentRange(getArguments());
    }

    /**
     * The argument positions selected by an {@code Argument[...]} access path, as an inclusive range.
     * <p>
     * Mirrors CodeQL's MaD selector grammar: the bracketed list is comma-separated, and each element
     * is {@code this} (the qualifier, position {@code -1}), an integer, or an inclusive range
     * {@code a..b}; the selector applies to the <em>union</em> of those positions. A comma-separated
     * union is representable here only when it is contiguous (e.g. {@code this,0} = {@code {-1, 0}});
     * a non-contiguous union (e.g. {@code 0,2}) returns empty rather than over-approximating to
     * include positions it does not cover. Returns empty for anything that is not a bare
     * {@code Argument[...]} selector (e.g. {@code ReturnValue} or a content-suffixed path).
     */
    static Optional<ArgumentRange> computeArgumentRange(String arguments) {
        if (!arguments.startsWith("Argument[") || !arguments.endsWith("]")) {
            return Optional.empty();
        }
        String list = arguments.substring("Argument[".length(), arguments.length() - 1);
        if (list.indexOf(',') < 0) {
            // Fast path: a single selector, which is the overwhelmingly common case.
            return Internal.parseSelector(list);
        }
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        int positions = 0;
        for (int start = 0; start <= list.length(); ) {
            int comma = list.indexOf(',', start);
            int end = comma < 0 ? list.length() : comma;
            Optional<ArgumentRange> term = Internal.parseSelector(list.substring(start, end).trim());
            if (!term.isPresent()) {
                return Optional.empty();
            }
            min = Math.min(min, term.get().getStart());
            max = Math.max(max, term.get().getEnd());
            positions += term.get().getEnd() - term.get().getStart() + 1;
            start = end + 1;
            if (comma < 0) {
                break;
            }
        }
        // Contiguous iff the selected positions exactly tile [min, max] with no gaps or overlaps.
        if (max - min + 1 != positions) {
            return Optional.empty();
        }
        return Optional.of(new ArgumentRange(min, max));
    }

    @Data
    class MethodMatcherKey {
        final String signature;
        final boolean matchOverrides;
    }

    @Data
    class ArgumentRange {
        // Inclusive
        @Getter
        final int start;

        // Inclusive
        @Getter
        final int end;
    }
}

class Internal {

    /** Parses a single selector inside {@code Argument[...]}: {@code this}, an integer {@code x}, or an inclusive range {@code x..y}. */
    static Optional<GenericExternalModel.ArgumentRange> parseSelector(String selector) {
        if ("this".equals(selector)) {
            // The receiver/qualifier. CodeQL's older dialect spelled this `Argument[-1]`.
            return Optional.of(new GenericExternalModel.ArgumentRange(-1, -1));
        }
        int dots = selector.indexOf("..");
        if (dots < 0) {
            Integer i = parseIndex(selector);
            return i == null ? Optional.empty() : Optional.of(new GenericExternalModel.ArgumentRange(i, i));
        }
        Integer lo = parseIndex(selector.substring(0, dots));
        Integer hi = parseIndex(selector.substring(dots + 2));
        return lo != null && hi != null && lo <= hi ?
                Optional.of(new GenericExternalModel.ArgumentRange(lo, hi)) :
                Optional.empty();
    }

    /** Parses a (possibly negative) argument index, returning {@code null} for any non-numeric token. */
    private static @Nullable Integer parseIndex(String s) {
        int start = !s.isEmpty() && s.charAt(0) == '-' ? 1 : 0;
        if (start == s.length()) {
            return null;
        }
        for (int i = start; i < s.length(); i++) {
            if (s.charAt(i) < '0' || s.charAt(i) > '9') {
                return null;
            }
        }
        return Integer.parseInt(s);
    }

    static boolean matches(JavaType parameter, String parameterSignature) {
        String parameterString = typePattern(parameter);

        if (parameterString == null) {
            return false;
        }
        if (parameterSignature.contains(".")) {
            return parameterString.equals(parameterSignature);
        }

        return parameterString
                .substring(parameterString.lastIndexOf('.') + 1)
                .equals(parameterSignature);
    }

    static @Nullable String typePattern(JavaType type) {
        if (type instanceof JavaType.Primitive) {
            if (type.equals(JavaType.Primitive.String)) {
                return ((JavaType.Primitive) type).getClassName();
            }
            return ((JavaType.Primitive) type).getKeyword();
        }
        if (type instanceof JavaType.Unknown) {
            return "*";
        }
        if (type instanceof JavaType.FullyQualified) {
            return ((JavaType.FullyQualified) type).getFullyQualifiedName();
        }
        if (type instanceof JavaType.Array) {
            JavaType elemType = ((JavaType.Array) type).getElemType();
            return typePattern(elemType) + "[]";
        }
        return null;
    }
}
