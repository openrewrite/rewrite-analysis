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

import org.openrewrite.Incubating;
import org.openrewrite.Tree;
import org.openrewrite.analysis.InvocationMatcher;
import org.openrewrite.internal.LoathingOfOthers;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.marker.SearchResult;

/**
 * Equivalent to {@link org.openrewrite.java.search.UsesMethod} but for any {@link InvocationMatcher}.
 *
 * @implNote If {@link org.openrewrite.java.MethodMatcher} were or had a base interface, this wouldn't be needed.
 */
@Incubating(since = "2.2.4")
@LoathingOfOthers("org.openrewrite.java.MethodMatcher")
public class UsesInvocation<P> extends JavaIsoVisitor<P> {
    private final InvocationMatcher invocationMatcher;

    public UsesInvocation(InvocationMatcher invocationMatcher) {
        this.invocationMatcher = invocationMatcher;
    }

    @Override
    public J visit(@Nullable Tree tree, P p) {
        if (tree instanceof JavaSourceFile) {
            JavaSourceFile cu = (JavaSourceFile) tree;
            for (JavaType.Method type : cu.getTypesInUse().getUsedMethods()) {
                if (invocationMatcher.matches(type)) {
                    return SearchResult.found(cu);
                }
            }
            return (J) tree;
        }
        return super.visit(tree, p);
    }

    @Override
    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, P p) {
        if (invocationMatcher.matches(method)) {
            return SearchResult.found(method);
        }
        return super.visitMethodInvocation(method, p);
    }

    @Override
    public J.MemberReference visitMemberReference(J.MemberReference memberRef, P p) {
        if (invocationMatcher.matches(memberRef)) {
            return SearchResult.found(memberRef);
        }
        return super.visitMemberReference(memberRef, p);
    }

    @Override
    public J.NewClass visitNewClass(J.NewClass newClass, P p) {
        if (invocationMatcher.matches(newClass)) {
            return SearchResult.found(newClass);
        }
        return super.visitNewClass(newClass, p);
    }
}
