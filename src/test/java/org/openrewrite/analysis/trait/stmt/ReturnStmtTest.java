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
package org.openrewrite.analysis.trait.stmt;

import org.junit.jupiter.api.Test;
import org.openrewrite.ExecutionContext;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.test.RewriteTest.toRecipe;

public class ReturnStmtTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(toRecipe(() -> new JavaIsoVisitor<>() {
            @Override
            public J preVisit(J tree, ExecutionContext executionContext) {
                return ReturnStmt.viewOf(getCursor())
                  .map(__ -> SearchResult.found(tree))
                  .orSuccess(tree);
            }
        })).cycles(1).expectedCyclesThatMakeChanges(1);
    }

    @Test
    void correctlyLabelsReturnStatement() {
        rewriteRun(
          java(
            """
              class Test {
                  int test() {
                      int i = 16 + 4;
                      return i;
                  }
              }
              """,
            """
              class Test {
                  int test() {
                      int i = 16 + 4;
                      /*~~>*/return i;
                  }
              }
              """
          )
        );
    }

    @Test
    void correctlyLabelsReturnStatementWithLiteral() {
        rewriteRun(
          java(
            """
              class Test {
                  boolean test() {
                      return true;
                  }
              }
              """,
            """
              class Test {
                  boolean test() {
                      /*~~>*/return true;
                  }
              }
              """
          )
        );
    }

    @Test
    void correctlyLabelsReturnStatementDifferentMethods() {
        rewriteRun(
          java(
            """
              class Test {
                  int foo(String s) {
                      return s.length();
                  }
                  boolean test() {
                      String s = "string";
                      return foo(s) > 3;
                  }
              }
              """,
            """
              class Test {
                  int foo(String s) {
                      /*~~>*/return s.length();
                  }
                  boolean test() {
                      String s = "string";
                      /*~~>*/return foo(s) > 3;
                  }
              }
              """
          )
        );
    }

    @Test
    void tagsAllPossibleReturns() {
        rewriteRun(
          java(
            """
              class Test {
                  int unused() {
                      return 1;
                  }
                  int test() {
                      if (false) {
                          return 1;
                      }
                      return 2;
                  }
              }
              """,
            """
              class Test {
                  int unused() {
                      /*~~>*/return 1;
                  }
                  int test() {
                      if (false) {
                          /*~~>*/return 1;
                      }
                      /*~~>*/return 2;
                  }
              }
              """
          )
        );
    }

    @Test
    void noReturn() {
        rewriteRun(
          spec -> spec.expectedCyclesThatMakeChanges(0),
          java(
            """
              class Test {
                  void test() {
                      float f = 1.0 + 2.0;
                      System.out.println(f);
                      char a = 'a';
                  }
              }
              """
          )
        );
    }
}
