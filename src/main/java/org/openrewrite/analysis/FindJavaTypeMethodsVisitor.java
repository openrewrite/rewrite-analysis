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

import lombok.AllArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.marker.SearchResult;

/**
 * This visitor is used to find method invocations that match a given method pattern as defined by {@link InvocationMatcher}.
 * <p/>
 * This visitor is most often useful for testing custom implementations of {@link InvocationMatcher}.
 */
@AllArgsConstructor
public class FindJavaTypeMethodsVisitor extends JavaIsoVisitor<ExecutionContext> {
    private final InvocationMatcher methodMatcher;

    @Override
    public J.@NotNull MethodInvocation visitMethodInvocation(J.@NotNull MethodInvocation method, @NotNull ExecutionContext ctx) {
        J.MethodInvocation m = super.visitMethodInvocation(method, ctx);
        if (methodMatcher.matches(method)) {
            m = SearchResult.found(m);
        }
        return m;
    }

    @Override
    public J.@NotNull MemberReference visitMemberReference(J.@NotNull MemberReference memberRef, @NotNull ExecutionContext ctx) {
        J.MemberReference m = super.visitMemberReference(memberRef, ctx);
        if (methodMatcher.matches(m.getMethodType())) {
            m = m.withReference(SearchResult.found(m.getReference()));
        }
        return m;
    }

    @Override
    public J.@NotNull NewClass visitNewClass(J.@NotNull NewClass newClass, @NotNull ExecutionContext ctx) {
        J.NewClass n = super.visitNewClass(newClass, ctx);
        if (methodMatcher.matches(newClass)) {
            n = SearchResult.found(n);
        }
        return n;
    }
}
