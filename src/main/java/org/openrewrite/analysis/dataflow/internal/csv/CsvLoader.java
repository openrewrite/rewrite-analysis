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
package org.openrewrite.analysis.dataflow.internal.csv;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;

import java.io.*;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public class CsvLoader {
    public static <R extends Mergeable<R>, E> R loadFromFile(String csvFileName, R emptyModel, Function<Iterable<E>, R> merger, Function<String[], E> csvMapper) {
        AtomicReference<R> model = new AtomicReference<>(emptyModel);
        try (ScanResult scanResult = new ClassGraph().acceptPaths("data-flow").scan()) {
            scanResult.getResourcesWithLeafName(csvFileName)
                    .forEachInputStreamIgnoringIOException((res, input) -> model.set(model.get().merge(loadCsv(input, res.getURI(), merger, csvMapper))));
        }
        return model.get();
    }

    private static <R extends Mergeable<R>, E> R loadCsv(InputStream input, URI source, Function<Iterable<E>, R> merger, Function<String[], E> csvMapper) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(input));
        try {
            List<E> models = new ArrayList<>();
            //noinspection UnusedAssignment skip the header line
            String line = reader.readLine();
            while ((line = reader.readLine()) != null) {
                String[] tokens = parseLine(line);
                models.add(csvMapper.apply(tokens));
            }
            return merger.apply(models);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read data-flow values from " + source, e);
        }
    }

    private static String[] parseLine(String line) {
        if (line.isEmpty()) {
            return new String[0];
        }

        ArrayList<String> result = new ArrayList<>();
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == ',') {
                result.add("");
                continue;
            }
            int valueStart = i;
            char stopAt = ',';
            if (line.charAt(i) == '"') {
                valueStart = ++i;
                stopAt = '"';
            }
            while (i < line.length() && line.charAt(i) != stopAt) {
                i++;
            }
            result.add(line.substring(valueStart, i));
            if (stopAt == '"') {
                i++;
            }
        }
        return result.toArray(new String[0]);
    }

}
