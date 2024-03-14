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
package org.openrewrite.analysis.util;

import fj.data.Option;
import org.junit.jupiter.api.Test;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openrewrite.java.Assertions.java;

class CursorUtilTest implements RewriteTest {

    private static final class FindCursorForTreeTestVisitor extends JavaVisitor<ExecutionContext> {

        @Override
        public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
            Option<Cursor> found = CursorUtil.findCursorForTree(getCursor(), method.getArguments().get(0));

            assertTrue(found.isSome(), "Cursor for the sub-tree tree not found");
            assertThat(found.some().<J>getValue()).isEqualTo(method.getArguments().get(0));
            assertThat(found.some().dropParentUntil(J.MethodInvocation.class::isInstance).<J>getValue()).isEqualTo(method);
            assertThat(found.some().dropParentUntil(J.CompilationUnit.class::isInstance)).isNotNull();

            return super.visitMethodInvocation(method, executionContext);
        }
    }

    @Test
    void findCursorForTreeTest() {
        rewriteRun(
          spec -> spec.recipe(RewriteTest.toRecipe(FindCursorForTreeTestVisitor::new)),
          java(
            """
            class Test {
                void test() {
                    System.out.println("Hello, World!");
                }
            }
            """
          )
        );
    }
}
