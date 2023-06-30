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
package org.openrewrite.analysis.dataflow;

import org.openrewrite.Cursor;
import org.openrewrite.Incubating;
import org.openrewrite.analysis.controlflow.Guard;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

@Incubating(since = "7.25.0")
public abstract class LocalTaintFlowSpec<Source extends Expression, Sink extends J> extends LocalFlowSpec<Source, Sink> {

    @Override
    public final boolean isAdditionalFlowStep(
            DataFlowNode srcNode,
            DataFlowNode sinkNode
    ) {
        return ExternalFlowModels.instance().isAdditionalTaintStep(
                srcNode,
                sinkNode
        ) || DefaultFlowModels.isDefaultAdditionalTaintStep(
                srcNode,
                sinkNode
        ) || isAdditionalTaintStep(
                srcNode,
                sinkNode
        );
    }

    public boolean isAdditionalTaintStep(
            DataFlowNode srcNode,
            DataFlowNode sinkNode
    ) {
        return false;
    }

    @Override
    public final boolean isBarrierGuard(Guard guard, boolean branch) {
        return isSanitizerGuard(guard, branch);
    }

    public boolean isSanitizerGuard(Guard guard, boolean branch) {
        return false;
    }

    @Override
    public final boolean isBarrier(Expression expression, Cursor cursor) {
        return isSanitizer(expression, cursor);
    }

    public boolean isSanitizer(Expression expression, Cursor cursor) {
        return false;
    }
}
