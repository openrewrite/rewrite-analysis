/*
 * Copyright 2026 the original author or authors.
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
package org.openrewrite.analysis.dataflow;

import org.junit.jupiter.api.Test;
import org.openrewrite.analysis.trait.expr.Literal;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.kotlin.Assertions.kotlin;
import static org.openrewrite.test.RewriteTest.toRecipe;

/**
 * Regression tests covering Kotlin sources where ControlFlow analysis used to
 * crash with {@code ControlFlowIllegalStateException: No current node!}.
 */
class FindLocalFlowPathsKotlinTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(toRecipe(() -> new FindLocalFlowPaths<>(new DataFlowSpec() {
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
                return true;
            }
        })));
    }

    @Test
    void elvisReturnInVariableInitializer() {
        // Regression: ControlFlow used to throw "No current node!" when a Kotlin
        // method body contained `val x = expr ?: return ...`. The Elvis right side
        // contains a `return`, which clears `current`, so the trailing variable
        // name (or any subsequent statement) tripped `currentAsBasicBlock()`.
        // Observed on the 2026-05-07 flagship run against
        // moderneinc/moderne-intellij-plugin (RecipeUtils.kt).
        rewriteRun(
          //language=kotlin
          kotlin(
            """
              object Foo {
                  fun problematic(items: List<String>?): Boolean {
                      if (items == null) return false
                      val first = items.firstOrNull() ?: return false
                      return first.isNotEmpty()
                  }
              }
              """
          )
        );
    }

    @Test
    void elvisThrowInVariableInitializer() {
        // Same shape but with `throw` instead of `return`.
        rewriteRun(
          //language=kotlin
          kotlin(
            """
              object Foo {
                  fun problematic(items: List<String>?): Int {
                      val first = items?.firstOrNull() ?: throw IllegalArgumentException("empty")
                      return first.length
                  }
              }
              """
          )
        );
    }
}
