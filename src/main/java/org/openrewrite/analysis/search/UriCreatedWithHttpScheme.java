/*
 * Copyright 2022 the original author or authors.
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
package org.openrewrite.analysis.search;

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.analysis.InvocationMatcher;
import org.openrewrite.analysis.controlflow.Guard;
import org.openrewrite.analysis.dataflow.DataFlowNode;
import org.openrewrite.analysis.dataflow.DataFlowSpec;
import org.openrewrite.analysis.dataflow.Dataflow;
import org.openrewrite.analysis.trait.expr.BinaryExpr;
import org.openrewrite.analysis.trait.expr.Literal;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.J;

public class UriCreatedWithHttpScheme extends Recipe {
    private static final InvocationMatcher URI_CREATE = InvocationMatcher.fromMethodMatcher("java.net.URI create(..)");
    private static final MethodMatcher STRING_REPLACE = new MethodMatcher("java.lang.String replace(..)");

    private static final DataFlowSpec INSECURE_URI_CREATE = new DataFlowSpec() {
        @Override
        public boolean isSource(DataFlowNode srcNode) {
            return srcNode
                    .asExpr(Literal.class)
                    .bind(Literal::getValue)
                    .map(v -> v.toString().startsWith("http://"))
                    .orSome(false);
        }

        @Override
        public boolean isSink(DataFlowNode sinkNode) {
            return URI_CREATE.advanced().isAnyArgument(sinkNode.getCursor());
        }

        @Override
        public boolean isAdditionalFlowStep(DataFlowNode srcNode, DataFlowNode sinkNode) {
            return sinkNode
                    .asExpr(BinaryExpr.class)
                    .bind(srcNode.asExpr(), binary -> src -> binary.getLeft().equals(src))
                    .orSome(false);
        }

        @Override
        public boolean isBarrierGuard(Guard guard, boolean branch) {
            return STRING_REPLACE.matches(guard.getExpression());
        }
    };

    @Getter final String displayName = "URIs created with an HTTP scheme";

    @Getter final String description = "This is a sample recipe demonstrating a simple application of local data flow analysis.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesInvocation<>(URI_CREATE), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ctx) {
                J.Literal l = super.visitLiteral(literal, ctx);
                if (Dataflow.startingAt(getCursor()).findSinks(INSECURE_URI_CREATE).isSome()) {
                    //noinspection ConstantConditions
                    return l.withValue(l.getValue().toString().replace("http://", "https://"))
                            .withValueSource(l.getValueSource().replace("http://", "https://"));
                }
                return l;
            }
        });
    }
}
