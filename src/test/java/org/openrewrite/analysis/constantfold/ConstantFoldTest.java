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
package org.openrewrite.analysis.constantfold;

import fj.data.Option;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.analysis.InvocationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class ConstantFoldTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(RewriteTest.toRecipe(ConstantFoldVisitor::new));
    }

    static class ConstantFoldVisitor<P> extends JavaIsoVisitor<P> {
        private static final InvocationMatcher ALL_MATCHER = t -> true;

        @Override
        public Expression visitExpression(Expression expression, P p) {
            if (ALL_MATCHER.advanced().isFirstArgument(getCursor())) {
                Option<J> constantValue = ConstantFold.findConstantJ(getCursor());
                if (constantValue.isSome() && constantValue.map(j -> j.getMarkers().findFirst(SearchResult.class).isEmpty()).orSome(false)) {
                    return SearchResult.found(expression, constantValue.some().print(getCursor()).trim());
                }
            }
            return super.visitExpression(expression, p);
        }
    }

    @DocumentExample
    @Test
    void constantFoldTagsElements() {
        rewriteRun(
          java(
            """
            class Test {
                private static final String FOO_IDENT = "FOO";
                private static final String BAR_IDENT = "BAR";
                void test() {
                    any(FOO_IDENT);
                    any(BAR_IDENT);
                    any("BAZ");
                }
                
                void any(Object arg) {
                    // No-op
                }
            }
            """,
            """
            class Test {
                private static final String FOO_IDENT = "FOO";
                private static final String BAR_IDENT = "BAR";
                void test() {
                    any(/*~~("FOO")~~>*/FOO_IDENT);
                    any(/*~~("BAR")~~>*/BAR_IDENT);
                    any(/*~~("BAZ")~~>*/"BAZ");
                }
                
                void any(Object arg) {
                    // No-op
                }
            }
            """
          )
        );
    }

    @Test
    void constantFoldSingleElementString() {
        rewriteRun(
          java(
            """
            class Test {
                private static final String FOO_IDENT = "FOO";
                void test() {
                    any(FOO_IDENT);
                }
                
                void any(Object arg) {
                    // No-op
                }
            }
            """,
            """
            class Test {
                private static final String FOO_IDENT = "FOO";
                void test() {
                    any(/*~~("FOO")~~>*/FOO_IDENT);
                }
                
                void any(Object arg) {
                    // No-op
                }
            }
            """
          )
        );
    }

    @Test
    void constantFoldTagsArray() {
        rewriteRun(
          java(
            """
            class Test {
                private static final String[] FOO_IDENT = new String[] {"FOO"};
                void test() {
                    any(FOO_IDENT);
                    any(new String[] {"BAR"});
                }
                
                void any(Object arg) {
                    // No-op
                }
            }
            """,
            """
            class Test {
                private static final String[] FOO_IDENT = new String[] {"FOO"};
                void test() {
                    any(/*~~(new String[] {"FOO"})~~>*/FOO_IDENT);
                    any(/*~~(new String[] {"BAR"})~~>*/new String[] {"BAR"});
                }
                
                void any(Object arg) {
                    // No-op
                }
            }
            """
          )
        );
    }
}
