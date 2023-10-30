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
package org.openrewrite.analysis.trait.variable;

import org.junit.jupiter.api.Test;
import org.openrewrite.ExecutionContext;
import org.openrewrite.analysis.trait.expr.VarAccess;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.test.RewriteTest.toRecipe;

public class JavadocTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(toRecipe(() -> new JavaIsoVisitor<>() {
            @Override
            public J preVisit(J tree, ExecutionContext executionContext) {
                return VarAccess.viewOf(getCursor())
                  .map(var -> {
                      assertNotNull(var.getVariable(), "VarAccess.getVariable() is null");
                      return SearchResult.found(tree);
                  })
                  .orSuccess(tree);
            }
        }));
    }

    @Test
    void javadocWithSee() {
        rewriteRun(
          java(
            """
              class Test {
                  /**
                   * @see #bar(String str)
                   */
                  void foo() {
                      // no-op
                  }
              
                  void bar(String str)
                  {
                      // no-op
                  }
              }
              """
          )
        );
    }

    @Test
    void differentJavadocWithSee() {
        rewriteRun(
          java(
            """
              class Test {
                  /**
                   * @see #bar(String)
                   */
                  void foo() {
                      // no-op
                  }
              
                  void bar(String str)
                  {
                      // no-op
                  }
              }
              """
          )
        );
    }

    @Test
    void javadocStaticField() {
        rewriteRun(
          java(
            """
              class Test {
                  /**
                   * @value #HELLO
                   */
                  static final String HELLO = "hello";
              }
              """
          )
        );
    }

    @Test
    void javadocWithCode() {
        rewriteRun(
          java(
            """
              class Test {
                  /**
                   * This method takes a {@code String} and
                   * a {@code int} as parameters
                   */
                  void foobar(String s, int i) {
                      // no-op
                  }
              }
              """
          )
        );
    }

    @Test
    void javadocWithCodeHtml() {
        rewriteRun(
          java(
            """
              class Test {
                  /**
                   * This method takes a <code>String</code> and
                   * a <code>int</code> as parameters
                   */
                  void foobar(String s, int i) {
                      // no-op
                  }
              }
              """
          )
        );
    }

    @Test
    void javadocWithThrows() {
        rewriteRun(
          java(
            """
              import java.io.IOException;
              
              class Test {
                  /**
                   * @throws IOException This method throws an exception
                   */
                  void foo() throws IOException {
                      throw new IOException();
                  }
              }
              """
          )
        );
    }

    @Test
    void javadocWithLink() {
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.builder().methodInvocations(false).build()),
          java(
            """
              class Test {
                  /**
                   * {@link #foo(String, float) foo}
                   */
                  void foo(String s, float f) {
                      // no-op
                  }
              }
              """
          )
        );
    }
}
