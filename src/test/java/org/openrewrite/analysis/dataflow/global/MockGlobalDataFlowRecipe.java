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
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import org.openrewrite.ExecutionContext;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.analysis.dataflow.DataFlowSpec;

@AllArgsConstructor
@FieldDefaults(makeFinal = true, level = lombok.AccessLevel.PRIVATE)
public class MockGlobalDataFlowRecipe extends ScanningRecipe<GlobalDataFlow.Accumulator> {
    transient DataFlowSpec spec;

    /**
     * To make Jackson happy.
     */
    @SuppressWarnings("unused")
    MockGlobalDataFlowRecipe() {
        this(null);
    }

    @Getter final String displayName = "blah";

    @Getter final String description = "blah.";

    @Override
    public GlobalDataFlow.Accumulator getInitialValue(ExecutionContext ctx) {
        return GlobalDataFlow.accumulator(spec);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(GlobalDataFlow.Accumulator acc) {
        return acc.scanner();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(GlobalDataFlow.Accumulator acc) {
        return acc.renderer();
    }
}
