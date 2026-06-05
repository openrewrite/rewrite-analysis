/*
 * Copyright 2025 the original author or authors.
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
package org.openrewrite.analysis.dataflow.internal;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.openrewrite.Cursor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

/**
 * Helpers for reasoning about the return value of a lambda — the value that "flows out" of a
 * functional argument. Shared by the higher-order ("lambda call") data-flow engine and the
 * higher-order sink models so both agree on exactly which expressions count as a lambda's result.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LambdaReturns {

    /** Holds if the node at {@code nodeCursor} is (the expression of) {@code lambda}'s return value. */
    public static boolean isLambdaResult(Cursor nodeCursor, J.Lambda lambda) {
        J node = nodeCursor.getValue();
        J body = lambda.getBody();
        if (!(body instanceof J.Block)) {
            return body instanceof Expression && Expression.unwrap((Expression) body) == node;
        }
        J.Return aReturn = nodeCursor.firstEnclosing(J.Return.class);
        if (aReturn == null || aReturn.getExpression() == null ||
            Expression.unwrap(aReturn.getExpression()) != node) {
            return false;
        }
        // The return must belong to this lambda, not a nested lambda or anonymous-class method.
        return nodeCursor.firstEnclosing(J.Lambda.class) == lambda;
    }
}
