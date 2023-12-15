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
package org.openrewrite.analysis.util;

import fj.data.Option;
import lombok.RequiredArgsConstructor;
import org.openrewrite.Cursor;
import org.openrewrite.Incubating;
import org.openrewrite.PrintOutputCapture;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaPrinter;
import org.openrewrite.java.tree.Comment;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.java.tree.Space;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@Incubating(since = "2.1.6")
public class CoordinateLocator {

    /**
     * Find the first element in the AST at the given line and column.
     * <p>
     * <strong>NOTE:</strong> line and column numbers are 1-based, which matches the behavior of most editors.
     * </p>
     *
     * @param sourceFile The source file to search.
     * @param line       The line number to search. 1-based.
     * @param column     The column number to search. 1-based.
     * @return The first element found at the given line and column, or {@link Option#none()} if no element was found.
     */
    public static Option<J> findCoordinate(JavaSourceFile sourceFile, int line, int column) {
        if (line < 1 || column < 1) {
            throw new IllegalArgumentException("Line and column numbers must be 1-based");
        }
        AtomicReference<J> found = new AtomicReference<>();
        CoordinateLocatorVisitor<Integer> locatorVisitor = new CoordinateLocatorVisitor<>(line, column, found);
        locatorVisitor.visit(
                sourceFile,
                locatorVisitor.new CoordinateLocatorPrinter(0), new Cursor(null, "root")
        );
        return Option.fromNull(found.get());
    }


    @RequiredArgsConstructor
    private static class CoordinateLocatorVisitor<P> extends JavaPrinter<P> {
        private final int line;
        private final int column;
        private final AtomicReference<J> found;
        private int foundLineNumber = 1;
        private int foundColumnNumber = 1;
        private boolean foundLine = false;
        private boolean foundColumn = false;

        @Override
        public @Nullable J preVisit(J tree, PrintOutputCapture<P> printOutputCapture) {
            if (foundLine && foundColumn && found.get() == null) {
                found.set(tree);
                stopAfterPreVisit();
                return tree;
            }
            return tree;
        }

        class CoordinateLocatorPrinter extends PrintOutputCapture<P> {

            public CoordinateLocatorPrinter(P p) {
                super(p);
            }

            @Override
            public PrintOutputCapture<P> append(@Nullable String text) {
                if (found.get() != null) {
                    // Optimization to avoid printing the rest of the file once we've found the element
                    return this;
                }
                if (text == null) {
                    return this;
                }
                for (int i = 0; i < text.length(); i++) {
                    append(text.charAt(i));
                }
                return this;
            }

            @Override
            public PrintOutputCapture<P> append(char c) {
                if (found.get() != null) {
                    // Optimization to avoid printing the rest of the file once we've found the element
                    return this;
                }
                if (isNewLine(c)) {
                    if (foundLine && !foundColumn) {
                        throw new IllegalStateException("Found line " + line + " but did not find column " + column);
                    }
                    foundLineNumber++;
                }
                if (foundLineNumber == line) {
                    foundLine = true;
                }
                if (foundLine && !isNewLine(c)) {
                    foundColumnNumber++;
                }
                if (foundLine && foundColumnNumber == column) {
                    foundColumn = true;
                }
                // Actually appending isn't necessary
                return this;
            }
        }
    }

    /**
     * Find all elements in the AST at the given line.
     * <p>
     * <strong>NOTE:</strong> line number is 1-based, which matches the behavior of most editors.
     * </p>
     *
     * @param sourceFile The source file to search.
     * @param line       The line number to search. 1-based.
     * @return The elements found at the given line, or an empty collection if no elements were found.
     */
    public static Collection<J> findLine(JavaSourceFile sourceFile, int line) {
        if (line < 1) {
            throw new IllegalArgumentException("Line numbers must be 1-based");
        }
        Set<J> found = Collections.newSetFromMap(new IdentityHashMap<>());
        LineLocator<Integer> locatorVisitor = new LineLocator<>(line, found);
        locatorVisitor.visit(
                sourceFile,
                locatorVisitor.new LineLocatorPrinter(0), new Cursor(null, "root")
        );
        return Collections.unmodifiableSet(found);
    }

    @RequiredArgsConstructor
    private static class LineLocator<P> extends JavaPrinter<P> {
        private final int line;
        private final Set<J> found;
        private int foundLineNumber = 1;

        private boolean foundLine() {
            return foundLineNumber == line;
        }

        @Override
        public @Nullable J preVisit(J tree, PrintOutputCapture<P> pPrintOutputCapture) {
            if (tree.getPrefix().getWhitespace().chars().anyMatch(CoordinateLocator::isNewLine)) {
                // If the element has a newline prefix, then it's on a new line
                return tree;
            }
            if (tree.getPrefix().getComments().stream().anyMatch(Comment::isMultiline)) {
                // If the element has a multiline comment prefix, then it's on a new line
                return tree;
            }
            if (foundLine()) {
                found.add(tree);
            }
            if (foundLineNumber > line) {
                // Optimization to avoid visiting the rest of the file once we've found the element
                stopAfterPreVisit();
                return tree;
            }
            return tree;
        }

        class LineLocatorPrinter extends PrintOutputCapture<P> {

            public LineLocatorPrinter(P p) {
                super(p);
            }

            @Override
            public PrintOutputCapture<P> append(@Nullable String text) {
                if (text == null) {
                    return this;
                }
                for (int i = 0; i < text.length(); i++) {
                    append(text.charAt(i));
                }
                return this;
            }

            @Override
            public PrintOutputCapture<P> append(char c) {
                if (isNewLine(c)) {
                    foundLineNumber++;
                }
                // Actually appending isn't necessary
                return this;
            }
        }
    }

    private static boolean isNewLine(int c) {
        return c == '\n' || c == '\r';
    }
}
