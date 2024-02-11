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
package org.openrewrite.analysis.dataflow.global;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.analysis.InvocationMatcher;
import org.openrewrite.analysis.dataflow.DataFlowNode;
import org.openrewrite.analysis.dataflow.TaintFlowSpec;
import org.openrewrite.analysis.trait.expr.Literal;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class GlobalTaintFlowTest implements RewriteTest {
    static final TaintFlowSpec TAINT_FLOW_SPEC = new TaintFlowSpec() {
        private static final InvocationMatcher SYSTEM_OUT_PRINTLN = InvocationMatcher.fromMethodMatcher(
          "java.io.PrintStream println(..)"
        );

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
            return SYSTEM_OUT_PRINTLN.advanced().isAnyArgument(sinkNode.getCursor());
        }
    };

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipe(new MockGlobalDataFlowRecipe(TAINT_FLOW_SPEC))
          .expectedCyclesThatMakeChanges(1)
          .cycles(1);
    }

    @DocumentExample
    @Test
    void normalTaintFlowStillWorks() {
        rewriteRun(
          java(
            """
              class Test {
                  void test() {
                      String s = "42";
                      String t = s.substring(1);
                      System.out.println(t);
                  }
              }
              """,
            """
              class Test {
                  void test() {
                      String s = /*~~(source)~~>*/"42";
                      String t = /*~~>*//*~~>*/s.substring(1);
                      System.out.println(/*~~(sink)~~>*/t);
                  }
              }
              """
          )
        );
    }

    @Test
    void simpleStringAppend() {
        rewriteRun(
          java(
            """
              class Test {
                  String stringAppend(String s) {
                      return "Value: " + s;
                  }
                  
                  void test() {
                      String s = "42";
                      String t = stringAppend(s);
                      System.out.println(t);
                  }
              }
              """,
            """
              class Test {
                  String stringAppend(String /*~~>*/s) {
                      return /*~~>*/"Value: " + /*~~>*/s;
                  }
                  
                  void test() {
                      String s = /*~~(source)~~>*/"42";
                      String t = /*~~>*/stringAppend(/*~~>*/s);
                      System.out.println(/*~~(sink)~~>*/t);
                  }
              }
              """
          )
        );
    }

    @Test
    void simpleSubstring() {
        rewriteRun(
          java(
            """
              import java.util.Objects;
                            
              class Test {
                  String stringSubstring(String s) {
                      return Objects.requireNonNull(s.substring(1));
                  }
                  
                  void test() {
                      String s = stringSubstring("42");
                      System.out.println(s);
                  }
              }
              """,
            """
              import java.util.Objects;
                            
              class Test {
                  String stringSubstring(String /*~~>*/s) {
                      return /*~~>*/Objects.requireNonNull(/*~~>*//*~~>*/s.substring(1));
                  }
                  
                  void test() {
                      String s = /*~~>*/stringSubstring(/*~~(source)~~>*/"42");
                      System.out.println(/*~~(sink)~~>*/s);
                  }
              }
              """
          )
        );
    }

    @Test
    void fizBuzz() {
        rewriteRun(
          java(
            """
              class Test {
                            
                  String stringFizzBuzz(String number) {
                      int n = Integer.parseInt(number);
                      if (n % 15 == 0) {
                          return "Value: FizzBuzz";
                      } else if (n % 3 == 0) {
                          return "Value: Fizz";
                      } else if (n % 5 == 0) {
                          return "Value: Buzz";
                      } else {
                          return "Value: " + number;
                      }
                  }
                  
                  void test() {
                      String s = stringFizzBuzz("42");
                      System.out.println(s);
                  }
              }
              """,
            """
              class Test {
                            
                  String stringFizzBuzz(String /*~~>*/number) {
                      int n = Integer.parseInt(/*~~>*/number);
                      if (n % 15 == 0) {
                          return "Value: FizzBuzz";
                      } else if (n % 3 == 0) {
                          return "Value: Fizz";
                      } else if (n % 5 == 0) {
                          return "Value: Buzz";
                      } else {
                          return /*~~>*/"Value: " + /*~~>*/number;
                      }
                  }
                  
                  void test() {
                      String s = /*~~>*/stringFizzBuzz(/*~~(source)~~>*/"42");
                      System.out.println(/*~~(sink)~~>*/s);
                  }
              }
              """
          )
        );
    }

    @Test
    @Disabled("Not getting flow out of the inner recursive function")
    void recursiveFunctionWithRecursivePath() {
        rewriteRun(
          java(
            """
              class Test {
                            
                  String recursive(String parameterS) {
                      if (parameterS.length() > 0) {
                          return recursive(parameterS.substring(1));
                      }
                      return parameterS;
                  }
                  
                  void test() {
                      String s = "42";
                      String t = recursive(s);
                      System.out.println(t);
                  }
              }
              """,
            """
              class Test {
                            
                  String recursive(String /*~~>*/parameterS) {
                      if (/*~~>*/parameterS.length() > 0) {
                          return /*~~>*/recursive(/*~~>*//*~~>*/parameterS.substring(1));
                      }
                      return /*~~>*/parameterS;
                  }
                  
                  void test() {
                      String s = /*~~(source)~~>*/"42";
                      String t = /*~~>*/recursive(/*~~>*/s);
                      System.out.println(/*~~(sink)~~>*/t);
                  }
              }
              """
          )
        );
    }
}
