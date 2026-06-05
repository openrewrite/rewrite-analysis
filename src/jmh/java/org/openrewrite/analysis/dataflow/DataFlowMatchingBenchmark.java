/*
 * Copyright 2025 the original author or authors.
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

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.SourceFile;
import org.openrewrite.analysis.trait.expr.MethodAccess;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.tree.J;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Exercises the external-model matching path: a single compilation unit dense in JDK calls that the
 * data-flow/taint models apply to (String, StringBuilder, collections, streams). Each invocation runs
 * the taint analysis over the whole unit, so the cost of matching every candidate flow step against the
 * (now ~26k-row) model set dominates. Useful for before/after comparison of the matcher.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(1)
@State(Scope.Benchmark)
public class DataFlowMatchingBenchmark {

    private J.CompilationUnit cu;
    private TaintFlowSpec spec;

    @Setup
    public void setup() {
        @SuppressWarnings("all")
        String source = "" +
                "import java.util.*;\n" +
                "import java.util.stream.*;\n" +
                "class Bench {\n" +
                "    String source() { return null; }\n" +
                "    void sink(Object o) {}\n" +
                "    void run() {\n" +
                "        String s = source();\n" +
                "        for (int i = 0; i < 50; i++) {\n" +
                "            String a = s.trim().toLowerCase().toUpperCase().substring(1).concat(\"x\").replace(\"a\", \"b\");\n" +
                "            StringBuilder sb = new StringBuilder();\n" +
                "            sb.append(a).append(s).insert(0, a);\n" +
                "            String b = sb.toString();\n" +
                "            List<String> list = new ArrayList<>();\n" +
                "            list.add(b);\n" +
                "            list.addAll(Arrays.asList(a, b));\n" +
                "            Map<String, String> map = new HashMap<>();\n" +
                "            map.put(\"k\", b);\n" +
                "            String c = map.getOrDefault(\"k\", a);\n" +
                "            String d = String.format(\"%s %s\", a, c);\n" +
                "            String e = list.stream().map(x -> x.trim()).collect(Collectors.joining(\",\"));\n" +
                "            sink(a); sink(b); sink(c); sink(d); sink(e);\n" +
                "            sink(String.join(\"/\", a, b, c));\n" +
                "            sink(Optional.of(a).orElse(b));\n" +
                "        }\n" +
                "    }\n" +
                "}\n";
        ExecutionContext ctx = new InMemoryExecutionContext();
        List<SourceFile> parsed = JavaParser.fromJavaVersion().build()
                .parse(ctx, source)
                .collect(Collectors.toList());
        cu = (J.CompilationUnit) parsed.get(0);
        spec = new TaintFlowSpec() {
            @Override
            public boolean isSource(DataFlowNode srcNode) {
                return srcNode
                        .asExpr(MethodAccess.class)
                        .map(MethodAccess::getSimpleName)
                        .map("source"::equals)
                        .orSome(false);
            }

            @Override
            public boolean isSink(DataFlowNode sinkNode) {
                return true;
            }
        };
    }

    @Benchmark
    public void taintFlowOverCompilationUnit(Blackhole bh) {
        bh.consume(new FindLocalFlowPaths<>(spec).visit(cu, 0));
    }
}
