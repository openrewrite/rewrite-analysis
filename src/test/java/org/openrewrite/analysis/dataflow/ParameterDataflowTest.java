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
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.test.RewriteTest.toRecipe;

class ParameterDataflowTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(toRecipe(() -> new FindLocalFlowPaths<>(new TaintFlowSpec() {
            @Override
            public boolean isSource(DataFlowNode srcNode) {
                return srcNode
                  .asParameter()
                  .map(p -> "source".equals(p.getName()))
                  .orSome(false);
            }

            @Override
            public boolean isSink(DataFlowNode sinkNode) {
                return true;
            }
        }))).expectedCyclesThatMakeChanges(1).cycles(1);
    }

    @DocumentExample
    @Test
    void dataflowFromSourceArgumentToPrintln() {
        rewriteRun(
          java(
            """
              class Test {
                  void test(String source) {
                      System.out.println(source);
                  }
              }
              """,
            """
              class Test {
                  void test(String /*~~>*/source) {
                      System.out.println(/*~~>*/source);
                  }
              }
              """
          )
        );
    }

    @Test
    void dataflowFromSourceArgumentToExpressions() {
        rewriteRun(
          java(
            """
              class Test {
                  void test(int source) {
                      int j = source;
                      int l = j;
                  }
              }
              """,
            """
              class Test {
                  void test(int /*~~>*/source) {
                      int j = /*~~>*/source;
                      int l = /*~~>*/j;
                  }
              }
              """
          )
        );
    }
}
