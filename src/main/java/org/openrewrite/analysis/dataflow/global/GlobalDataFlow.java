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

import lombok.NoArgsConstructor;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.TreeVisitor;
import org.openrewrite.analysis.dataflow.DataFlowNode;
import org.openrewrite.analysis.dataflow.DataFlowSpec;

/**
 * Plan:
 * <p>
 * <ol>
 *     <li>
 *         Build an exhaustive flow graph that includes all methods, from all arguments, to all arguments, and to all return values.
 *         Don't discriminate at all. This is a superset graph of data flow. It will include all possible paths.
 *     </li>
 *     <li>
 *         Connect this graph together after finding all of the valid source nodes.
 *     </li>
 *     <li>
 *         Walk the graph from all sources, pruning all paths were {@link DataFlowSpec#isAdditionalFlowStep}
 *         returns false, and where {@link DataFlowSpec#isSink(DataFlowNode)} returns false.
 *     </li>
 * </ol>
 */
@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public class GlobalDataFlow {

    public static Accumulator accumulator(DataFlowSpec spec) {
        return CallOrderEnforcingGlobalDataFlowAccumulator.wrap(new GlobalDataFlowAccumulator(spec));
    }

    public interface Accumulator {

        TreeVisitor<?, ExecutionContext> scanner();

        Summary summary(Cursor cursor);

        default TreeVisitor<?, ExecutionContext> renderer() {
            return new RenderGlobalFlowPaths<>(this);
        }

        default boolean isSource(Cursor cursor) {
            return summary(cursor).isSource();
        }

        default boolean isSink(Cursor cursor) {
            return summary(cursor).isSink();
        }

        default boolean isFlowParticipant(Cursor cursor) {
            return summary(cursor).isFlowParticipant();
        }
    }

    public interface Summary {
        boolean isSource();

        boolean isSink();

        boolean isFlowParticipant();
    }
}
