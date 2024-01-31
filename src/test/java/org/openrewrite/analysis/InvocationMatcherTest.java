/*
 * Copyright 2024 the original author or authors.
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
package org.openrewrite.analysis;

import org.junit.jupiter.api.Test;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.test.RewriteTest;

import java.util.function.Predicate;

import static org.openrewrite.java.Assertions.java;

public class InvocationMatcherTest implements RewriteTest {

    static JavaIsoVisitor<ExecutionContext> matcherVisitor(Predicate<Cursor> match) {
        return new JavaIsoVisitor<>() {
            @Override
            public J preVisit(J tree, ExecutionContext executionContext) {
                if (match.test(getCursor())) {
                    return SearchResult.found(tree);
                }
                return tree;
            }
        };
    }

    static InvocationMatcher matcher(String m) {
        return InvocationMatcher.fromMethodMatcher(m);
    }

    @Test
    void methodMatcherFirstParameter() {
        // Given
        InvocationMatcher m = matcher("Test *(..)");
        // When
        Predicate<Cursor> test = c -> m.advanced().isFirstParameter(c);
        // Then
        rewriteRun(
          spec -> spec.recipe(RewriteTest.toRecipe(() -> matcherVisitor(test))),
          java("""
              class Test {
                  void test() {
                      acceptsVarargs("foo", "bar");
                      acceptsMultiple("foo", "bar");
                  }
                  
                  void acceptsVarargs(String... args) {}
                  void acceptsMultiple(String a, String b) {}
              }
              """,
            """
              class Test {
                  void test() {
                      acceptsVarargs(/*~~>*/"foo", /*~~>*/"bar");
                      acceptsMultiple(/*~~>*/"foo", "bar");
                  }
                  
                  void acceptsVarargs(String... args) {}
                  void acceptsMultiple(String a, String b) {}
              }
              """
          )
        );
    }

    @Test
    void methodMatcherFirstArgument() {
        // Given
        InvocationMatcher m = matcher("Test *(..)");
        // When
        Predicate<Cursor> test = c -> m.advanced().isFirstArgument(c);
        // Then
        rewriteRun(
          spec -> spec.recipe(RewriteTest.toRecipe(() -> matcherVisitor(test))),
          java("""
              class Test {
                  void test() {
                      acceptsVarargs("foo", "bar");
                      acceptsMultiple("foo", "bar");
                  }
                  
                  void acceptsVarargs(String... args) {}
                  void acceptsMultiple(String a, String b) {}
              }
              """,
            """
              class Test {
                  void test() {
                      acceptsVarargs(/*~~>*/"foo", "bar");
                      acceptsMultiple(/*~~>*/"foo", "bar");
                  }
                  
                  void acceptsVarargs(String... args) {}
                  void acceptsMultiple(String a, String b) {}
              }
              """
          )
        );
    }
}
