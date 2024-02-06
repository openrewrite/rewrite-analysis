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

public class MethodAccessTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(toRecipe(() -> new JavaIsoVisitor<>() {
            @Override
            public J preVisit(J tree, ExecutionContext executionContext) {
                return MethodAccess.viewOf(getCursor())
                  .map(ma -> SearchResult.found(tree, "name: " + ma.getSimpleName()))
                  .orSuccess(tree);
            }
        })).cycles(1).expectedCyclesThatMakeChanges(1);
    }

    @DocumentExample
    @Test
    void correctlyLabelsMethodAccess() {
        rewriteRun(
          java(
            """
              class Test {
                  void foo() {
                      String i = "abc";
                      int j = 16;
                  }
                  
                  void test() {
                      foo();
                  }
              }
              """,
            """
              class Test {
                  void foo() {
                      String i = "abc";
                      int j = 16;
                  }
                  
                  void test() {
                      /*~~(name: foo)~~>*/foo();
                  }
              }
              """
          )
        );
    }

    @Test
    void correctlyLabelsOverloadedMethodAccess() {
        rewriteRun(
          java(
            """
              class Test {
                  void bar(int i) {
                      i += 3;
                  }
                  
                  void bar(String s) {
                      s += "!";
                  }
                  
                  void test(int i, String s) {
                      bar(i);
                      bar(s);
                  }
              }
              """,
            """
              class Test {
                  void bar(int i) {
                      i += 3;
                  }
                  
                  void bar(String s) {
                      s += "!";
                  }
                  
                  void test(int i, String s) {
                      /*~~(name: bar)~~>*/bar(i);
                      /*~~(name: bar)~~>*/bar(s);
                  }
              }
              """
          )
        );
    }

    @Test
    void correctlyLabelsExternalMethodAccess() {
        rewriteRun(
          java(
            """
              class Foo {
                  String baz() {
                      return "baz";
                  }
              }
              
              class Test {
                  void test() {
                      System.out.println(new Foo().baz());
                  }
              }
              """,
            """
              class Foo {
                  String baz() {
                      return "baz";
                  }
              }
              
              class Test {
                  void test() {
                      /*~~(name: println)~~>*/System.out.println(/*~~(name: baz)~~>*/new Foo().baz());
                  }
              }
              """
          )
        );
    }

    @Test
    void correctlyLabelsStaticMethodAccess() {
        rewriteRun(
          java(
            """
              class Bar {
                  static void foo() {
                  }
              }
              
              class Test {
                  void test() {
                      Bar.foo();
                  }
              }
              """,
            """
              class Bar {
                  static void foo() {
                  }
              }
              
              class Test {
                  void test() {
                      /*~~(name: foo)~~>*/Bar.foo();
                  }
              }
              """
          )
        );
    }

    @Test
    void correctlyLabelsSuperMethodAccess() {
        rewriteRun(
          java(
            """
              class Foo {
                  void ex() {
                      int i = 1;
                  }
              }
              
              class Bar extends Foo {
                  void ex() {
                      int i = 2;
                      super.ex();
                  }
              }
              
              class Test {
                  void test() {
                      new Bar().ex();
                  }
              }
              """,
            """
              class Foo {
                  void ex() {
                      int i = 1;
                  }
              }
              
              class Bar extends Foo {
                  void ex() {
                      int i = 2;
                      /*~~(name: ex)~~>*/super.ex();
                  }
              }
              
              class Test {
                  void test() {
                      /*~~(name: ex)~~>*/new Bar().ex();
                  }
              }
              """
          )
        );
    }

    @Test
    void noMethodAccess() {
        rewriteRun(
          spec -> spec.expectedCyclesThatMakeChanges(0),
          java(
            """
              class Test {
                  void unusedFunction() {
                  }
                  
                  void test() {
                      int i = 16;
                      i += 2;
                  }
              }
              """
          )
        );
    }
}
