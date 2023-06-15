<<<<<<< HEAD
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
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.search.FindMethods;
import org.openrewrite.test.RewriteTest;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import java.util.stream.Collectors;

import static org.openrewrite.java.Assertions.java;
=======
package org.openrewrite.analysis.dataflow;

import org.junit.jupiter.api.Test;
>>>>>>> a7da7ec95534a35119c31c78f888438ff6cb9cdf

public class ExternalFlowModelsTest {
    @Test
    void listFlowModelsOptimized() {
        final var externalFlowModels = ExternalFlowModels.instance();

<<<<<<< HEAD
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
        value = value.stream().filter(model -> filterModels(model)).collect(Collectors.toSet());
        taint = taint.stream().filter(model -> filterModels(model)).collect(Collectors.toSet());

        // Ensure that there are no unchecked values
        assert value.size() == 0;

        // Create a Map to count different taint flow models
        Map<String, Set<ExternalFlowModels.FlowModel>> taintCategories = new HashMap<String, Set<ExternalFlowModels.FlowModel>>();
        categorizeTaintModels(taint, taintCategories);

        // Print out results
        System.out.println("taint flow models types:");
        taintCategories.entrySet().stream().sorted((s1, s2) -> s2.getValue().size() - s1.getValue().size()).forEach(entry -> System.out.println(entry.getKey() + " -> " + entry.getValue().size()));
        System.out.println("TOTAL: " + taint.size());

        // Ensure that Map was computed correctly
        assert taintCategories.values().stream().mapToInt(set -> set.size()).sum() == taint.size();

        /*// CHANGE VARIABLES BELOW TO VIEW UNCOVERED TAINT FLOWS
        String input = "Argument[1..2]";
        String output = "Argument[0]";
        printFlowModelContents(input, output, taintCategories);*/
        for (Set<ExternalFlowModels.FlowModel> set : taintCategories.values()) {
            for (ExternalFlowModels.FlowModel flowModel : set) {
                if (!flowModel.namespace.contains("kotlin") && !flowModel.namespace.contains("apache.commons.io")) {
                    if (!flowModel.input.contains("-1")) {
                        System.out.println(flowModel);
                    }
                }
            }
        }
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

    static void categorizeTaintModels(Set<ExternalFlowModels.FlowModel> taint, Map<String, Set<ExternalFlowModels.FlowModel>> taintCategories) {
        for (ExternalFlowModels.FlowModel t : taint) {
            String newKey = "input: " + t.input + ", output: " + t.output;

            if (!taintCategories.containsKey(newKey)) {
                Set<ExternalFlowModels.FlowModel> newFlowModelSet = new HashSet<ExternalFlowModels.FlowModel>();
                taintCategories.put(newKey, newFlowModelSet);
            }

            taintCategories.get(newKey).add(t);
        }
    }

    static void printFlowModelContents(String input, String output, Map<String, Set<ExternalFlowModels.FlowModel>> taintCategories) {
        String key = "input: " + input + ", output: " + output;

        if (taintCategories.containsKey(key)) {
            Set<ExternalFlowModels.FlowModel> taintModels = taintCategories.get(key);

            System.out.println("\ntaint models with " + key + " (count = " + taintModels.size() + ")");

            for (ExternalFlowModels.FlowModel taint : taintModels) {
                System.out.println(taint);
            }
        }
=======
        var flowModels = externalFlowModels.getFullyQualifiedNameToFlowModels().forAll();
        var optimizedModels = ExternalFlowModels.Optimizer.optimize(flowModels);
>>>>>>> a7da7ec95534a35119c31c78f888438ff6cb9cdf
    }
}
