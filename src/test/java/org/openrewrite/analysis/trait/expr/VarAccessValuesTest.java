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
import org.openrewrite.ExecutionContext;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.Collection;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.test.RewriteTest.toRecipe;

public class VarAccessValuesTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(toRecipe(() -> new JavaIsoVisitor<>() {
            @Override
            public J preVisit(J tree, ExecutionContext executionContext) {
                return VarAccess.viewOf(getCursor())
                  .map(var -> {
                      Collection<Expr> values = var.getVariable().getAssignedValues();
                      return SearchResult.mergingFound(
                        tree,
                        String.join(" ", values.stream().map(v -> ((Literal) v).getValue().some().toString()).toList())
                      );
                  })
                  .orSuccess(tree);
            }
        })).cycles(1).expectedCyclesThatMakeChanges(1);
    }

    @Test
    void correctlyLabelsVariableValues() {
        rewriteRun(
          java(
            """
              class Test {
                  void test() {
                      String s = "first";
                      s = "second";
                  }
              }
              """,
            """
              class Test {
                  void test() {
                      String s = "first";
                      /*~~(first second)~~>*/s = "second";
                  }
              }
              """
          )
        );
    }

    @Test
    void correctlyLabelsVariableValuesNoImmediateInstantiation() {
        rewriteRun(
          java(
            """
              class Test {
                  void test() {
                      String s;
                      s = "first";
                  }
              }
              """,
            """
              class Test {
                  void test() {
                      String s;
                      /*~~(first)~~>*/s = "first";
                  }
              }
              """
          )
        );
    }

    @Test
    void correctlyLabelsDifferentVariablesValues() {
        rewriteRun(
          java(
            """
              class Test {
                  void test() {
                      int i = 42;
                      String s;
                      int j;
                      i = 16;
                      s = "hello";
                      i = 32;
                      j = 1;
                      s = "world";
                  }
              }
              """,
            """
              class Test {
                  void test() {
                      int i = 42;
                      String s;
                      int j;
                      /*~~(42 16 32)~~>*/i = 16;
                      /*~~(hello world)~~>*/s = "hello";
                      /*~~(42 16 32)~~>*/i = 32;
                      /*~~(1)~~>*/j = 1;
                      /*~~(hello world)~~>*/s = "world";
                  }
              }
              """
          )
        );
    }

    @Test
    void correctlyLabelsVariableValuesOnAllPaths() {
        rewriteRun(
          java(
            """
              class Test {
                  void test() {
                      int i = 1;
                      if (i + 1 == 2) {
                          i = 2;
                      } else {
                          i = 3;
                      }
                  }
              }
              """,
            """
              class Test {
                  void test() {
                      int i = 1;
                      if (/*~~(1 2 3)~~>*/i + 1 == 2) {
                          /*~~(1 2 3)~~>*/i = 2;
                      } else {
                          /*~~(1 2 3)~~>*/i = 3;
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void correctlyLabelsParenthesizedVariableValues() {
        rewriteRun(
          java(
            """
              class Test {
                  void test() {
                      int i = 1;
                      (i) = 2;
                      ((i)) = 3;
                  }
              }
              """,
            """
              class Test {
                  void test() {
                      int i = 1;
                      (/*~~(1 2 3)~~>*/i) = 2;
                      ((/*~~(1 2 3)~~>*/i)) = 3;
                  }
              }
              """
          )
        );
    }

    @Test
    void correctlyLabelsVariableValuesThis() {
        rewriteRun(
          java(
            """
              class Test {
                  int i = 42;
                  void test() {
                      this.i = 16;
                      this.i = 12;
                  }
              }
              """,
            """
              class Test {
                  int i = 42;
                  void test() {
                      this./*~~(42 16 12)~~>*/i = 16;
                      this./*~~(42 16 12)~~>*/i = 12;
                  }
              }
              """
          )
        );
    }

    @Test
    void correctlyLabelsDuplicateVariableValuesThisNoInstantiation() {
        rewriteRun(
          java(
            """
              class Test {
                  int i;
                  void test1(int i) {
                      this.i = 1;
                      i = 2;
                  }
                  void test2() {
                      this.i = 3;
                      int i = 4;
                      i = 5;
                  }
              }
              """,
            """
              class Test {
                  int i;
                  void test1(int i) {
                      this./*~~(1 3)~~>*/i = 1;
                      /*~~(2)~~>*/i = 2;
                  }
                  void test2() {
                      this./*~~(1 3)~~>*/i = 3;
                      int i = 4;
                      /*~~(4 5)~~>*/i = 5;
                  }
              }
              """
          )
        );
    }
}
