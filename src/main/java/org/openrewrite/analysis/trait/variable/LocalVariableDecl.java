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
package org.openrewrite.analysis.trait.variable;

import fj.data.Option;
import fj.data.Validation;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.openrewrite.Cursor;
import org.openrewrite.Tree;
import org.openrewrite.analysis.trait.Top;
import org.openrewrite.analysis.trait.TraitFactory;
import org.openrewrite.analysis.trait.expr.Expr;
import org.openrewrite.analysis.trait.expr.VarAccess;
import org.openrewrite.analysis.trait.member.Callable;
import org.openrewrite.analysis.trait.member.InstanceInitializer;
import org.openrewrite.analysis.trait.member.Method;
import org.openrewrite.analysis.trait.member.StaticInitializerMethod;
import org.openrewrite.analysis.trait.util.TraitErrors;
import org.openrewrite.analysis.util.FlagUtil;
import org.openrewrite.java.tree.*;

import java.util.Collection;
import java.util.Iterator;
import java.util.UUID;

/**
 * A local variable declaration
 */
public interface LocalVariableDecl extends LocalScopeVariable {

    enum Factory implements TraitFactory<LocalVariableDecl> {
        F;

        @Override
        public Validation<TraitErrors, LocalVariableDecl> viewOf(Cursor cursor) {
            if (cursor.getValue() instanceof J.VariableDeclarations.NamedVariable) {
                Cursor maybeVariableDecl = cursor.getParentTreeCursor();
                return LocalVariableDeclBase.findNearestParentCallable(cursor)
                        .map(callable -> new LocalVariableDeclBase(cursor, cursor.getValue(), maybeVariableDecl.getValue(), callable));
            }
            return TraitErrors.invalidTraitCreationType(LocalVariableDecl.class, cursor, J.VariableDeclarations.class);
        }
    }

    static Validation<TraitErrors, LocalVariableDecl> viewOf(Cursor cursor) {
        return Factory.F.viewOf(cursor);
    }
}

@AllArgsConstructor
class LocalVariableDeclBase extends Top.Base implements LocalVariableDecl {
    final Cursor cursor;
    final J.VariableDeclarations.NamedVariable variable;
    final J.VariableDeclarations variableDeclarations;
    @Getter(onMethod = @__(@Override))
    final Callable callable;

    @Override
    public String getName() {
        return variable.getSimpleName();
    }

    @Override
    public UUID getId() {
        return variable.getId();
    }

    @Override
    public Option<JavaType> getType() {
        return Option.fromNull(variable.getType());
    }

    @Override
    public Collection<VarAccess> getVarAccesses() {
        return VarAccess.findAllInScope(cursor.dropParentUntil(JavaSourceFile.class::isInstance), this);
    }

    @Override
    public Collection<Expr> getAssignedValues() {
        return VariableUtil.findAssignedValues(cursor.dropParentUntil(JavaSourceFile.class::isInstance), this);
    }

    @Override
    public Collection<Flag> getFlags() {
        return FlagUtil.fromModifiers(variableDeclarations.getModifiers());
    }

    static Validation<TraitErrors, Callable> findNearestParentCallable(Cursor cursor) {
        Iterator<Cursor> path = cursor.getPathAsCursors(c -> c.getValue() instanceof J);
        Cursor previous = null;
        while (path.hasNext()) {
            Cursor c = path.next();
            Tree t = c.getValue();
            if (t instanceof J.ClassDeclaration || t instanceof J.NewClass) {
                break;
            }
            if (t instanceof J.Block && J.Block.isStaticOrInitBlock(c)) {
                J.Block block = (J.Block) t;
                if (block.isStatic()) {
                    return StaticInitializerMethod.viewOf(c.getParentTreeCursor()).map(m -> m);
                } else {
                    return InstanceInitializer.viewOf(c.getParentTreeCursor()).map(m -> m);
                }
            }
            if (t instanceof J.MethodDeclaration) {
                J.MethodDeclaration m = (J.MethodDeclaration) t;
                assert previous != null : "previous should not be null";
                if (m.getParameters().contains(previous.<Statement>getValue())) {
                    break;
                }
            }
            Validation<TraitErrors, Method> v = Method.viewOf(c);
            if (v.isSuccess()) {
                return v.map(m -> m);
            }
            previous = c;
        }
        return TraitErrors.invalidTraitCreationError("No parent Method found");
    }
}
