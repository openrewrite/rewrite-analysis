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

import lombok.AllArgsConstructor;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.TreeVisitor;

import java.util.concurrent.atomic.AtomicBoolean;

@AllArgsConstructor(staticName = "wrap")
final class CallOrderEnforcingGlobalDataFlowAccumulator implements GlobalDataFlow.Accumulator {
    private final GlobalDataFlow.Accumulator decorated;
    private final AtomicBoolean isScanned = new AtomicBoolean(false);

    @Override
    public TreeVisitor<?, ExecutionContext> scanner() {
        isScanned.set(true);
        return decorated.scanner();
    }

    @Override
    public GlobalDataFlow.Summary summary(Cursor cursor) {
        if (!isScanned.get()) {
            throw new IllegalStateException(
                    "GlobalDataFlow.Accumulator.summary(Cursor) called before GlobalDataFlow.Accumulator.scanner()\n" +
                    "\n" +
                    "\t===> Ensure that Global Data Flow is used in a Scanning Recipe <===\n" +
                    "\n"
            );
        }
        return decorated.summary(cursor);
    }
}
