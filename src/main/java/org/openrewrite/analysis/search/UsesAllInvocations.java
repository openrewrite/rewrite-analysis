/*
 * Copyright 2024 the original author or authors.
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
package org.openrewrite.analysis.search;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Incubating;
import org.openrewrite.Tree;
import org.openrewrite.analysis.InvocationMatcher;
import org.openrewrite.internal.LoathingOfOthers;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.marker.SearchResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Marks a {@link JavaSourceFile} as matching if all the passed methods are found.
 *
 * @implNote This is a copy of {@link org.openrewrite.java.search.UsesAllMethods} but for any {@link InvocationMatcher}.
 */
@Incubating(since = "2.2.4")
@LoathingOfOthers("org.openrewrite.java.MethodMatcher")
@RequiredArgsConstructor
public class UsesAllInvocations<P> extends JavaIsoVisitor<P> {
    private final List<InvocationMatcher> invocationMatchers;

    public UsesAllInvocations(InvocationMatcher... invocationMatchers) {
        this(Arrays.asList(invocationMatchers));
    }

    @Override
    public J visit(@Nullable Tree tree, P p) {
        if (tree instanceof JavaSourceFile) {
            JavaSourceFile cu = (JavaSourceFile) requireNonNull(tree);
            List<InvocationMatcher> unmatched = new ArrayList<>(invocationMatchers);
            for (JavaType.Method type : cu.getTypesInUse().getUsedMethods()) {
                if (unmatched.removeIf(matcher -> matcher.matches(type)) && unmatched.isEmpty()) {
                    return SearchResult.found(cu);
                }
            }
        }
        return (J) tree;
    }
}
