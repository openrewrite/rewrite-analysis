/*
 * Copyright 2025 the original author or authors.
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
import org.openrewrite.Issue;
import org.openrewrite.analysis.trait.expr.Literal;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.test.RewriteTest.toRecipe;

/**
 * Tests for dataflow tracking through anonymous classes.
 * See <a href="https://github.com/openrewrite/rewrite-analysis/issues/93">Issue #93</a>
 */
@SuppressWarnings({
  "Convert2Lambda",
  "Anonymous2MethodRef",
  "FunctionName",
  "UnnecessaryLocalVariable"
})
class FindLocalFlowPathsAnonymousClassTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(toRecipe(() -> new FindLocalFlowPaths<>(new DataFlowSpec() {
            @Override
            public boolean isSource(DataFlowNode srcNode) {
                return srcNode
                  .asExpr(Literal.class)
                  .bind(Literal::getValue)
                  .map("42"::equals)
                  .orSome(false);
            }

            @Override
            public boolean isSink(DataFlowNode sinkNode) {
                return true;
            }
        }))).expectedCyclesThatMakeChanges(1).cycles(1);
    }

    @Test
    void dataflowThroughLambdaExpressionBody() {
        // This test demonstrates that dataflow works correctly through lambda expressions
        // with expression body (no return statement)
        // Note: return f is also marked because f captures the tainted value
        rewriteRun(
          java(
            """
              import java.util.function.Function;
              class Test {
                  Object test() {
                      String o = "42";
                      Function<Object, Object> f = x -> o;
                      return f;
                  }
              }
              """,
            """
              import java.util.function.Function;
              class Test {
                  Object test() {
                      String o = /*~~>*/"42";
                      Function<Object, Object> f = x -> /*~~>*/o;
                      return /*~~>*/f;
                  }
              }
              """
          )
        );
    }

    @Test
    void dataflowThroughLambdaBlockBody() {
        // Test with lambda block body (with return statement)
        // Note: Unlike expression body lambdas, block body lambdas don't propagate
        // the return value flow to the variable assignment (f)
        rewriteRun(
          java(
            """
              import java.util.function.Function;
              class Test {
                  Object test() {
                      String o = "42";
                      Function<Object, Object> f = x -> { return o; };
                      return f;
                  }
              }
              """,
            """
              import java.util.function.Function;
              class Test {
                  Object test() {
                      String o = /*~~>*/"42";
                      Function<Object, Object> f = x -> { return /*~~>*/o; };
                      return f;
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-analysis/issues/93")
    @Test
    void dataflowThroughAnonymousClass() {
        // This test demonstrates that dataflow IS tracked through anonymous classes
        // Note: Similar to lambda block bodies, the return statement inside the anonymous
        // class method doesn't propagate flow to the variable assignment (f)
        rewriteRun(
          java(
            """
              import java.util.function.Function;
              class Test {
                  Object test() {
                      String o = "42";
                      Function<Object, Object> f = new Function<Object, Object>() {
                          public Object apply(Object x) {
                              return o;
                          }
                      };
                      return f;
                  }
              }
              """,
            """
              import java.util.function.Function;
              class Test {
                  Object test() {
                      String o = /*~~>*/"42";
                      Function<Object, Object> f = new Function<Object, Object>() {
                          public Object apply(Object x) {
                              return /*~~>*/o;
                          }
                      };
                      return f;
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-analysis/issues/93")
    @Test
    void dataflowThroughAnonymousClassMultipleReferences() {
        // Test multiple references to the same variable inside anonymous class
        // Both usages of 'o' should be marked as dataflow sinks
        rewriteRun(
          java(
            """
              import java.util.function.Function;
              class Test {
                  Object test() {
                      String o = "42";
                      Function<Object, Object> f = new Function<Object, Object>() {
                          public Object apply(Object x) {
                              System.out.println(o);
                              return o;
                          }
                      };
                      return f;
                  }
              }
              """,
            """
              import java.util.function.Function;
              class Test {
                  Object test() {
                      String o = /*~~>*/"42";
                      Function<Object, Object> f = new Function<Object, Object>() {
                          public Object apply(Object x) {
                              System.out.println(/*~~>*/o);
                              return /*~~>*/o;
                          }
                      };
                      return f;
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-analysis/issues/93")
    @Test
    void dataflowThroughAnonymousClassWithShadowedParameter() {
        // When anonymous class method parameter has the same name as outer variable,
        // the parameter should shadow the outer variable (similar to lambda)
        rewriteRun(
          java(
            """
              import java.util.function.Function;
              class Test {
                  Object test() {
                      String o = "42";
                      Function<Object, Object> f = new Function<Object, Object>() {
                          public Object apply(Object o) {
                              return o;
                          }
                      };
                      return f;
                  }
              }
              """,
            """
              import java.util.function.Function;
              class Test {
                  Object test() {
                      String o = /*~~>*/"42";
                      Function<Object, Object> f = new Function<Object, Object>() {
                          public Object apply(Object o) {
                              return o;
                          }
                      };
                      return f;
                  }
              }
              """
          )
        );
    }

    @Test
    void lambdaWithShadowedParameter() {
        // Baseline: lambda parameter shadowing works correctly
        rewriteRun(
          java(
            """
              import java.util.function.Function;
              class Test {
                  Object test() {
                      String o = "42";
                      Function<Object, Object> f = o -> o;
                      return f;
                  }
              }
              """,
            """
              import java.util.function.Function;
              class Test {
                  Object test() {
                      String o = /*~~>*/"42";
                      Function<Object, Object> f = o -> o;
                      return f;
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-analysis/issues/93")
    @Test
    void dataflowThroughNestedAnonymousClass() {
        // Test dataflow through nested anonymous classes
        rewriteRun(
          java(
            """
              import java.util.function.Function;
              class Test {
                  Object test() {
                      String o = "42";
                      Function<Object, Object> f = new Function<Object, Object>() {
                          public Object apply(Object x) {
                              Function<Object, Object> inner = new Function<Object, Object>() {
                                  public Object apply(Object y) {
                                      return o;
                                  }
                              };
                              return inner;
                          }
                      };
                      return f;
                  }
              }
              """,
            """
              import java.util.function.Function;
              class Test {
                  Object test() {
                      String o = /*~~>*/"42";
                      Function<Object, Object> f = new Function<Object, Object>() {
                          public Object apply(Object x) {
                              Function<Object, Object> inner = new Function<Object, Object>() {
                                  public Object apply(Object y) {
                                      return /*~~>*/o;
                                  }
                              };
                              return inner;
                          }
                      };
                      return f;
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-analysis/issues/93")
    @Test
    void dataflowThroughAnonymousClassAndLambda() {
        // Test dataflow through both anonymous class and lambda
        // Note: 'inner' is marked because lambda expression body (y -> o) propagates flow
        rewriteRun(
          java(
            """
              import java.util.function.Function;
              class Test {
                  Object test() {
                      String o = "42";
                      Function<Object, Object> f = new Function<Object, Object>() {
                          public Object apply(Object x) {
                              Function<Object, Object> inner = y -> o;
                              return inner;
                          }
                      };
                      return f;
                  }
              }
              """,
            """
              import java.util.function.Function;
              class Test {
                  Object test() {
                      String o = /*~~>*/"42";
                      Function<Object, Object> f = new Function<Object, Object>() {
                          public Object apply(Object x) {
                              Function<Object, Object> inner = y -> /*~~>*/o;
                              return /*~~>*/inner;
                          }
                      };
                      return f;
                  }
              }
              """
          )
        );
    }

    @Issue("https://github.com/openrewrite/rewrite-analysis/issues/93")
    @Test
    void dataflowThroughRunnableAnonymousClass() {
        // Test with Runnable which has no parameters
        rewriteRun(
          java(
            """
              class Test {
                  Object test() {
                      String o = "42";
                      Runnable r = new Runnable() {
                          public void run() {
                              System.out.println(o);
                          }
                      };
                      return r;
                  }
              }
              """,
            """
              class Test {
                  Object test() {
                      String o = /*~~>*/"42";
                      Runnable r = new Runnable() {
                          public void run() {
                              System.out.println(/*~~>*/o);
                          }
                      };
                      return r;
                  }
              }
              """
          )
        );
    }
}
