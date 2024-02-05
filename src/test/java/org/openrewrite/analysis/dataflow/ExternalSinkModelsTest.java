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
package org.openrewrite.analysis.dataflow;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExternalSinkModelsTest {

    @Test
    void listSinkModelsOptimized() {
        final var externalSinkModels = ExternalSinkModels.instance();

        final var sinkModels = externalSinkModels.getFullyQualifiedNameToSinkModel().forAll();
        final var optimizedModels = ExternalSinkModels.Optimizer.optimize(sinkModels);

        // Place the sink flow models into a new set that can be manipulated
        Set<ExternalSinkModels.SinkModel> models = new HashSet<>(sinkModels.getSinkModels());

        // Remove all optimized sink flow models
        models.removeAll(optimizedModels.getSinkModels());

        assertEquals(0, models.size(), "All sink models should be optimized");
    }

}
