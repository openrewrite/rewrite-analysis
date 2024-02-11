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
import org.openrewrite.DocumentExample;
import org.openrewrite.ExecutionContext;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.test.RewriteTest.toRecipe;

class ThrowStmtTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(toRecipe(() -> new JavaIsoVisitor<>() {
            @Override
            public J preVisit(J tree, ExecutionContext executionContext) {
                return ThrowStmt.viewOf(getCursor())
                  .map(__ -> SearchResult.found(tree))
                  .orSuccess(tree);
            }
        })).cycles(1).expectedCyclesThatMakeChanges(1);
    }

    @DocumentExample
    @Test
    void correctlyLabelsThrowStatement() {
        rewriteRun(
          java(
            """
              class Test {
                  void test(String s) {
                      if (s == null) {
                          throw new NullPointerException();
                      }
                      System.out.println(s);
                  }
              }
              """,
            """
              class Test {
                  void test(String s) {
                      if (s == null) {
                          /*~~>*/throw new NullPointerException();
                      }
                      System.out.println(s);
                  }
              }
              """
          )
        );
    }

    @Test
    void correctlyLabelsThrowStatementsDifferentMethods() {
        rewriteRun(
          java(
            """
              class Test {
                  void nullCheck(String s) {
                      if (s == null) {
                          throw new NullPointerException();
                      }
                  }
                  void lenCheck(String s) {
                      if (s.length() == 0) {
                          throw new IllegalArgumentException();
                      }
                  }
                  void test(String s) {
                      nullCheck(s);
                      lenCheck(s);
                      System.out.println(s);
                  }
              }
              """,
            """
              class Test {
                  void nullCheck(String s) {
                      if (s == null) {
                          /*~~>*/throw new NullPointerException();
                      }
                  }
                  void lenCheck(String s) {
                      if (s.length() == 0) {
                          /*~~>*/throw new IllegalArgumentException();
                      }
                  }
                  void test(String s) {
                      nullCheck(s);
                      lenCheck(s);
                      System.out.println(s);
                  }
              }
              """
          )
        );
    }
}
