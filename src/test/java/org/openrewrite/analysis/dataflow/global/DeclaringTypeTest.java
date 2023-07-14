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

import org.junit.jupiter.api.Test;
import org.openrewrite.ExecutionContext;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.MethodCall;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.test.RewriteTest.toRecipe;

public class DeclaringTypeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(toRecipe(() -> new JavaIsoVisitor<>() {

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                return visitMethodCall(super.visitMethodInvocation(method, executionContext));
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext executionContext) {
                return visitMethodCall(super.visitNewClass(newClass, executionContext));
            }

            private <M extends MethodCall> M visitMethodCall(M methodCall) {
                assert methodCall.getMethodType() != null : "Types are missing for MethodCall " + methodCall.print(getCursor());
                JavaType.Method declaring = MethodTypeUtils.getDeclarationMethod(methodCall.getMethodType());
                if (methodCall.getMethodType().equals(declaring)) {
                    return methodCall;
                }
                return SearchResult.found(
                  methodCall,
                  "call:" + methodCall.getMethodType() + " decl: " +
                  MethodTypeUtils.getDeclarationMethod(methodCall.getMethodType())
                );
            }
        }));
    }

    @Test
    void polymorphicMethodCall() {
        rewriteRun(
          java(
            """
              class Test {
                  static abstract class Abstract {
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
              """
          )
        );
    }
}
