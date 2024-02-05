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

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExternalFlowModelsTest {
    @Test
    void listFlowModelsOptimized() {
        final var externalFlowModels = ExternalFlowModels.instance();

        // Collect all flow models and all optimized flow models
        var flowModels = externalFlowModels.getFullyQualifiedNameToFlowModels().forAll();
        var optimizedModels = ExternalFlowModels.Optimizer.optimize(flowModels);

        // Place the data flow models and taint flow models into separate sets
        Set<ExternalFlowModels.FlowModel> value = new HashSet<>(flowModels.value);
        Set<ExternalFlowModels.FlowModel> taint = new HashSet<>(flowModels.taint);

        // Remove all optimized data/taint flow models
        value.removeAll(optimizedModels.getValueFlowModels());
        taint.removeAll(optimizedModels.getTaintFlowModels());

        // Filter out unwanted data/taint flow models
        value = value.stream().filter(ExternalFlowModelsTest::filterModels).collect(Collectors.toSet());
        taint = taint.stream().filter(ExternalFlowModelsTest::filterModels).collect(Collectors.toSet());

        // Ensure that there are no unchecked values
        assertEquals(0, value.size(), "All value models should be optimized");
        assertEquals(0, taint.size(), "All taint models should be optimized");
    }
    
    static boolean filterModels(ExternalFlowModels.FlowModel model) {
        for (int i = 0; i < model.input.length() - 1; i++) {
            char currC = model.input.charAt(i);
            char nextC = model.input.charAt(i + 1);

            if (currC == '.') {
                if (currC == nextC) {
                    i++;
                } else {
                    return false;
                }
            }
        }

        for (int j = 0; j < model.output.length() - 1; j++) {
            char currC = model.output.charAt(j);
            char nextC = model.output.charAt(j + 1);

            if (currC == '.') {
                if (currC == nextC) {
                    j++;
                } else {
                    return false;
                }
            }
        }

        return true;
    }
}
