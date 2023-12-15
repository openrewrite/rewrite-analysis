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
package org.openrewrite.analysis.util;

import org.junit.jupiter.api.Test;
import org.openrewrite.ExecutionContext;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.test.RewriteTest;

import java.util.Collection;
import java.util.function.Supplier;

import static org.openrewrite.java.Assertions.java;

public class CoordinateLocatorTest implements RewriteTest {

    private static Supplier<TreeVisitor<?, ExecutionContext>> locateCoordinate(int line, int column) {
        return () -> new JavaIsoVisitor<>() {

            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext executionContext) {
                J found = CoordinateLocator.findCoordinate(cu, line, column).some();
                //noinspection DataFlowIssue
                return (J.CompilationUnit) cu.accept(new JavaIsoVisitor<>() {
                    @Override
                    public @Nullable J preVisit(J tree, ExecutionContext executionContext) {
                        if (tree.isScope(found)) {
                            return SearchResult.found(tree, tree.getClass().getSimpleName());
                        }
                        return super.preVisit(tree, executionContext);
                    }
                }, executionContext);
            }
        };
    }

    private static Supplier<TreeVisitor<?, ExecutionContext>> locateLine(int line) {
        return () -> new JavaIsoVisitor<>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext executionContext) {
                Collection<J> found = CoordinateLocator.findLine(cu, line);

                //noinspection DataFlowIssue
                return (J.CompilationUnit) cu.accept(new JavaIsoVisitor<>() {
                    @Override
                    public @Nullable J preVisit(J tree, ExecutionContext executionContext) {
                        if (found.contains(tree)) {
                            return SearchResult.found(tree, tree.getClass().getSimpleName());
                        }
                        return super.preVisit(tree, executionContext);
                    }
                }, executionContext);
            }
        };
    }

    @Test
    void findCoordinatePassedString() {
        rewriteRun(
          spec -> spec.recipe(RewriteTest.toRecipe(locateCoordinate(3, 28))),
          java(
            """
              class Test {
                  void test() {
                      System.out.println("Hello World!");
                  }
              }
              """,
            """
              class Test {
                  void test() {
                      System.out.println(/*~~(Literal)~~>*/"Hello World!");
                  }
              }
              """
          )
        );
    }

    @Test
    void findCoordinateMethodInvocation() {
        rewriteRun(
          spec -> spec.recipe(RewriteTest.toRecipe(locateCoordinate(3, 20))),
          java(
            """
              class Test {
                  void test() {
                      System.out.println("Hello World!");
                  }
              }
              """,
            """
              class Test {
                  void test() {
                      System.out./*~~(Identifier)~~>*/println("Hello World!");
                  }
              }
              """
          )
        );
    }

    @Test
    void findMethodCallLineNumber() {
        rewriteRun(
          spec -> spec.recipe(RewriteTest.toRecipe(locateLine(3))),
          java(
            """
              class Test {
                  void test() {
                      System.out.println("Hello World!");
                  }
              }
              """,
            """
              class Test {
                  void test() {
                      /*~~(FieldAccess)~~>*//*~~(Identifier)~~>*/System./*~~(Identifier)~~>*/out./*~~(Identifier)~~>*/println(/*~~(Literal)~~>*/"Hello World!");
                  }
              }
              """
          )
        );
    }

    @Test
    void findClassHeaderLine() {
        rewriteRun(
          spec -> spec.recipe(RewriteTest.toRecipe(locateLine(1))),
          java(
            """
              class Test {
                  void test() {
                      System.out.println("Hello World!");
                  }
              }
              """,
            """
              /*~~(ClassDeclaration)~~>*/class /*~~(Identifier)~~>*/Test /*~~(Block)~~>*/{
                  void test() {
                      System.out.println("Hello World!");
                  }
              }
              """
          )
        );
    }

    @Test
    void findClassHeaderWithMultilneCommentLine() {
        rewriteRun(
          spec -> spec.recipe(RewriteTest.toRecipe(locateLine(1))),
          java(
            """
              class Test /*
              */ {
                  void test() {
                      System.out.println("Hello World!");
                  }
              }
              """,
            """
              /*~~(ClassDeclaration)~~>*/class /*~~(Identifier)~~>*/Test /*
              */ {
                  void test() {
                      System.out.println("Hello World!");
                  }
              }
              """
          )
        );
    }
}
