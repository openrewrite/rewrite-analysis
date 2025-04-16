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
package org.openrewrite.analysis.dataflow;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.analysis.trait.expr.Literal;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.test.RewriteTest.toRecipe;

@SuppressWarnings("UnnecessaryLocalVariable")
class FindLocalFlowPathsNumericTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(toRecipe(() -> new FindLocalFlowPaths<>(new DataFlowSpec() {
            @Override
            public boolean isSource(DataFlowNode srcNode) {
                return srcNode
                  .asExpr(Literal.class)
                  .bind(Literal::getValue)
                  .map(l -> "42".equals(l.toString()))
                  .orSome(false);
            }

            @Override
            public boolean isSink(DataFlowNode sinkNode) {
                return true;
            }
        })));
    }

    @DocumentExample
    @Test
    void transitiveAssignment() {
        rewriteRun(
          spec -> spec.expectedCyclesThatMakeChanges(1).cycles(1),
          java(
            """
              class Test {
                  void test() {
                      int n = 42;
                      int o = n;
                      System.out.println(o);
                      int p = o;
                  }
              }
              """,
                """
              class Test {
                  void test() {
                      int n = /*~~>*/42;
                      int o = /*~~>*/n;
                      System.out.println(/*~~>*/o);
                      int p = /*~~>*/o;
                  }
              }
              """
          )
        );
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void multiblockTransitiveAssignment() {
        rewriteRun(
          spec -> spec.expectedCyclesThatMakeChanges(1).cycles(1),
          java(
            """
              class Test {
                  void test() {
                      if (true) {
                          int n = 42;
                          int o = n;
                          System.out.println(o);
                          int p = o;
                      }
                  }
              }
              """,
                """
              class Test {
                  void test() {
                      if (true) {
                          int n = /*~~>*/42;
                          int o = /*~~>*/n;
                          System.out.println(/*~~>*/o);
                          int p = /*~~>*/o;
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void flowPathDetectionWithChainedLambdas() {
        //language=java
        rewriteRun(
          spec -> spec.expectedCyclesThatMakeChanges(1).cycles(1),
          java(
            """
              import java.util.List;
              import java.util.stream.Stream;

              class Test {
                  void test() {
                      List<Integer> list = Stream.of(1, 2, 3).peek(i -> {
                          System.out.println("Number " + i);
                      }).peek(i -> {
                          int n = 42;
                          int o = n;
                          System.out.println(o);
                          int p = o;
                      }).toList();
                  }
              }
              """,
                """
              import java.util.List;
              import java.util.stream.Stream;

              class Test {
                  void test() {
                      List<Integer> list = Stream.of(1, 2, 3).peek(i -> {
                          System.out.println("Number " + i);
                      }).peek(i -> {
                          int n = /*~~>*/42;
                          int o = /*~~>*/n;
                          System.out.println(/*~~>*/o);
                          int p = /*~~>*/o;
                      }).toList();
                  }
              }
              """
          )
        );
    }
}
