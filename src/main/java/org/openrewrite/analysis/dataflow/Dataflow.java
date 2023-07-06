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
import lombok.RequiredArgsConstructor;
import org.openrewrite.Cursor;
import org.openrewrite.Incubating;
import org.openrewrite.analysis.controlflow.ControlFlow;
import org.openrewrite.analysis.dataflow.analysis.*;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.tree.Expression;

import java.util.Optional;
import java.util.Set;

/**
 * <a href="https://en.wikipedia.org/wiki/Dataflow_programming">Dataflow</a>.
 */
@Incubating(since = "7.24.0")
@RequiredArgsConstructor(staticName = "startingAt")
public class Dataflow {
    @Nullable
    private final Cursor start;

    public Option<SinkFlowSummary> findSinks(DataFlowSpec spec) {
        if (start == null) {
            return Option.none();
        }
        return DataFlowNode.of(start).bind(n -> {
            if (!spec.isSource(n)) {
                return Option.none();
            }
            return ControlFlow.startingAt(start).findControlFlow().bind(summary -> {
                Set<Expression> reachable = summary.computeReachableExpressions(spec::isBarrierGuard);

                FlowGraph flow = new SinkFlow(n);
                ForwardFlow.findSinks(flow, spec);
                SinkFlowSummary sinkFlowSummary = SinkFlowSummary.create(flow, spec, reachable);
                return sinkFlowSummary.isNotEmpty() ? Option.some(sinkFlowSummary) : Option.none();
            });
        });
    }

    public <E extends Expression> Optional<SourceFlow> findSources(DataFlowSpec spec) {
        throw new UnsupportedOperationException("Not yet implemented.");
    }
}
