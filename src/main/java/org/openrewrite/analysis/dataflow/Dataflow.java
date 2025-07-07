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

import fj.data.Option;
import lombok.AllArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.Incubating;
import org.openrewrite.analysis.controlflow.ControlFlow;
import org.openrewrite.analysis.dataflow.analysis.FlowGraph;
import org.openrewrite.analysis.dataflow.analysis.ForwardFlow;
import org.openrewrite.analysis.dataflow.analysis.SinkFlowSummary;
import org.openrewrite.java.tree.Expression;

import java.util.Set;

/**
 * <a href="https://en.wikipedia.org/wiki/Dataflow_programming">Dataflow</a>.
 */
@AllArgsConstructor(access = lombok.AccessLevel.PRIVATE)
@Incubating(since = "7.24.0")
public class Dataflow {
    @Nullable
    private final DataFlowNode n;

    public Option<SinkFlowSummary> findSinks(DataFlowSpec spec) {
        if (n == null) {
            return Option.none();
        }
        if (!spec.isSource(n)) {
            return Option.none();
        }
        return ControlFlow.startingAt(n.getCursor()).findControlFlow().bind(summary -> {
            Set<Expression> reachable = summary.computeReachableExpressions(spec::isBarrierGuard);

            FlowGraph flow = ForwardFlow.findAllFlows(n, spec, FlowGraph.Factory.defaultFactory());
            SinkFlowSummary sinkFlowSummary = SinkFlowSummary.create(flow, spec, reachable);
            return sinkFlowSummary.isNotEmpty() ? Option.some(sinkFlowSummary) : Option.none();
        });
    }

    public static Dataflow startingAt(Cursor start) {
        return startingAt(DataFlowNode.of(start).toNull());
    }

    public static Dataflow startingAt(@Nullable DataFlowNode node) {
        return new Dataflow(node);
    }
}
