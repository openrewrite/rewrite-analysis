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

import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.analysis.dataflow.Dataflow;
import org.openrewrite.analysis.dataflow.LocalFlowSpec;
import org.openrewrite.analysis.controlflow.Guard;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

public class UriCreatedWithHttpScheme extends Recipe {
    private static final MethodMatcher URI_CREATE = new MethodMatcher("java.net.URI create(..)");
    private static final MethodMatcher STRING_REPLACE = new MethodMatcher("java.lang.String replace(..)");

    private static final LocalFlowSpec<J.Literal, Expression> INSECURE_URI_CREATE = new LocalFlowSpec<J.Literal, Expression>() {
        @Override
        public boolean isSource(J.Literal source, Cursor cursor) {
            return source.getValue() != null && source.getValue().toString().startsWith("http://");
        }

        @Override
        public boolean isSink(Expression sink, Cursor cursor) {
            J.MethodInvocation maybeMethodInvocation = cursor.firstEnclosing(J.MethodInvocation.class);
            if (maybeMethodInvocation != null && URI_CREATE.matches(maybeMethodInvocation)) {
                return maybeMethodInvocation.getArguments().contains(sink);
            } else {
                return false;
            }
        }

        @Override
        public boolean isAdditionalFlowStep(Expression srcExpression, Cursor srcCursor, Expression sinkExpression, Cursor sinkCursor) {
            if (sinkExpression instanceof J.Binary) {
                J.Binary endBinary = (J.Binary) sinkExpression;
                return srcExpression == endBinary.getLeft();
            }
            return false;
        }

        @Override
        public boolean isBarrierGuard(Guard guard, boolean branch) {
            return STRING_REPLACE.matches(guard.getExpression());
        }
    };

    @Override
    public String getDisplayName() {
        return "URIs created with an HTTP scheme";
    }

    @Override
    public String getDescription() {
        return "This is a sample recipe demonstrating a simple application of local data flow analysis.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesMethod<>(URI_CREATE), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Literal visitLiteral(J.Literal literal, ExecutionContext ctx) {
                J.Literal l = super.visitLiteral(literal, ctx);
                if (Dataflow.startingAt(getCursor()).findSinks(INSECURE_URI_CREATE).isPresent()) {
                    //noinspection ConstantConditions
                    return l.withValue(l.getValue().toString().replace("http://", "https://"))
                            .withValueSource(l.getValueSource().replace("http://", "https://"));
                }
                return l;
            }
        });
    }
}
