package org.openrewrite.analysis.dataflow;

import org.junit.jupiter.api.Test;

public class ExternalFlowModelsTest {
    @Test
    void listFlowModelsOptimized() {
        final var externalFlowModels = ExternalFlowModels.instance();

        var flowModels = externalFlowModels.getFullyQualifiedNameToFlowModels().forAll();
        var optimizedModels = ExternalFlowModels.Optimizer.optimize(flowModels);
    }
}
