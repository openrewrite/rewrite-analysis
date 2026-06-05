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
package org.openrewrite.analysis.dataflow.internal.csv;

import org.junit.jupiter.api.Test;
import org.openrewrite.analysis.dataflow.internal.csv.AccessPath.CallbackKind;
import org.openrewrite.analysis.dataflow.internal.csv.AccessPath.Root;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AccessPathTest {

    @Test
    void flatArgument() {
        AccessPath ap = AccessPath.parse("Argument[0]").orElseThrow();
        assertThat(ap.getRoot()).isEqualTo(Root.ARGUMENT);
        assertThat(ap.getRootRange()).isEqualTo(new GenericExternalModel.ArgumentRange(0, 0));
        assertThat(ap.getCallbackKind()).isEqualTo(CallbackKind.NONE);
        assertThat(ap.isCallback()).isFalse();
    }

    @Test
    void qualifierArgument() {
        AccessPath ap = AccessPath.parse("Argument[-1]").orElseThrow();
        assertThat(ap.getRoot()).isEqualTo(Root.ARGUMENT);
        assertThat(ap.getRootRange()).isEqualTo(new GenericExternalModel.ArgumentRange(-1, -1));
        assertThat(ap.getCallbackKind()).isEqualTo(CallbackKind.NONE);
    }

    @Test
    void argumentRange() {
        AccessPath ap = AccessPath.parse("Argument[0..2]").orElseThrow();
        assertThat(ap.getRootRange()).isEqualTo(new GenericExternalModel.ArgumentRange(0, 2));
    }

    @Test
    void receiverThisIsQualifier() {
        // CodeQL's current dialect uses `Argument[this]` for the receiver/qualifier where the older
        // dialect used `Argument[-1]`; both must resolve to position -1.
        AccessPath ap = AccessPath.parse("Argument[this]").orElseThrow();
        assertThat(ap.getRoot()).isEqualTo(Root.ARGUMENT);
        assertThat(ap.getRootRange()).isEqualTo(new GenericExternalModel.ArgumentRange(-1, -1));
        assertThat(ap.getCallbackKind()).isEqualTo(CallbackKind.NONE);
    }

    @Test
    void receiverThisWithContentCollapses() {
        assertThat(AccessPath.parse("Argument[this].MapValue").orElseThrow().getRootRange())
                .isEqualTo(new GenericExternalModel.ArgumentRange(-1, -1));
    }

    @Test
    void argumentSetUnionIsContiguousRange() {
        // `Argument[this,0]` is MaD set-notation: the union {receiver, arg 0} = {-1, 0}, which is the
        // contiguous range [-1, 0]. (CodeQL: `Argument[x,y]` splits on `,`; `this` is position -1.)
        AccessPath ap = AccessPath.parse("Argument[this,0]").orElseThrow();
        assertThat(ap.getRoot()).isEqualTo(Root.ARGUMENT);
        assertThat(ap.getRootRange()).isEqualTo(new GenericExternalModel.ArgumentRange(-1, 0));
    }

    @Test
    void nonContiguousArgumentSetIsIgnored() {
        // A non-contiguous union (e.g. {0, 2}) cannot be represented as a single range without
        // over-approximating to include position 1, so it is conservatively unparseable.
        assertThat(AccessPath.parse("Argument[0,2]")).isEmpty();
    }

    @Test
    void returnValueRoot() {
        AccessPath ap = AccessPath.parse("ReturnValue").orElseThrow();
        assertThat(ap.getRoot()).isEqualTo(Root.RETURN_VALUE);
        assertThat(ap.getCallbackKind()).isEqualTo(CallbackKind.NONE);
    }

    @Test
    void contentCollapsesToContainer() {
        // Element / MapKey / MapValue / ArrayElement / Field / SyntheticField are collapsed away.
        assertThat(AccessPath.parse("Argument[-1].Element").orElseThrow().getRootRange())
                .isEqualTo(new GenericExternalModel.ArgumentRange(-1, -1));
        assertThat(AccessPath.parse("Argument[-1].Element").orElseThrow().getCallbackKind())
                .isEqualTo(CallbackKind.NONE);
        assertThat(AccessPath.parse("ReturnValue.Element").orElseThrow().getRoot())
                .isEqualTo(Root.RETURN_VALUE);
        assertThat(AccessPath.parse("Argument[0].SyntheticField[com.google.common.collect.Table.rowKey]").orElseThrow().getRootRange())
                .isEqualTo(new GenericExternalModel.ArgumentRange(0, 0));
        assertThat(AccessPath.parse("Argument[0].MapValue.Element").orElseThrow().getRootRange())
                .isEqualTo(new GenericExternalModel.ArgumentRange(0, 0));
    }

    @Test
    void callbackParameter() {
        AccessPath ap = AccessPath.parse("Argument[0].Parameter[1]").orElseThrow();
        assertThat(ap.getRoot()).isEqualTo(Root.ARGUMENT);
        assertThat(ap.getRootRange()).isEqualTo(new GenericExternalModel.ArgumentRange(0, 0));
        assertThat(ap.getCallbackKind()).isEqualTo(CallbackKind.PARAMETER);
        assertThat(ap.getCallbackRange()).isEqualTo(new GenericExternalModel.ArgumentRange(1, 1));
        assertThat(ap.isCallback()).isTrue();
    }

    @Test
    void callbackParameterWithTrailingContent() {
        AccessPath ap = AccessPath.parse("Argument[0].Parameter[0].Element").orElseThrow();
        assertThat(ap.getCallbackKind()).isEqualTo(CallbackKind.PARAMETER);
        assertThat(ap.getCallbackRange()).isEqualTo(new GenericExternalModel.ArgumentRange(0, 0));
    }

    @Test
    void callbackReturnValue() {
        AccessPath ap = AccessPath.parse("Argument[1].ReturnValue").orElseThrow();
        assertThat(ap.getRoot()).isEqualTo(Root.ARGUMENT);
        assertThat(ap.getRootRange()).isEqualTo(new GenericExternalModel.ArgumentRange(1, 1));
        assertThat(ap.getCallbackKind()).isEqualTo(CallbackKind.RETURN_VALUE);
        assertThat(ap.isCallback()).isTrue();
    }

    @Test
    void callbackReturnValueWithTrailingContent() {
        AccessPath ap = AccessPath.parse("Argument[1].ReturnValue.Element").orElseThrow();
        assertThat(ap.getCallbackKind()).isEqualTo(CallbackKind.RETURN_VALUE);
    }

    @Test
    void emptyIsUnparseable() {
        assertThat(AccessPath.parse("")).isEmpty();
    }

    @Test
    void unknownRootIsUnparseable() {
        assertThat(AccessPath.parse("Bogus")).isEmpty();
    }
}
