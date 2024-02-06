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

public class ClassInstanceExprTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(toRecipe(() -> new JavaIsoVisitor<>() {
            @Override
            public J preVisit(J tree, ExecutionContext executionContext) {
                return ClassInstanceExpr.viewOf(getCursor())
                  .map(__ -> SearchResult.found(tree))
                  .orSuccess(tree);
            }
        })).cycles(1).expectedCyclesThatMakeChanges(1);
    }

    @DocumentExample
    @Test
    void correctlyLabelsNewClassInstance() {
        rewriteRun(
          java(
            """
              class Foo {
              }
              
              class Test {
                  void test() {
                      Foo foo = new Foo();
                  }
              }
              """,
            """
              class Foo {
              }
              
              class Test {
                  void test() {
                      Foo foo = /*~~>*/new Foo();
                  }
              }
              """
          )
        );
    }

    @Test
    void correctlyLabelsNewClassInstanceInConstructor() {
        rewriteRun(
          java(
            """
              class Foo {
              }
              
              class Bar {
                  Foo foo;
                  Bar(Foo foo) {
                      this.foo = foo;
                  }
              }
              
              class Test {
                  void test() {
                      Bar bar = new Bar(new Foo());
                  }
              }
              """,
            """
              class Foo {
              }
              
              class Bar {
                  Foo foo;
                  Bar(Foo foo) {
                      this.foo = foo;
                  }
              }
              
              class Test {
                  void test() {
                      Bar bar = /*~~>*/new Bar(/*~~>*/new Foo());
                  }
              }
              """
          )
        );
    }

    @Test
    void correctlyLabelsNestedNewClassInstance() {
        rewriteRun(
          java(
            """
              class Foo {
                  class Bar {
                  }
              }
              
              class Test {
                  void test() {
                      Foo foo = new Foo();
                      Foo.Bar bar = foo.new Bar();
                  }
              }
              """,
            """
              class Foo {
                  class Bar {
                  }
              }
              
              class Test {
                  void test() {
                      Foo foo = /*~~>*/new Foo();
                      Foo.Bar bar = /*~~>*/foo.new Bar();
                  }
              }
              """
          )
        );
    }
}
