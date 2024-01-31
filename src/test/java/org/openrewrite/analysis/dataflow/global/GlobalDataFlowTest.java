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
import org.openrewrite.analysis.InvocationMatcher;
import org.openrewrite.analysis.dataflow.DataFlowNode;
import org.openrewrite.analysis.dataflow.DataFlowSpec;
import org.openrewrite.analysis.trait.expr.Literal;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings("ObviousNullCheck")
public class GlobalDataFlowTest implements RewriteTest {

    static final DataFlowSpec DATA_FLOW_SPEC = new DataFlowSpec() {
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
          .recipe(new MockGlobalDataFlowRecipe(DATA_FLOW_SPEC))
          .expectedCyclesThatMakeChanges(1)
          .cycles(1);
    }

    @Test
    void identityFunction() {
        rewriteRun(
          java(
            """
              class Test {
                            
                  String identity(String s) {
                      return s;
                  }
                  
                  void test() {
                      String s = "42";
                      String t = identity(s);
                      System.out.println(t);
                  }
              }
              """,
            """
              class Test {
                            
                  String identity(String /*~~>*/s) {
                      return /*~~>*/s;
                  }
                  
                  void test() {
                      String s = /*~~(source)~~>*/"42";
                      String t = /*~~>*/identity(/*~~>*/s);
                      System.out.println(/*~~(sink)~~>*/t);
                  }
              }
              """
          )
        );
    }

    @Test
    void polymorphicIdentityFunction() {
        rewriteRun(
          java(
            """
              class Test {
                            
                  Object identity(Object s) {
                      return s;
                  }
                  
                  void test() {
                      String s = "42";
                      Object t = identity(s);
                      System.out.println(t);
                  }
              }
              """,
            """
              class Test {
                            
                  Object identity(Object /*~~>*/s) {
                      return /*~~>*/s;
                  }
                  
                  void test() {
                      String s = /*~~(source)~~>*/"42";
                      Object t = /*~~>*/identity(/*~~>*/s);
                      System.out.println(/*~~(sink)~~>*/t);
                  }
              }
              """
          )
        );
    }

    @Test
    void identityFunctionReversedOrder() {
        rewriteRun(
          java(
            """
              class Test {
                  
                  void test() {
                      String s = "42";
                      String t = identity(s);
                      System.out.println(t);
                  }
                  
                  String identity(String s) {
                      return s;
                  }
              }
              """,
            """
              class Test {
                  
                  void test() {
                      String s = /*~~(source)~~>*/"42";
                      String t = /*~~>*/identity(/*~~>*/s);
                      System.out.println(/*~~(sink)~~>*/t);
                  }
                  
                  String identity(String /*~~>*/s) {
                      return /*~~>*/s;
                  }
              }
              """
          )
        );
    }

    @Test
    void noOpFunction() {
        rewriteRun(
          spec -> spec.expectedCyclesThatMakeChanges(0),
          java(
            """
              class Test {
                            
                  String noOp(String s) {
                      return "something else";
                  }
                  
                  void test() {
                      String s = "42";
                      String t = noOp(s);
                      System.out.println(t);
                  }
              }
              """
          )
        );
    }

    @Test
    void printFunction() {
        rewriteRun(
          java(
            """
              class Test {
                            
                  void print(String s) {
                      System.out.println(s);
                  }
                  
                  void test() {
                      String s = "42";
                      print(s);
                  }
              }
              """,
            """
              class Test {
                            
                  void print(String /*~~>*/s) {
                      System.out.println(/*~~(sink)~~>*/s);
                  }
                  
                  void test() {
                      String s = /*~~(source)~~>*/"42";
                      print(/*~~>*/s);
                  }
              }
              """
          )
        );
    }

    @Test
    void recursiveFunctionWithoutRecursivePath() {
        rewriteRun(
          java(
            """
              class Test {
                            
                  String recursive(String s) {
                      if(s.length() > 0) {
                          return recursive(s.substring(1));
                      }
                      return s;
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
                            
                  String recursive(String /*~~>*/s) {
                      if(/*~~>*/s.length() > 0) {
                          return recursive(s.substring(1));
                      }
                      return /*~~>*/s;
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

    @Test
    void stringNullCheck() {
        rewriteRun(
          java(
            """
              class Test {
                  static String requireNonNull(String obj) {
                      if (obj == null)
                          throw new NullPointerException();
                      return obj;
                  }
                  
                  void test() {
                      String s = requireNonNull("0");
                      System.out.println(s);
                      String t = requireNonNull("42");
                      System.out.println(t);
                      String u = requireNonNull("0");
                      System.out.println(u);
                  }
              }
              """,
            """
              class Test {
                  static String requireNonNull(String /*~~>*/obj) {
                      if (/*~~>*/obj == null)
                          throw new NullPointerException();
                      return /*~~>*/obj;
                  }
                  
                  void test() {
                      String s = requireNonNull("0");
                      System.out.println(s);
                      String t = /*~~>*/requireNonNull(/*~~(source)~~>*/"42");
                      System.out.println(/*~~(sink)~~>*/t);
                      String u = requireNonNull("0");
                      System.out.println(u);
                  }
              }
              """
          )
        );
    }

    @Test
    void genericNullCheck() {
        rewriteRun(
          java(
            """
              class Test {
                  static <T> T requireNonNull(T obj) {
                      if (obj == null)
                          throw new NullPointerException();
                      return obj;
                  }
                  
                  void test() {
                      String s = requireNonNull("0");
                      System.out.println(s);
                      String t = requireNonNull("42");
                      System.out.println(t);
                      String u = requireNonNull("0");
                      System.out.println(u);
                  }
              }
              """,
            """
              class Test {
                  static <T> T requireNonNull(T /*~~>*/obj) {
                      if (/*~~>*/obj == null)
                          throw new NullPointerException();
                      return /*~~>*/obj;
                  }
                  
                  void test() {
                      String s = requireNonNull("0");
                      System.out.println(s);
                      String t = /*~~>*/requireNonNull(/*~~(source)~~>*/"42");
                      System.out.println(/*~~(sink)~~>*/t);
                      String u = requireNonNull("0");
                      System.out.println(u);
                  }
              }
              """
          )
        );
    }

    @Test
    void multiFileNullCheck() {
        rewriteRun(
          java(
            """
              class Test {
                  void test() {
                      String s = Util.requireNonNull("0");
                      System.out.println(s);
                      String t = Util.requireNonNull("42");
                      System.out.println(t);
                      String u = Util.requireNonNull("0");
                      System.out.println(u);
                  }
              }
              """,
            """
              class Test {
                  void test() {
                      String s = Util.requireNonNull("0");
                      System.out.println(s);
                      String t = /*~~>*/Util.requireNonNull(/*~~(source)~~>*/"42");
                      System.out.println(/*~~(sink)~~>*/t);
                      String u = Util.requireNonNull("0");
                      System.out.println(u);
                  }
              }
              """
          ),
          java(
            """
              class Util {
                  static String requireNonNull(String obj) {
                      if (obj == null)
                          throw new NullPointerException();
                      return obj;
                  }
              }
              """,
            """
              class Util {
                  static String requireNonNull(String /*~~>*/obj) {
                      if (/*~~>*/obj == null)
                          throw new NullPointerException();
                      return /*~~>*/obj;
                  }
              }
              """
          )
        );
    }

    @Test
    void polymorphicDataFlowThroughSuperclasses() {
        rewriteRun(
          java(
            """
              class Test {
                  abstract static class Abstract {
                      String identity(String obj) {
                          return obj;
                      }
                  }
                  
                  static class Concrete extends Abstract { }
                  
                  void test() {
                      Abstract a = new Concrete();
                      String s = a.identity("42");
                      System.out.println(s);
                      Concrete c = new Concrete();
                      String t = c.identity("42");
                      System.out.println(t);
                  }
              }
              """,
            """
              class Test {
                  abstract static class Abstract {
                      String identity(String /*~~>*/obj) {
                          return /*~~>*/obj;
                      }
                  }
                  
                  static class Concrete extends Abstract { }
                  
                  void test() {
                      Abstract a = new Concrete();
                      String s = /*~~>*/a.identity(/*~~(source)~~>*/"42");
                      System.out.println(/*~~(sink)~~>*/s);
                      Concrete c = new Concrete();
                      String t = /*~~>*/c.identity(/*~~(source)~~>*/"42");
                      System.out.println(/*~~(sink)~~>*/t);
                  }
              }
              """
          )
        );
    }

    @Test
    void polymorphicDataFlowThroughSuperclassesWithOverride() {
        rewriteRun(
          java(
            """
              class Test {
                  abstract static class Abstract {
                      String identity(String obj) {
                          return obj;
                      }
                  }
                  
                  static class Concrete extends Abstract {
                      @Override
                      String identity(String obj) {
                          return super.identity(obj);
                      }
                  }
                  
                  void test() {
                      Abstract a = new Concrete();
                      String s = a.identity("42");
                      System.out.println(s);
                      Concrete c = new Concrete();
                      String t = c.identity("42");
                      System.out.println(t);
                  }
              }
              """,
            """
              class Test {
                  abstract static class Abstract {
                      String identity(String /*~~>*/obj) {
                          return /*~~>*/obj;
                      }
                  }
                  
                  static class Concrete extends Abstract {
                      @Override
                      String identity(String /*~~>*/obj) {
                          return /*~~>*/super.identity(/*~~>*/obj);
                      }
                  }
                  
                  void test() {
                      Abstract a = new Concrete();
                      String s = /*~~>*/a.identity(/*~~(source)~~>*/"42");
                      System.out.println(/*~~(sink)~~>*/s);
                      Concrete c = new Concrete();
                      String t = /*~~>*/c.identity(/*~~(source)~~>*/"42");
                      System.out.println(/*~~(sink)~~>*/t);
                  }
              }
              """
          )
        );
    }

    @Test
    @Disabled("Need to support resolving the correct JavaType.Method for the given call site")
    void polymorphicDataFlowThroughSuperclassesWithOverrideCalledOnSubtype() {
        rewriteRun(
          java(
            """
              class Test {
                  abstract static class Abstract {
                      abstract String identity(String obj);
                  }
                  
                  static class Concrete extends Abstract {
                      @Override
                      String identity(String obj) {
                          return obj;
                      }
                  }
                  
                  void test() {
                      Abstract a = new Concrete();
                      String s = a.identity("42");
                      System.out.println(s);
                  }
              }
              """,
            """
              class Test {
                  abstract static class Abstract {
                      abstract String identity(String obj);
                  }
                  
                  static class Concrete extends Abstract {
                      @Override
                      String identity(String /*~~>*/obj) {
                          return /*~~>*/obj;
                      }
                  }
                  
                  void test() {
                      Abstract a = new Concrete();
                      String s = /*~~>*/a.identity(/*~~(source)~~>*/"42");
                      System.out.println(/*~~(sink)~~>*/s);
                  }
              }
              """
          )
        );
    }
}
