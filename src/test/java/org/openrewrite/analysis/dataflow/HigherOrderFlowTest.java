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
import org.openrewrite.DocumentExample;
import org.openrewrite.analysis.trait.expr.MethodAccess;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.test.RewriteTest.toRecipe;

/**
 * Higher-order ("lambda call") data flow: flow into a lambda argument's parameter and out of its
 * return value, driven by the CodeQL-derived callback models in {@code model.csv}.
 */
@SuppressWarnings("FunctionName")
class HigherOrderFlowTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(toRecipe(() -> new FindLocalFlowPaths<>(new TaintFlowSpec() {
            @Override
            public boolean isSource(DataFlowNode srcNode) {
                return srcNode
                  .asExpr(MethodAccess.class)
                  .map(MethodAccess::getSimpleName)
                  .map("source"::equals)
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
    void outOfCallbackComputeIfAbsentExpressionBody() {
        rewriteRun(
          java(
            """
              import java.util.Map;
              class Test {
                  String source() { return null; }
                  void test(Map<String, String> map) {
                      String v = map.computeIfAbsent("k", key -> source());
                      System.out.println(v);
                  }
              }
              """,
            """
              import java.util.Map;
              class Test {
                  String source() { return null; }
                  void test(Map<String, String> map) {
                      String v = /*~~>*/map.computeIfAbsent("k", key -> /*~~>*/source());
                      System.out.println(/*~~>*/v);
                  }
              }
              """
          )
        );
    }

    @Test
    void outOfCallbackComputeIfAbsentResultFlowsOnward() {
        // Map.computeIfAbsent has two OUT callback models (the function's return value flows to both
        // the call result and into the map's values). Both match argument 1, so the engine must pick
        // the call-result edge deterministically and then keep following the flow through the
        // subsequent statements; routing to the qualifier instead would dead-end here.
        rewriteRun(
          java(
            """
              import java.util.Map;
              class Test {
                  String source() { return null; }
                  void sink(Object o) {}
                  void test(Map<String, String> map) {
                      String name = map.computeIfAbsent("k", key -> source());
                      String alias = name;
                      sink(alias);
                  }
              }
              """,
            """
              import java.util.Map;
              class Test {
                  String source() { return null; }
                  void sink(Object o) {}
                  void test(Map<String, String> map) {
                      String name = /*~~>*/map.computeIfAbsent("k", key -> /*~~>*/source());
                      String alias = /*~~>*/name;
                      sink(/*~~>*/alias);
                  }
              }
              """
          )
        );
    }

    @Test
    void outOfCallbackComputeIfAbsentBlockBodyIsLimited() {
        // Known limitation: when the source sits inside a *block-body* lambda, control-flow
        // reachability is scoped to the lambda body (a block body forms its own control-flow
        // region), so flow does not escape through the lambda's `return` to the enclosing call
        // result. Expression-body lambdas (see above) are not subject to this. Lifting it requires
        // reworking how a lambda's `return` reachability connects to the enclosing flow, which has
        // subtle interactions with the existing expression-body behavior. Mirrors the block-body
        // limitation documented in FindLocalFlowPathsAnonymousClassTest.
        rewriteRun(
          java(
            """
              import java.util.Map;
              class Test {
                  String source() { return null; }
                  void test(Map<String, String> map) {
                      String v = map.computeIfAbsent("k", key -> {
                          return source();
                      });
                      System.out.println(v);
                  }
              }
              """,
            """
              import java.util.Map;
              class Test {
                  String source() { return null; }
                  void test(Map<String, String> map) {
                      String v = map.computeIfAbsent("k", key -> {
                          return /*~~>*/source();
                      });
                      System.out.println(v);
                  }
              }
              """
          )
        );
    }

    @Test
    void intoCallbackForEachBlockBody() {
        rewriteRun(
          java(
            """
              import java.util.List;
              class Test {
                  List<String> source() { return null; }
                  void sink(Object o) {}
                  void test() {
                      List<String> list = source();
                      list.forEach(x -> {
                          sink(x);
                      });
                  }
              }
              """,
            """
              import java.util.List;
              class Test {
                  List<String> source() { return null; }
                  void sink(Object o) {}
                  void test() {
                      List<String> list = /*~~>*/source();
                      /*~~>*/list.forEach(/*~~>*/x -> {
                          sink(/*~~>*/x);
                      });
                  }
              }
              """
          )
        );
    }

    @Test
    void intoCallbackForEach() {
        rewriteRun(
          java(
            """
              import java.util.List;
              class Test {
                  List<String> source() { return null; }
                  void test() {
                      List<String> list = source();
                      list.forEach(x -> System.out.println(x));
                  }
              }
              """,
            """
              import java.util.List;
              class Test {
                  List<String> source() { return null; }
                  void test() {
                      List<String> list = /*~~>*/source();
                      /*~~>*/list.forEach(/*~~>*/x -> System.out.println(/*~~>*/x));
                  }
              }
              """
          )
        );
    }
}
