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
import org.openrewrite.Cursor;
import org.openrewrite.DocumentExample;
import org.openrewrite.ExecutionContext;
import org.openrewrite.TreeVisitor;
import org.openrewrite.analysis.trait.member.Method;
import org.openrewrite.analysis.trait.util.TraitErrors;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.test.RewriteTest;

import java.util.function.BiFunction;
import java.util.function.Supplier;

import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.test.RewriteTest.toRecipe;

class ParameterTest implements RewriteTest {

    static Supplier<TreeVisitor<?, ExecutionContext>> visitVariable(
      BiFunction<J.VariableDeclarations.NamedVariable, Cursor, J.VariableDeclarations.NamedVariable> visitor
    ) {
        return () -> new JavaIsoVisitor<>() {
            @Override
            public J.VariableDeclarations.NamedVariable visitVariable(
              J.VariableDeclarations.NamedVariable variable,
              ExecutionContext executionContext
            ) {
                return super.visitVariable(visitor.apply(variable, getCursor()), executionContext);
            }
        };
    }

    @DocumentExample
    @Test
    void variableVisit() {
        rewriteRun(
          spec -> spec.recipe(toRecipe(visitVariable((variable, cursor) -> {
                Parameter p = Parameter.viewOf(cursor).on(TraitErrors::doThrow);
              assertThat(p.getPosition()).as("Parameter position is incorrect").isZero();
              assertThat(p.isVarArgs()).as("Parameter isVarArgs is incorrect").isFalse();
              assertThat(p.getFlags()).as("Parameter flags are incorrect").isEqualTo(emptySet());
              assertThat(p.getName()).as("Parameter name is incorrect").isEqualTo("i");
              assertThat(p.getCallable().getName()).as("Parameter callable name is incorrect").isEqualTo("test");
                Method method = Method.Factory.F.firstEnclosingViewOf(cursor).on(TraitErrors::doThrow);
              assertThat(method).as("Parameter callable is incorrect").isEqualTo(p.getCallable());
                return SearchResult.found(variable);
            }
          ))),
          java(
            "abstract class Test { abstract void test(int i); }",
            "abstract class Test { abstract void test(int /*~~>*/i); }"
          )
        );
    }

    @Test
    void variableVisitVarargs() {
        rewriteRun(
          spec -> spec.recipe(toRecipe(visitVariable((variable, cursor) -> {
                Parameter p = Parameter.viewOf(cursor).on(TraitErrors::doThrow);
              assertThat(p.getPosition()).as("Parameter position is incorrect").isZero();
              assertThat(p.isVarArgs()).as("Parameter isVarArgs is incorrect").isTrue();
              assertThat(p.getFlags()).as("Parameter flags are incorrect").isEqualTo(emptySet());
              assertThat(p.getName()).as("Parameter name is incorrect").isEqualTo("i");
              assertThat(p.getCallable().getName()).as("Parameter callable name is incorrect").isEqualTo("test");
                return SearchResult.found(variable);
            }
          ))),
          java(
            "abstract class Test { abstract void test(int... i); }",
            "abstract class Test { abstract void test(int... /*~~>*/i); }"
          )
        );
    }
}
