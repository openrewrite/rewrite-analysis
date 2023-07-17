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

public class TryStmtTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(toRecipe(() -> new JavaIsoVisitor<>() {
            @Override
            public J preVisit(J tree, ExecutionContext executionContext) {
                return TryStmt.viewOf(getCursor())
                  .map(__ -> SearchResult.found(tree))
                  .orSuccess(tree);
            }
        })).cycles(1).expectedCyclesThatMakeChanges(1);
    }

    @Test
    void correctlyLabelsTryStatement() {
        rewriteRun(
          java(
            """
              class Test {
                  void test() {
                      try {
                          int i = 1 / 0;
                      } catch (Exception e) {
                          // no-op
                      }
                  }
              }
              """,
            """
              class Test {
                  void test() {
                      /*~~>*/try {
                          int i = 1 / 0;
                      } catch (Exception e) {
                          // no-op
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void correctlyLabelsTryStatementInDifferentMethod() {
        rewriteRun(
          java(
            """
              class Test {
                  double division(double i, double j) {
                      try {
                          return i / j;
                      } catch (Exception e) {
                          // no-op
                      }
                  }
                  void test(double i, double j) {
                      System.out.println(division(i, j));
                  }
              }
              """,
            """
              class Test {
                  double division(double i, double j) {
                      /*~~>*/try {
                          return i / j;
                      } catch (Exception e) {
                          // no-op
                      }
                  }
                  void test(double i, double j) {
                      System.out.println(division(i, j));
                  }
              }
              """
          )
        );
    }
}
