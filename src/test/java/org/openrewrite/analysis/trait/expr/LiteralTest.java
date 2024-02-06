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

public class LiteralTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(toRecipe(() -> new JavaIsoVisitor<>() {
            @Override
            public J preVisit(J tree, ExecutionContext executionContext) {
                return Literal.viewOf(getCursor())
                  .map(l -> {
                      assert l.getValue().isSome();
                      return SearchResult.found(tree, l.getValue().some().toString());
                  })
                  .orSuccess(tree);
            }
        })).cycles(1).expectedCyclesThatMakeChanges(1);
    }

    @DocumentExample
    @Test
    void correctlyLabelsLiterals() {
        rewriteRun(
          java(
            """
              class Test {
                  void test() {
                      String i = "abc";
                      int j = 16;
                  }
              }
              """,
            """
              class Test {
                  void test() {
                      String i = /*~~(abc)~~>*/"abc";
                      int j = /*~~(16)~~>*/16;
                  }
              }
              """
          )
        );
    }

    @Test
    void correctlyLabelsLiteralsAsArguments() {
        rewriteRun(
          java(
            """
              class Test {
                  void foo(char c, int i) {
                  }
                  
                  void test() {
                      foo('c', 5);
                  }
              }
              """,
            """
              class Test {
                  void foo(char c, int i) {
                  }
                  
                  void test() {
                      foo(/*~~(c)~~>*/'c', /*~~(5)~~>*/5);
                  }
              }
              """
          )
        );
    }

    @Test
    void correctlyLabelsLiteralsInArithmeticAssignmentOperators() {
        rewriteRun(
          java(
            """
              class Test {
                  void test(int i) {
                      i += 5;
                      i -= 2;
                      i *= 'c';
                  }
              }
              """,
            """
              class Test {
                  void test(int i) {
                      i += /*~~(5)~~>*/5;
                      i -= /*~~(2)~~>*/2;
                      i *= /*~~(c)~~>*/'c';
                  }
              }
              """
          )
        );
    }

    @Test
    void correctlyLabelsLiteralsOnCompileTimeConstants() {
        rewriteRun(
          java(
            """
              class Test {
                  private static final int FOO = 1;
                  private static final String BAR = "bar";
                  
                  void test() {
                      int i = FOO;
                      String s = BAR;
                  }
              }
              """,
            """
              class Test {
                  private static final int FOO = /*~~(1)~~>*/1;
                  private static final String BAR = /*~~(bar)~~>*/"bar";
                  
                  void test() {
                      int i = FOO;
                      String s = BAR;
                  }
              }
              """
          )
        );
    }

    @Test
    void correctlyLabelsLiteralInReturn() {
        rewriteRun(
          java(
            """
              class Test {
                  boolean foo() {
                      return false;
                  }
                  
                  void test() {
                      if (foo()) {
                          String s = "string";
                      }
                  }
              }
              """,
            """
              class Test {
                  boolean foo() {
                      return /*~~(false)~~>*/false;
                  }
                  
                  void test() {
                      if (foo()) {
                          String s = /*~~(string)~~>*/"string";
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void correctlyLabelsLiteralInTernary() {
        rewriteRun(
          java(
            """
              class Test {
                  void test(char c) {
                      String s = c == 'a' ? "string" : "other";
                  }
              }
              """,
            """
              class Test {
                  void test(char c) {
                      String s = c == /*~~(a)~~>*/'a' ? /*~~(string)~~>*/"string" : /*~~(other)~~>*/"other";
                  }
              }
              """
          )
        );
    }

    @Test
    void correctlyLabelsLiteralInExpression() {
        rewriteRun(
          java(
            """
              class Test {
                  void test(int i) {
                      char c = 16 * (2 + 'a') - i - 5;
                      c -= 10;
                  }
              }
              """,
            """
              class Test {
                  void test(int i) {
                      char c = /*~~(16)~~>*/16 * (/*~~(2)~~>*/2 + /*~~(a)~~>*/'a') - i - /*~~(5)~~>*/5;
                      c -= /*~~(10)~~>*/10;
                  }
              }
              """
          )
        );
    }

    @Test
    void correctlyLabelsLiteralOnMethodCall() {
        rewriteRun(
          java(
            """
              class Test {
                  void test() {
                      boolean b1 = "string".length() == 5;
                      boolean b2 = "surprise".contains("s");
                  }
              }
              """,
            """
              class Test {
                  void test() {
                      boolean b1 = /*~~(string)~~>*/"string".length() == /*~~(5)~~>*/5;
                      boolean b2 = /*~~(surprise)~~>*/"surprise".contains(/*~~(s)~~>*/"s");
                  }
              }
              """
          )
        );
    }

    @Test
    void noLiteralsPresent() {
        rewriteRun(
          spec -> spec.expectedCyclesThatMakeChanges(0),
          java(
            """
              class Test {
                  boolean test(int i, float f, double d, char c, String s) {
                      boolean b1 = s.contains(Character.toString(c));
                      if (b1 && i - f == d) {
                          return i > d;
                      } else {
                          return i > f;
                      }
                  }
              }
              """
          )
        );
    }
}
