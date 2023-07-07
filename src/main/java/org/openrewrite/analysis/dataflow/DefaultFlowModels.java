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

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.openrewrite.Incubating;
import org.openrewrite.analysis.trait.expr.BinaryExpr;
import org.openrewrite.java.tree.J;

@Incubating(since = "7.24.2")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class DefaultFlowModels {

    /**
     * Holds if the additional step from `src` to `sink` should be included in all
     * taint flow configurations.
     */
    static boolean isDefaultAdditionalTaintStep(
            DataFlowNode srcNode,
            DataFlowNode sinkNode
    ) {
        return isLocalAdditionalTaintStep(
                srcNode,
                sinkNode
        );
    }

    private static boolean isLocalAdditionalTaintStep(
            DataFlowNode srcNode,
            DataFlowNode sinkNode
    ) {
        return AdditionalLocalTaint.isStringAddTaintStep(
                srcNode,
                sinkNode
        );
    }

    private static final class AdditionalLocalTaint {

        private static boolean isStringAddTaintStep(
                DataFlowNode srcNode,
                DataFlowNode sinkNode
        ) {
            return sinkNode
                    .asExpr(BinaryExpr.class)
                    .bind(srcNode.asExpr(), binary -> src -> J.Binary.Type.Addition.equals(binary.getOperator()) &&
                                    binary.getLeft().equals(src) || binary.getRight().equals(src))
                    .orSome(false);
        }
    }
}
