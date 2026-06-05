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
 * Taint flow through container contents. CodeQL models container reads/writes with a content
 * component ({@code Element}, {@code MapValue}, …); this content-insensitive engine collapses that
 * component onto the container, so storing a tainted value into a container taints the whole
 * container and reading any value back out is tainted.
 */
@SuppressWarnings({"FunctionName", "UnusedAssignment"})
class ContentFlowTest implements RewriteTest {
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
    void collectionElementAddThenGet() {
        // Collection.add: `Argument[0] -> Argument[this].Element` collapses to `Argument[0] -> Argument[this]`
        // (tainting the whole list); List.get: `Argument[this].Element -> ReturnValue` collapses to a read.
        rewriteRun(
          java(
            """
              import java.util.*;
              class Test {
                  String source() { return null; }
                  void sink(Object o) {}
                  void test() {
                      List<String> list = new ArrayList<>();
                      list.add(source());
                      String x = list.get(0);
                      sink(x);
                  }
              }
              """,
            """
              import java.util.*;
              class Test {
                  String source() { return null; }
                  void sink(Object o) {}
                  void test() {
                      List<String> list = new ArrayList<>();
                      /*~~>*/list.add(/*~~>*/source());
                      String x = /*~~>*//*~~>*/list.get(0);
                      sink(/*~~>*/x);
                  }
              }
              """
          )
        );
    }

    @Test
    void mapValueReadFromTaintedMap() {
        // Map.get: `Argument[this].MapValue -> ReturnValue` collapses to `Argument[this] -> ReturnValue`,
        // so reading any value out of a tainted map is tainted.
        rewriteRun(
          java(
            """
              import java.util.*;
              class Test {
                  Map<String, String> source() { return null; }
                  void sink(Object o) {}
                  void test() {
                      Map<String, String> map = source();
                      String v = map.get("k");
                      sink(v);
                  }
              }
              """,
            """
              import java.util.*;
              class Test {
                  Map<String, String> source() { return null; }
                  void sink(Object o) {}
                  void test() {
                      Map<String, String> map = /*~~>*/source();
                      String v = /*~~>*//*~~>*/map.get("k");
                      sink(/*~~>*/v);
                  }
              }
              """
          )
        );
    }

    @Test
    void optionalOfThenGet() {
        // Optional contents are modeled as `Element`; Optional.of stores, Optional.get reads.
        rewriteRun(
          java(
            """
              import java.util.*;
              class Test {
                  String source() { return null; }
                  void sink(Object o) {}
                  void test() {
                      Optional<String> o = Optional.of(source());
                      String x = o.get();
                      sink(x);
                  }
              }
              """,
            """
              import java.util.*;
              class Test {
                  String source() { return null; }
                  void sink(Object o) {}
                  void test() {
                      Optional<String> o = /*~~>*/Optional.of(/*~~>*/source());
                      String x = /*~~>*//*~~>*/o.get();
                      sink(/*~~>*/x);
                  }
              }
              """
          )
        );
    }
}
