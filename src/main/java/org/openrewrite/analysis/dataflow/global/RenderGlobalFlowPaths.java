package org.openrewrite.analysis.dataflow.global;

import lombok.AllArgsConstructor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.marker.SearchResult;

@AllArgsConstructor
public class RenderGlobalFlowPaths<P> extends JavaIsoVisitor<P> {
    GlobalDataFlow.Accumulator acc;

    @Override
    public Expression visitExpression(Expression expression, P p) {
        Expression e = super.visitExpression(expression, p);
        if (acc.isSource(getCursor())) {
            e = SearchResult.mergingFound(e, "source");
        }
        if (acc.isSink(getCursor())) {
            e = SearchResult.mergingFound(e, "sink");
        }
        if (expression != e) {
            return e;
        }
        if (acc.isFlowParticipant(getCursor())) {
            return SearchResult.found(e);
        }
        return e;
    }

    @Override
    public J.VariableDeclarations.NamedVariable visitVariable(J.VariableDeclarations.NamedVariable variable, P p) {
        J.VariableDeclarations.NamedVariable v = super.visitVariable(variable, p);
        if (acc.isSource(getCursor())) {
            v = SearchResult.mergingFound(v, "source");
        }
        if (acc.isSink(getCursor())) {
            v = SearchResult.mergingFound(v, "sink");
        }
        if (variable != v) {
            return v;
        }
        if (acc.isFlowParticipant(getCursor())) {
            return SearchResult.found(v);
        }
        return v;
    }
}
