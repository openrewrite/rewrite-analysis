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

import org.openrewrite.Incubating;
import org.openrewrite.analysis.controlflow.Guard;

@Incubating(since = "7.24.0")
public abstract class DataFlowSpec {
    /**
     * The following is always true: {@code  source == cursor.getValue()}.
     *
     * @param srcNode The {@link DataFlowNode} to check to determine if it should be considered flow-graph source.
     * @return {@code true} if {@code srcNode} should be considered the source or root of a flow graph.
     */
    public abstract boolean isSource(DataFlowNode srcNode);

    /**
     * The following is always true: {@code  sink == cursor.getValue()}.
     *
     * @param sinkNode The {@link DataFlowNode} to check to determine if it should be considered a flow-graph sink.
     * @return {@code true} if {@code sinkNode} should be considered the sink or leaf of a flow graph.
     */
    public abstract boolean isSink(DataFlowNode sinkNode);

    public final boolean isFlowStep(
            DataFlowNode srcNode,
            DataFlowNode sinkNode
    ) {
        return ExternalFlowModels.instance().isAdditionalFlowStep(
                srcNode,
                sinkNode
        ) || isAdditionalFlowStep(
                srcNode,
                sinkNode
        );
    }

    /**
     * takes an existing flow-step in the graph and offers a potential next flow step.
     * The method can then decide if the offered potential next flow step should be considered a valid next flow step
     * in the graph.
     *
     * Allows for ad-hoc taint tracking by allowing for additional, non-default flow steps to be added to the flow graph.
     *
     * The following is always true:
     * {@code  srcExpression == srcCursor.getValue() && sinkExpression == sinkCursor.getValue()}.
     */
    public boolean isAdditionalFlowStep(
            DataFlowNode srcNode,
            DataFlowNode sinkNode
    ) {
        return false;
    }

    public boolean isBarrierGuard(Guard guard, boolean branch) {
        return false;
    }

    /**
     * Holds if flow through `expression` is prohibited.
     */
    public boolean isBarrier(DataFlowNode node) {
        return false;
    }
}
