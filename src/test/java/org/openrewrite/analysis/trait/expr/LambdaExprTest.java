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
import org.openrewrite.analysis.trait.member.Method;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.test.RewriteTest.toRecipe;

class LambdaExprTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(toRecipe(() -> new JavaIsoVisitor<>() {
            @Override
            public J preVisit(J tree, ExecutionContext executionContext) {
                return LambdaExpr.viewOf(getCursor())
                  .map(__ -> SearchResult.found(tree))
                  .orSuccess(tree);
            }
        })).cycles(1).expectedCyclesThatMakeChanges(1);
    }

    @DocumentExample
    @Test
    void correctlyLabelsLambda() {
        rewriteRun(
          java(
            """
              interface Fun {
                  String message();
              }
              
              class Test {
                  void test() {
                      Fun foobar = () -> "foobar";
                  }
              }
              """,
            """
              interface Fun {
                  String message();
              }
              
              class Test {
                  void test() {
                      Fun foobar = /*~~>*/() -> "foobar";
                  }
              }
              """
          )
        );
    }

    @Test
    void correctlyLabelsLambdaInReturn() {
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
                      return /*~~>*/str -> {
                          String upperStr = str.toUpperCase();
                          return "Hello, " + upperStr;
                      };
                  }
              }
              """
          )
        );
    }

    @Test
    void correctlyLabelsLambdaInConstructor() {
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
                      FutureTask<Exception> task = new FutureTask<>(/*~~>*/() -> {
                          try {
                              return null;
                          } catch (Exception e) {
                              return e;
                          }
                      });
                  }
              }
              """
          )
        );
    }

    @Test
    void asMethodResolvesFunctionalInterfaceMethod() {
        // LambdaExpr.asMethod() models the lambda's implicit method as the single abstract method
        // of its functional interface (here Function.apply), whose parameters are the lambda's.
        rewriteRun(
          spec -> spec.recipe(toRecipe(() -> new JavaIsoVisitor<ExecutionContext>() {
              @Override
              public J preVisit(J tree, ExecutionContext ctx) {
                  return LambdaExpr.viewOf(getCursor()).map(lambda -> {
                      Method method = lambda.asMethod();
                      assertThat(method.getName()).as("SAM name").isEqualTo("apply");
                      assertThat(method.getParameters()).singleElement().satisfies(p -> {
                          assertThat(p.getName()).isEqualTo("s");
                          assertThat(p.getPosition()).isZero();
                          assertThat(p.getCallable()).isEqualTo(method);
                      });
                      return (J) SearchResult.found(tree);
                  }).orSuccess(tree);
              }
          })).cycles(1).expectedCyclesThatMakeChanges(1),
          java(
            """
              import java.util.function.Function;

              class Test {
                  void test() {
                      Function<String, String> f = s -> s;
                  }
              }
              """,
            """
              import java.util.function.Function;

              class Test {
                  void test() {
                      Function<String, String> f = /*~~>*/s -> s;
                  }
              }
              """
          )
        );
    }

    @Test
    void asMethodResolvesMultiParameterFunctionalInterface() {
        rewriteRun(
          spec -> spec.recipe(toRecipe(() -> new JavaIsoVisitor<ExecutionContext>() {
              @Override
              public J preVisit(J tree, ExecutionContext ctx) {
                  return LambdaExpr.viewOf(getCursor()).map(lambda -> {
                      Method method = lambda.asMethod();
                      assertThat(method.getName()).as("SAM name").isEqualTo("combine");
                      assertThat(method.getParameters()).hasSize(2);
                      assertThat(method.getParameters().get(0).getName()).isEqualTo("a");
                      assertThat(method.getParameters().get(0).getPosition()).isZero();
                      assertThat(method.getParameters().get(1).getName()).isEqualTo("b");
                      assertThat(method.getParameters().get(1).getPosition()).isEqualTo(1);
                      return (J) SearchResult.found(tree);
                  }).orSuccess(tree);
              }
          })).cycles(1).expectedCyclesThatMakeChanges(1),
          java(
            """
              interface Two {
                  String combine(String a, String b);
              }

              class Test {
                  void test() {
                      Two t = (a, b) -> a + b;
                  }
              }
              """,
            """
              interface Two {
                  String combine(String a, String b);
              }

              class Test {
                  void test() {
                      Two t = /*~~>*/(a, b) -> a + b;
                  }
              }
              """
          )
        );
    }

    @Test
    void correctlyLabelsNestedLambda() {
        rewriteRun(
          java(
            """
              import java.util.function.IntFunction;
              import java.util.function.IntPredicate;
              
              class Test {
                  void test() {
                      IntFunction<IntPredicate> foo = a -> b -> a % b == 0;
                  }
              }
              """,
            """
              import java.util.function.IntFunction;
              import java.util.function.IntPredicate;
              
              class Test {
                  void test() {
                      IntFunction<IntPredicate> foo = /*~~>*/a -> /*~~>*/b -> a % b == 0;
                  }
              }
              """
          )
        );
    }
}
