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
package org.openrewrite.analysis.trait.expr;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.ExecutionContext;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.test.RewriteTest.toRecipe;

public class BinaryExprTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(toRecipe(() -> new JavaIsoVisitor<>() {
            @Override
            public J preVisit(J tree, ExecutionContext executionContext) {
                return BinaryExpr.viewOf(getCursor())
                  .map(b -> SearchResult.found(tree, "operator: " + b.getOperator() + ", type: " + b.getType()))
                  .orSuccess(tree);
            }
        })).cycles(1).expectedCyclesThatMakeChanges(1);
    }

    @DocumentExample
    @Test
    void correctlyLabelsBinaryExpr() {
        rewriteRun(
          java(
            """
              class Test {
                  void test() {
                      int i = 16 + 4;
                  }
              }
              """,
            """
              class Test {
                  void test() {
                      int i = /*~~(operator: Addition, type: int)~~>*/16 + 4;
                  }
              }
              """
          )
        );
    }

    @Test
    void correctlyLabelsBinaryExprWithVariables() {
        rewriteRun(
          java(
            """
              class Test {
                  void test(double d1, double d2) {
                      double i = d1 + d2;
                      int j = 2;
                      double k = i / j;
                  }
              }
              """,
            """
              class Test {
                  void test(double d1, double d2) {
                      double i = /*~~(operator: Addition, type: double)~~>*/d1 + d2;
                      int j = 2;
                      double k = /*~~(operator: Division, type: double)~~>*/i / j;
                  }
              }
              """
          )
        );
    }

    @Test
    void correctlyLabelsBinaryExprInConditional() {
        rewriteRun(
          java(
            """
              class Test {
                  void test(int i, int j) {
                      if (i > j) {
                          return true;
                      }
                      return false;
                  }
              }
              """,
            """
              class Test {
                  void test(int i, int j) {
                      if (/*~~(operator: GreaterThan, type: boolean)~~>*/i > j) {
                          return true;
                      }
                      return false;
                  }
              }
              """
          )
        );
    }

    @Test
    void correctlyLabelsBinaryExprInReturnValue() {
        rewriteRun(
          java(
            """
              class Test {
                  int foo() {
                      return 'z' - 'b';
                  }
                  
                  void test() {
                      int c = foo();
                  }
              }
              """,
            """
              class Test {
                  int foo() {
                      return /*~~(operator: Subtraction, type: int)~~>*/'z' - 'b';
                  }
                  
                  void test() {
                      int c = foo();
                  }
              }
              """
          )
        );
    }

    @Test
    void correctlyLabelsBinaryExprAsArgument() {
        rewriteRun(
          java(
            """
              class Test {
                  float foo(float f) {
                      return f;
                  }
                  
                  void test() {
                      float f = foo(19.0f + 2.0f);
                  }
              }
              """,
            """
              class Test {
                  float foo(float f) {
                      return f;
                  }
                  
                  void test() {
                      float f = foo(/*~~(operator: Addition, type: float)~~>*/19.0f + 2.0f);
                  }
              }
              """
          )
        );
    }

    @Test
    void correctlyLabelsBinaryExprInMethodCall() {
        rewriteRun(
          java(
            """
              class Test {
                  boolean test() {
                      return ("hello " + "world").equals("hello world");
                  }
              }
              """,
            """
              class Test {
                  boolean test() {
                      return (/*~~(operator: Addition, type: java.lang.String)~~>*/"hello " + "world").equals("hello world");
                  }
              }
              """
          )
        );
    }

    @Test
    void correctlyLabelsMultipleBinaryExprDifferentOperator() {
        rewriteRun(
          java(
            """
              class Test {
                  void test() {
                      float f = 1.0f + 2.0f * 3.0f;
                  }
              }
              """,
            """
              class Test {
                  void test() {
                      float f = /*~~(operator: Addition, type: float)~~>*/1.0f + /*~~(operator: Multiplication, type: float)~~>*/2.0f * 3.0f;
                  }
              }
              """
          )
        );
    }

    @Test
    void correctlyLabelsMultipleBinaryExprSameOperator() {
        rewriteRun(
          java(
            """
              class Test {
                  void test() {
                      boolean b = true && false && true;
                  }
              }
              """,
            """
              class Test {
                  void test() {
                      boolean b = /*~~(operator: And, type: boolean)~~>*//*~~(operator: And, type: boolean)~~>*/true && false && true;
                  }
              }
              """
          )
        );
    }

    @Test
    void correctlyLabelsNestedBinaryExpr() {
        rewriteRun(
          java(
            """
              class Test {
                  void test() {
                      int b = (2 + 3) - (5 * 6);
                  }
              }
              """,
            """
              class Test {
                  void test() {
                      int b = /*~~(operator: Subtraction, type: int)~~>*/(/*~~(operator: Addition, type: int)~~>*/2 + 3) - (/*~~(operator: Multiplication, type: int)~~>*/5 * 6);
                  }
              }
              """
          )
        );
    }

    @Test
    void noBinaryExpr() {
        rewriteRun(
          spec -> spec.expectedCyclesThatMakeChanges(0),
          java(
            """
              class Test {
                  void test(int i, boolean b) {
                      if (b) {
                          i += 5;
                          int j = i;
                      } else {
                          int k = 1;
                          k -= i;
                      }
                  }
              }
              """
          )
        );
    }
}
