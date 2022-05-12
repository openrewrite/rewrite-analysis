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
package org.openrewrite.java.dataflow;

import lombok.RequiredArgsConstructor;
import org.openrewrite.Cursor;
import org.openrewrite.Incubating;
import org.openrewrite.java.dataflow.analysis.ForwardFlow;
import org.openrewrite.java.dataflow.analysis.SinkFlow;
import org.openrewrite.java.dataflow.analysis.SourceFlow;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.Iterator;

@Incubating(since = "7.24.0")
@RequiredArgsConstructor
public class Dataflow {
    final Cursor start;

    public <Source extends Expression, Sink extends J> SinkFlow<Source, Sink> findSinks(LocalFlowSpec<Source, Sink> spec) {
        Iterator<Cursor> cursorPath = start.getPathAsCursors();
        while (cursorPath.hasNext()) {
            Cursor cursor = cursorPath.next();
            Object value = cursor.getValue();
            if (spec.getSourceType().isAssignableFrom(value.getClass())) {
                //noinspection unchecked
                Source source = (Source) value;

                SinkFlow<Source, Sink> flow = new SinkFlow<>(cursor, spec, start);
                if (spec.isSource(source, start)) {
                    ForwardFlow.findSinks(flow);
                }

                if (flow.isNotEmpty()) {
                    return flow;
                }
            }
        }

        return new SinkFlow<>(null, spec, start);
    }

    public <E extends Expression> SourceFlow<E> findSources(LocalFlowSpec<E, ?> spec) {
        throw new UnsupportedOperationException("Not yet implemented.");
    }
}