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
package org.openrewrite.analysis.dataflow;

import org.junit.jupiter.api.Test;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.test.RewriteTest.toRecipe;

public class DataFlowNodeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(toRecipe(() -> new TreeVisitor<>() {
            @Override
            public @Nullable Tree preVisit(Tree tree, ExecutionContext executionContext) {
                Tree t = super.preVisit(tree, executionContext);
                return DataFlowNode
                  .of(getCursor())
                  .map(df -> SearchResult.found(t))
                  .orSome(t);
            }
        }));
    }

    @Test
    void noDataFlowNodes() {
        rewriteRun(
          java(
            "class A {}"
          )
        );
    }

    @Test
    void importsAreNotDataFlowNodes() {
        rewriteRun(
          java("""
            import java.util.*;
            class A {}
            """
          )
        );
    }

    @Test
    void dataFlowNodes() {
        rewriteRun(
          java("""
            class A {
               int test(int i) {
                  i++;
                  return i;
               }
            }
            """,
            """
            class A {
               int test(int /*~~>*/i) {
                  /*~~>*//*~~>*/i++;
                  return /*~~>*/i;
               }
            }
            """
          )
        );
    }
}
