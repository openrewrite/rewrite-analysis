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
package org.openrewrite.analysis.trait.variable;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.ExecutionContext;
import org.openrewrite.analysis.trait.expr.VarAccess;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.test.RewriteTest.toRecipe;

class LambdaTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(toRecipe(() -> new JavaIsoVisitor<>() {
            @Override
            public J preVisit(J tree, ExecutionContext executionContext) {
                return VarAccess.viewOf(getCursor())
                  .map(var -> {
                      assertThat(var.getVariable()).as("VarAccess.getVariable() is null").isNotNull();
                      return SearchResult.found(tree);
                  })
                  .orSuccess(tree);
            }
        }));
    }

    @DocumentExample
    @Test
    void lambdaException() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.concurrent.FutureTask;
              
              class Test {
                  void foobar() {
                      FutureTask<Exception> task = new FutureTask<>(() -> {
                          try {
                              return null;
                          } catch (Exception e) {
                              return e;
                          }
                      });
                  }
              }
              """,
            """
              import java.util.concurrent.FutureTask;

              class Test {
                  void foobar() {
                      FutureTask<Exception> task = new FutureTask<>(() -> {
                          try {
                              return null;
                          } catch (Exception e) {
                              return /*~~>*/e;
                          }
                      });
                  }
              }
              """
          )
        );
    }

    @Disabled("https://github.com/openrewrite/rewrite-analysis/issues/31")
    @Test
    void lambdaNewInstance() {
        //language=java
        rewriteRun(
          java(
            """
              class A {
                  static A newInstance() {
                      return new A();
                  }
              }
              
              class Test {
                  ThreadLocal<A> foobar =
                      ThreadLocal.withInitial(() -> {
                          A a = A.newInstance();
                          return a;
                      });
              }
              """,
            """
              class A {
                  static A newInstance() {
                      return new A();
                  }
              }
              
              class Test {
                  ThreadLocal<A> foobar =
                      ThreadLocal.withInitial(() -> {
                          A a = A.newInstance();
                          return /*~~>*/a;
                      });
              }
              """
          )
        );
    }

    @Disabled("https://github.com/openrewrite/rewrite-analysis/issues/31")
    @Test
    void lambdaReturn() {
        //language=java
        rewriteRun(
          java(
            """
              import java.util.function.Function;
              
              class Test {
                  Function<String, String> foo() {
                      return str -> {
                          String upperStr = str.toUpperCase();
                          return "Hello, " + upperStr;
                      };
                  }
              }
              """,
            """
              import java.util.function.Function;
              
              class Test {
                  Function<String, String> foo() {
                      return str -> {
                          String upperStr = /*~~>*/str.toUpperCase();
                          return "Hello, " + /*~~>*/upperStr;
                      };
                  }
              }
              """
          )
        );
    }
}
