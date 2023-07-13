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
import lombok.NoArgsConstructor;
import org.openrewrite.Cursor;
import org.openrewrite.java.tree.J;

import java.util.Iterator;

@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public class CursorUtil {

    public static Option<Cursor> findCallableBlockCursor(Cursor start) {
        Iterator<Cursor> cursorPath = start.getPathAsCursors();
        Cursor methodDeclarationBlockCursor = null;
        while (cursorPath.hasNext()) {
            Cursor nextCursor = cursorPath.next();
            Object next = nextCursor.getValue();
            if (next instanceof J.Block) {
                methodDeclarationBlockCursor = nextCursor;
                if (J.Block.isStaticOrInitBlock(nextCursor)) {
                    return Option.some(nextCursor);
                }
            } else if (next instanceof J.MethodDeclaration) {
                if (methodDeclarationBlockCursor == null && ((J.MethodDeclaration) next).getBody() != null) {
                    methodDeclarationBlockCursor = new Cursor(nextCursor, ((J.MethodDeclaration) next).getBody());
                }
                return Option.some(methodDeclarationBlockCursor);
            }
        }
        return Option.none();
    }

}
