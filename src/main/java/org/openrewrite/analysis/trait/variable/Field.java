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
import org.openrewrite.Cursor;
import org.openrewrite.analysis.trait.Top;
import org.openrewrite.analysis.trait.TraitFactory;
import org.openrewrite.analysis.trait.expr.Expr;
import org.openrewrite.analysis.trait.expr.VarAccess;
import org.openrewrite.analysis.trait.member.FieldDeclaration;
import org.openrewrite.analysis.trait.member.Member;
import org.openrewrite.analysis.trait.util.TraitErrors;
import org.openrewrite.analysis.util.FlagUtil;
import org.openrewrite.java.tree.Flag;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.java.tree.JavaType;

import java.util.Collection;
import java.util.UUID;

/**
 * A class or instance field.
 */
public interface Field extends Member, Variable {
    /**
     * Gets the field declaration in which this field is declared.
     * <p/>
     * Note that this declaration is only available if the field occurs in source code.
     */
    Option<FieldDeclaration> getDeclaration();

    enum Factory implements TraitFactory<Field> {
        F;

        @Override
        public Validation<TraitErrors, Field> viewOf(Cursor cursor) {
            if (cursor.getValue() instanceof J.VariableDeclarations.NamedVariable) {
                Cursor maybeVariableDecl = cursor.getParentTreeCursor();
                Cursor maybeBlock = maybeVariableDecl.getParentTreeCursor();
                Cursor maybeClassDecl = maybeBlock.getParentTreeCursor();
                if (maybeClassDecl.getValue() instanceof J.ClassDeclaration ||
                    maybeClassDecl.getValue() instanceof J.NewClass && ((J.NewClass) maybeClassDecl.getValue()).getBody() != null) {
                    return Validation.success(new FieldFromCursor(cursor, cursor.getValue(), maybeVariableDecl.getValue(), maybeBlock));
                }
                return TraitErrors.invalidTraitCreationError("Field must be declared in a class, interface, or anonymous class");
            }
            return TraitErrors.invalidTraitCreationType(Field.class, cursor, J.VariableDeclarations.NamedVariable.class);
        }
    }

    static Validation<TraitErrors, Field> viewOf(Cursor cursor) {
        return Factory.F.viewOf(cursor);
    }
}

@AllArgsConstructor
class FieldFromCursor extends Top.Base implements Field {
    Cursor cursor;
    J.VariableDeclarations.NamedVariable variable;
    J.VariableDeclarations variableDeclarations;
    Cursor parentBlock;

    @Override
    public String getName() {
        return variable.getSimpleName();
    }

    @Override
    public UUID getId() {
        return variable.getId();
    }

    @Override
    public Option<FieldDeclaration> getDeclaration() {
        return Option.none();
    }

    @Override
    public Option<JavaType> getType() {
        return Option.fromNull(variable.getType());
    }

    @Override
    public Collection<VarAccess> getVarAccesses() {
        // Searching starts at the J.CompilationUnit because we want to find all references to this field, within the file,
        // not just within the class (which may contain multiple classes).
        Cursor searchScope = parentBlock.dropParentUntil(JavaSourceFile.class::isInstance);
        return VarAccess.findAllInScope(searchScope, this);
    }

    @Override
    public Collection<Expr> getAssignedValues() {
        return VariableUtil.findAssignedValues(parentBlock.dropParentUntil(JavaSourceFile.class::isInstance), this);
    }

    @Override
    public Collection<Flag> getFlags() {
        return FlagUtil.fromModifiers(variableDeclarations.getModifiers());
    }
}
