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
package org.openrewrite.analysis.dataflow.analysis;

import fj.data.Option;
import lombok.AllArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.Incubating;
import org.openrewrite.Tree;
import org.openrewrite.analysis.InvocationMatcher;
import org.openrewrite.analysis.dataflow.CallbackFlowModel;
import org.openrewrite.analysis.dataflow.DataFlowNode;
import org.openrewrite.analysis.dataflow.DataFlowSpec;
import org.openrewrite.analysis.dataflow.internal.LambdaReturns;
import org.openrewrite.analysis.trait.expr.Call;
import org.openrewrite.analysis.trait.expr.VarAccess;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;

import java.util.*;
import java.util.stream.Stream;

import static java.util.Collections.*;
import static java.util.stream.Collectors.toSet;

@Incubating(since = "7.24.0")
public class ForwardFlow extends JavaVisitor<Integer> {

    public static FlowGraph findAllFlows(DataFlowNode node, DataFlowSpec spec, FlowGraph.Factory factory) {
        FlowGraph graph = factory.create(node);
        findAllFlows(graph, spec);
        return graph;
    }

    public static void findAllFlows(FlowGraph root, DataFlowSpec spec) {
        VariableNameToFlowGraph variableNameToFlowGraph =
                computeVariableAssignment(root.getNode().getCursor(), root, spec);
        if (variableNameToFlowGraph.identifierToFlow.isEmpty()) {
            return;
        }
        // The parent statement of the source. Data flow can not start before the source.
        Object taintStmt = null;
        Cursor taintStmtCursorParentParent = null;
        Cursor taintStmtCursorParent = null;
        if (variableNameToFlowGraph.currentCursor != null && variableNameToFlowGraph.currentCursor.getValue() instanceof J) {
            taintStmt = variableNameToFlowGraph.currentCursor.getValue();
            taintStmtCursorParentParent = variableNameToFlowGraph.currentCursor.getParent();
            taintStmtCursorParent = variableNameToFlowGraph.currentCursor;
        }
        boolean rootIsParameter = root.getNode().isParameter();
        Iterator<Cursor> remainingPath = variableNameToFlowGraph.remainingCursorPath;
        while (remainingPath.hasNext()) {
            taintStmtCursorParentParent = remainingPath.next();
            Object next = taintStmtCursorParentParent.getValue();
            if (next instanceof J.Block) {
                break;
            }
            // A lambda parameter is in scope within the lambda body, which is reached by walking up
            // through the lambda (NamedVariable -> VariableDeclarations -> Lambda.Parameters -> Lambda)
            // rather than down into a method body. Stop at the lambda so its body becomes the scope.
            if (rootIsParameter && next instanceof J.Lambda) {
                taintStmt = next;
                taintStmtCursorParent = taintStmtCursorParentParent;
                break;
            }
            if (next instanceof J) {
                taintStmt = next;
                taintStmtCursorParent = taintStmtCursorParentParent;
            }
        }

        Analysis analysis = new Analysis(spec, variableNameToFlowGraph.identifierToFlow.copy());
        if (taintStmtCursorParent == null) {
            throw new IllegalStateException("`taintStmtCursorParent` is null. Computing flow starting at " + root.getNode().getCursor().getValue());
        }
        if (taintStmt instanceof J.WhileLoop ||
            taintStmt instanceof J.DoWhileLoop ||
            taintStmt instanceof J.ForLoop) {
            // This occurs when an assignment occurs within the control parenthesis of a loop
            Statement body;
            if (taintStmt instanceof J.WhileLoop) {
                assert taintStmtCursorParent.getValue() instanceof J.WhileLoop : "taintStmtCursorParent is not a while loop";
                body = ((J.WhileLoop) taintStmt).getBody();
            } else if (taintStmt instanceof J.DoWhileLoop) {
                assert taintStmtCursorParent.getValue() instanceof J.DoWhileLoop : "taintStmtCursorParent is not a do while loop";
                body = ((J.DoWhileLoop) taintStmt).getBody();
            } else {
                assert taintStmtCursorParent.getValue() instanceof J.ForLoop : "taintStmtCursorParent is not a for loop";
                body = ((J.ForLoop) taintStmt).getBody();
            }
            analysis.visit(body, 0, taintStmtCursorParent);
        } else if (taintStmt instanceof J.Try) {
            J.Try _try = (J.Try) taintStmt;
            analysis.visit(_try.getBody(), 0, taintStmtCursorParent);
            analysis.visit(_try.getFinally(), 0, taintStmtCursorParent);
        } else if (taintStmt instanceof J.MethodDeclaration) {
            J.MethodDeclaration methodDeclaration = (J.MethodDeclaration) taintStmt;
            assert taintStmtCursorParent.getValue() instanceof J.MethodDeclaration : "taintStmtCursorParent is not a method declaration";
            analysis.visit(methodDeclaration.getBody(), 0, taintStmtCursorParent);
        } else if (taintStmt instanceof J.Lambda) {
            // The source is a lambda parameter; flow begins in the lambda body, which may be a
            // single expression or a block.
            J.Lambda lambda = (J.Lambda) taintStmt;
            assert taintStmtCursorParent.getValue() instanceof J.Lambda : "taintStmtCursorParent is not a lambda";
            analysis.visit(lambda.getBody(), 0, taintStmtCursorParent);
        } else {
            // This is when assignment occurs within the body of a block
            assert taintStmt != null : "taintStmt is null";
            Cursor c = root.getNode().getCursor().dropParentUntil(v -> v instanceof J.Block || v instanceof J.CompilationUnit);
            if (c.getValue() instanceof J.Block) {
                visitBlocksRecursive(c, taintStmt, analysis);
            }
        }
    }

    /**
     * @param blockCursor    The cursor for the current {@link J.Block} being explored.
     * @param startStatement The statement to start looking for flow from. Should not start before this point.
     * @param analysis       The analysis visitor to use.
     */
    private static void visitBlocksRecursive(Cursor blockCursor, Object startStatement, Analysis analysis) {
        boolean seenRoot = false;
        J.Block block = blockCursor.getValue();
        final List<String> declaredVariables = new ArrayList<>();
        for (Statement statement : block.getStatements()) {
            if (statement instanceof J.VariableDeclarations) {
                J.VariableDeclarations variableDeclarations = (J.VariableDeclarations) statement;
                for (J.VariableDeclarations.NamedVariable variableDeclaration : variableDeclarations.getVariables()) {
                    declaredVariables.add(variableDeclaration.getSimpleName());
                }
            }
            if (seenRoot) {
                analysis.visit(statement, 0, blockCursor);
            }
            if (statement == startStatement) {
                seenRoot = true;
            }
        }
        J.MethodDeclaration parentMethodDeclaration = blockCursor.firstEnclosing(J.MethodDeclaration.class);
        if (parentMethodDeclaration != null && parentMethodDeclaration.getBody() == block) {
            // This block is the body of a method, so we don't need to visit any higher
            return;
        }
        J.Block parentBlock = blockCursor.getParentOrThrow().firstEnclosing(J.Block.class);
        if (parentBlock != null && parentBlock.getStatements().contains(block) &&
            J.Block.isStaticOrInitBlock(blockCursor)) {
            // This block is the body of a static block or an init block, so we don't need to visit any higher
            return;
        }

        // Remove any variables that were declared in this block
        declaredVariables.forEach(analysis.flowsByIdentifier.peek()::remove);

        // Get the parent J
        J nextStartStatement = blockCursor.getParentOrThrow().firstEnclosing(J.class);
        if (nextStartStatement instanceof J.Block && ((J.Block) nextStartStatement).getStatements().contains(block)) {
            // If the parent J is a block, and the current block is a statement in the of the parent J,
            // then use it as the starting point.
            nextStartStatement = block;
        } else if (nextStartStatement == null || !getPossibleSubBlock(nextStartStatement).contains(block)) {
            // We found *a* parent J, but it wasn't a parent J that we should use as a starting point.
            return;
        }
        visitBlocksRecursive(blockCursor.dropParentUntil(J.Block.class::isInstance), nextStartStatement, analysis);
    }

    private static Set<Statement> getPossibleSubBlock(J j) {
        if (j instanceof J.If) {
            J.If _if = (J.If) j;
            if (_if.getElsePart() != null) {
                return Stream.of(_if.getThenPart(), _if.getElsePart().getBody()).collect(toSet());
            }
            return singleton(_if.getThenPart());
        }
        if (j instanceof J.WhileLoop) {
            return singleton(((J.WhileLoop) j).getBody());
        }
        if (j instanceof J.DoWhileLoop) {
            return singleton(((J.DoWhileLoop) j).getBody());
        }
        if (j instanceof J.ForLoop) {
            return singleton(((J.ForLoop) j).getBody());
        }
        if (j instanceof J.ForEachLoop) {
            return singleton(((J.ForEachLoop) j).getBody());
        }
        if (j instanceof J.Try) {
            J.Try _try = (J.Try) j;
            return Stream.concat(
                    Stream.of(_try.getBody(), _try.getFinally()),
                    _try.getCatches().stream().map(J.Try.Catch::getBody)
            )
            .collect(toSet());
        }
        return emptySet();
    }

    @AllArgsConstructor
    static class IdentifierToFlows {
        private final Map<String, Set<FlowGraph>> identifierToFlows;

        public IdentifierToFlows() {
            this(new HashMap<>());
        }

        public void put(String identifier, FlowGraph flow) {
            identifierToFlows.computeIfAbsent(identifier, k -> newSetFromMap(new IdentityHashMap<>())).add(flow);
        }

        public void putAll(IdentifierToFlows other) {
            other.identifierToFlows.forEach((identifier, flows) -> flows.forEach(flow -> put(identifier, flow)));
        }

        public FlowGraph addForIdentifierVisit(String identifier, Cursor cursor) {
            if (!hasFlows(identifier)) {
                throw new IllegalArgumentException("No flows for identifier " + identifier);
            }
            Iterator<FlowGraph> iterator = get(identifier).iterator();
            FlowGraph flow = iterator.next();
            DataFlowNode flowNode = DataFlowNode.ofOrThrow(cursor, "identifier is not a DataFlowNode: " + cursor);
            // Create a FlowGraph for the current identifier being visited
            FlowGraph newFlowGraph = flow.addEdge(flowNode);
            while (iterator.hasNext()) {
                // Add edges to all other flows for this identifier, pointing all existing flows to the new flow
                FlowGraph next = iterator.next();
                next.addEdge(newFlowGraph);
            }
            // Replace the existing flows with the new flow
            identifierToFlows.get(identifier).clear();
            put(identifier, newFlowGraph);
            return newFlowGraph;
        }

        public Set<FlowGraph> get(String identifier) {
            return identifierToFlows.getOrDefault(identifier, emptySet());
        }

        public boolean hasFlows(String identifier) {
            return identifierToFlows.containsKey(identifier);
        }

        public Set<FlowGraph> remove(String identifier) {
            return identifierToFlows.remove(identifier);
        }

        public boolean isEmpty() {
            return identifierToFlows.isEmpty();
        }

        public IdentifierToFlows copy() {
            Map<String, Set<FlowGraph>> newIdentifierToFlows = new HashMap<>();
            identifierToFlows.forEach((identifier, flows) -> newIdentifierToFlows.put(identifier, new HashSet<>(flows)));
            return new IdentifierToFlows(newIdentifierToFlows);
        }
    }

    private static class Analysis extends JavaVisitor<Integer> {
        final DataFlowSpec dataFlowSpec;
        Deque<IdentifierToFlows> flowsByIdentifier = new ArrayDeque<>();

        Analysis(DataFlowSpec dataFlowSpec, IdentifierToFlows initial) {
            this.dataFlowSpec = dataFlowSpec;
            this.flowsByIdentifier.push(initial);
        }

        @Override
        public J visitVariable(J.VariableDeclarations.NamedVariable variable, Integer p) {
            // a new variable declaration kills existing taints
            flowsByIdentifier.peek().remove(variable.getSimpleName());
            return super.visitVariable(variable, p);
        }

        @Override
        public J visitLambda(J.Lambda lambda, Integer p) {
            for (J parameter : lambda.getParameters().getParameters()) {
                new JavaIsoVisitor<Integer>() {
                    @Override
                    public J.Identifier visitIdentifier(J.Identifier identifier, Integer integer) {
                        flowsByIdentifier.peek().remove(identifier.getSimpleName());
                        return identifier;
                    }
                }.visit(parameter, 0);
            }

            return super.visitLambda(lambda, p);
        }

        @Override
        public J visitIdentifier(J.Identifier ident, Integer p) {
            // The identifier must be a variable access to be used in a flow
            if (VarAccess.viewOf(getCursor()).map(va -> !va.isRValue()).orSuccess(true)) {
                return ident;
            }
            // If the identifier is a field access then it is not local flow
            J.FieldAccess parentFieldAccess = getCursor().firstEnclosing(J.FieldAccess.class);
            if (parentFieldAccess != null && parentFieldAccess.getName() == ident) {
                return ident;
            }

            if (flowsByIdentifier.peek().hasFlows(ident.getSimpleName())) {

                FlowGraph next = flowsByIdentifier.peek().addForIdentifierVisit(ident.getSimpleName(), getCursor());

                VariableNameToFlowGraph variableNameToFlowGraph =
                        computeVariableAssignment(getCursor(), next, dataFlowSpec);

                if (!variableNameToFlowGraph.identifierToFlow.isEmpty()) {
                    flowsByIdentifier.peek().putAll(variableNameToFlowGraph.identifierToFlow);
                }
            }
            return ident;
        }

        @Override
        public J visitBlock(J.Block block, Integer p) {
            flowsByIdentifier.push(flowsByIdentifier.peek().copy());
            J b = super.visitBlock(block, p);
            flowsByIdentifier.pop();
            return b;
        }

        @Override
        public J visitAssignment(J.Assignment assignment, Integer integer) {
            J.Assignment a = (J.Assignment) super.visitAssignment(assignment, integer);
            Expression left = a.getVariable().unwrap();
            if (left instanceof J.Identifier) {
                String variableName = ((J.Identifier) left).getSimpleName();
                if (flowsByIdentifier.peek().hasFlows(variableName) &&
                    flowsByIdentifier.peek().get(variableName).stream().allMatch(v -> v.getNode().getCursor().getValue() != a.getAssignment())) {
                    flowsByIdentifier.peek().remove(variableName);
                }
            }
            return a;
        }

        @Override
        public J visitMethodDeclaration(J.MethodDeclaration method, Integer p) {
            // Handle method declarations inside anonymous classes
            // Remove flows for method parameters that shadow outer variables
            for (Statement param : method.getParameters()) {
                new JavaIsoVisitor<Integer>() {
                    @Override
                    public J.Identifier visitIdentifier(J.Identifier identifier, Integer integer) {
                        flowsByIdentifier.peek().remove(identifier.getSimpleName());
                        return identifier;
                    }
                }.visit(param, 0);
            }
            return super.visitMethodDeclaration(method, p);
        }

        @Override
        public J visitNewClass(J.NewClass newClass, Integer integer) {
            // For anonymous classes, explicitly visit the body to ensure
            // dataflow tracking through captured variables from the enclosing scope.
            // The default visitor may not traverse the anonymous class body
            // in a way that allows captured variables to be tracked.
            J.Block body = newClass.getBody();
            if (body != null) {
                // Push a new scope for the anonymous class body
                flowsByIdentifier.push(flowsByIdentifier.peek().copy());
                try {
                    // Create a proper cursor chain: current -> newClass -> body
                    Cursor newClassCursor = new Cursor(getCursor(), newClass);
                    Cursor bodyCursor = new Cursor(newClassCursor, body);
                    // Visit the body statements with the correct cursor chain
                    for (Statement statement : body.getStatements()) {
                        visit(statement, integer, bodyCursor);
                    }
                } finally {
                    flowsByIdentifier.pop();
                }
            }
            // Visit non-body parts (arguments, enclosing)
            return super.visitNewClass(newClass, integer);
        }
    }

    @AllArgsConstructor
    private static final class VariableNameToFlowGraph {
        /**
         * A map of variable names to the flow graph that represents the flow to that variable.
         * <p/>
         * <ul>
         *     <li>For statements that do not terminate in an assignment, this will be an empty map.</li>
         *     <li>For statements that terminate in an assignment, this will be a map of the variable name to the flow graph that created it. Usually meaning the map only has one element.</li>
         *     <li>For statements where data/taint flow occurs to the subject/qualifier of a {@link J.MethodInvocation}, the map can be larger than one element.<li/>
         * </ul>
         */
        IdentifierToFlows identifierToFlow;
        Cursor currentCursor;
        Iterator<Cursor> remainingCursorPath;
    }

    private static VariableNameToFlowGraph computeVariableAssignment(Cursor startCursor, FlowGraph currentFlow, DataFlowSpec spec) {
        Iterator<Cursor> cursorPath = startCursor.getPathAsCursors(c -> c.getValue() instanceof J);
        Cursor ancestorCursor = null;
        if (cursorPath.hasNext()) {
            // Must avoid inspecting the 'current' node to compute the variable assignment.
            // This is because we perform filtering here, and filtered types may be valid 'source' types.
            ancestorCursor = cursorPath.next();
            if (currentFlow.getNode().asParameter().isSome()) {
                // If currentFlow is a parameter, then the name of the parameter is the only flow.
                // The cursor path will contain no additional variable names where flow can occur.
                IdentifierToFlows identifierToFlows = new IdentifierToFlows();
                identifierToFlows.put(currentFlow.getNode().asParameter().some().getName(), currentFlow);
                // If the current flow is a parameter, then we can't have any additional flows.
                return new VariableNameToFlowGraph(identifierToFlows, currentFlow.getNode().getCursor(), cursorPath);
            }
        }
        IdentifierToFlows identifierToFlow = new IdentifierToFlows();
        FlowGraph nextFlowGraph = currentFlow;
        while (cursorPath.hasNext()) {
            ancestorCursor = cursorPath.next();
            Object ancestor = ancestorCursor.getValue();

            Option<DataFlowNode> maybeAncestorNode = DataFlowNode.of(ancestorCursor);
            if (maybeAncestorNode.isSome()) {
                DataFlowNode ancestorNode = maybeAncestorNode.some();
                if (ancestorNode.asParameter().isSome()) {
                    continue;
                }
                // Offer the cursor of the current flow graph, and a next possible expression to
                // `isAdditionalFlowStep` to see if it should be added to the flow graph.
                // This allows the API user to extend what the definition of 'flow' is.
                Cursor previousCursor = nextFlowGraph.getNode().getCursor();
                if (spec.isBarrier(
                        ancestorNode
                )) {
                    break;
                }

                Cursor parentCursor = previousCursor.getParentTreeCursor();
                if (parentCursor.getValue() instanceof J.MethodInvocation) {
                    // The parent is a MethodInvocation, `previousCursor` must be either an argument or the select
                    J.MethodInvocation methodInvocation = parentCursor.getValue();
                    // Support flow from any argument to the subject of a method invocation
                    if (methodInvocation.getSelect() != null && methodInvocation.getArguments().contains(previousCursor.getValue())) {
                        Cursor selectCursor = new Cursor(parentCursor, methodInvocation.getSelect());
                        // Select may not be a data flow node if it's a static access
                        Option<DataFlowNode> selectNode = DataFlowNode.of(selectCursor);
                        if (selectNode.isSome() && spec.isFlowStep(
                                DataFlowNode.ofOrThrow(previousCursor, "Unable to create DataFlowNode for " + previousCursor),
                                selectNode.some()
                        )) {
                            nextFlowGraph = nextFlowGraph.addEdge(selectNode.some());
                            Expression unwrappedSelect = methodInvocation.getSelect().unwrap();
                            VariableNameToFlowGraph variableNameToFlowGraph =
                                    computeVariableAssignment(selectCursor, nextFlowGraph, spec);
                            if (unwrappedSelect instanceof J.Identifier) {
                                // If the select is an identifier, then we can add it to the map of variable names to flow graphs
                                String variableName = ((J.Identifier) unwrappedSelect).getSimpleName();
                                variableNameToFlowGraph.identifierToFlow.put(variableName, nextFlowGraph);
                            }
                            return variableNameToFlowGraph;
                        }
                    }

                    // Flow from one argument or the select to another argument
                    if (methodInvocation.getArguments().contains(previousCursor.getValue()) ||
                        methodInvocation.getSelect() == previousCursor.getValue()) {
                        for (Expression expr : methodInvocation.getArguments()) {
                            if (expr.equals(previousCursor.getValue())) {
                                // There is no flow to itself
                                continue;
                            }

                            Cursor argumentCursor = new Cursor(parentCursor, expr);
                            DataFlowNode argumentNode = DataFlowNode.ofOrThrow(argumentCursor, "Unable to create DataFlowNode for " + argumentCursor);

                            if (spec.isFlowStep(
                                    DataFlowNode.ofOrThrow(previousCursor, "Unable to create DataFlowNode for " + previousCursor),
                                    argumentNode
                            )) {
                                nextFlowGraph = nextFlowGraph.addEdge(argumentNode);
                                Expression unwrappedArgument = expr.unwrap();
                                VariableNameToFlowGraph variableNameToFlowGraph =
                                        computeVariableAssignment(argumentCursor, nextFlowGraph, spec);
                                if (unwrappedArgument instanceof J.Identifier) {
                                    // If the argument is an identifier, then we can add it to the map of variable names to flow graphs
                                    String variableName = ((J.Identifier) unwrappedArgument).getSimpleName();
                                    variableNameToFlowGraph.identifierToFlow.put(variableName, nextFlowGraph);
                                }
                                return variableNameToFlowGraph;
                            }
                        }
                    }

                    // Into-callback ("lambda call"): route flow from the select or an argument into a
                    // parameter of a lambda passed as another argument, per any matching model (e.g.
                    // Iterable.forEach passes the receiver's elements to the consumer's parameter).
                    if (tryIntoCallbackFlow(nextFlowGraph, parentCursor, previousCursor, spec)) {
                        break;
                    }
                } else if (parentCursor.getValue() instanceof J.NewClass) {
                    // The parent is a J.NewClass, `previousCursor` must be an argument
                    J.NewClass constructorInvocation = parentCursor.getValue();

                    // Flow from one argument to another argument
                    if (constructorInvocation.getArguments().contains(previousCursor.getValue())) {
                        for (Expression expr : constructorInvocation.getArguments()) {
                            if (expr.equals(previousCursor.getValue())) {
                                // There is no flow to itself
                                continue;
                            }

                            Cursor argumentCursor = new Cursor(parentCursor, expr);
                            DataFlowNode argumentNode = DataFlowNode.ofOrThrow(argumentCursor, "Unable to create DataFlowNode for " + argumentCursor);

                            if (spec.isFlowStep(
                                    DataFlowNode.ofOrThrow(previousCursor, "Unable to create DataFlowNode for " + previousCursor),
                                    argumentNode
                            )) {
                                nextFlowGraph = nextFlowGraph.addEdge(argumentNode);
                                Expression unwrappedArgument = expr.unwrap();
                                VariableNameToFlowGraph variableNameToFlowGraph =
                                        computeVariableAssignment(argumentCursor, nextFlowGraph, spec);
                                if (unwrappedArgument instanceof J.Identifier) {
                                    // If the argument is an identifier, then we can add it to the map of variable names to flow graphs
                                    String variableName = ((J.Identifier) unwrappedArgument).getSimpleName();
                                    variableNameToFlowGraph.identifierToFlow.put(variableName, nextFlowGraph);
                                }
                                return variableNameToFlowGraph;
                            }
                        }
                    }
                }
                if (spec.isFlowStep(
                        DataFlowNode.ofOrThrow(previousCursor, "Unable to create DataFlowNode for " + previousCursor),
                        ancestorNode
                )) {
                    nextFlowGraph = nextFlowGraph.addEdge(ancestorNode);
                    Tree ancestorParent = ancestorCursor.getParentTreeCursor().getValue();
                    if (ancestorParent instanceof J.Block || ancestorParent instanceof J.Case) {
                        // If the ancestor is a block or a case, then we've reached the end of the flow.
                        // We can stop here.
                        // This is important to ensure we retain the remaining `cursorPath` for the
                        // `Analysis` to have a valid starting point when a flow passes from argument to subject
                        break;
                    } else {
                        // Continue to the next ancestor
                        continue;
                    }
                }

                if (ancestor instanceof J.Ternary) {
                    J.Ternary ternary = (J.Ternary) ancestor;
                    Object previousCursorValue = nextFlowGraph.getNode().getCursor().getValue();
                    if (ternary.getTruePart() == previousCursorValue ||
                        ternary.getFalsePart() == previousCursorValue) {
                        nextFlowGraph = nextFlowGraph.addEdge(ancestorNode);
                        continue;
                    } else {
                        // Data flow does not occur from the ternary conditional part
                        break;
                    }
                } else if (ancestor instanceof J.TypeCast ||
                           ancestor instanceof J.Parentheses ||
                           ancestor instanceof J.ControlParentheses) {
                    Cursor parent = ancestorCursor.getParentOrThrow();
                    if (parent.getValue() instanceof J.Switch || parent.getValue() instanceof J.SwitchExpression) {
                        // Don't add control flow to control parentheses in switch statements
                        break;
                    }
                    nextFlowGraph = nextFlowGraph.addEdge(ancestorNode);
                    continue;
                }
            }

            if (ancestor instanceof J.Binary) {
                break;
            } else if (ancestor instanceof J.MethodInvocation || ancestor instanceof J.NewClass) {
                // Out-of-callback ("lambda call"): if the current flow node is the result of a lambda
                // passed as an argument of this call, route flow to the call's output node per any
                // matching out-of-callback model (e.g. Map.computeIfAbsent maps the mapping function's
                // return value to the call result).
                tryOutOfCallbackFlow(nextFlowGraph, ancestorCursor, spec);
                break;
            } else if (ancestor instanceof J.Assignment ||
                       ancestor instanceof J.AssignmentOperation ||
                       ancestor instanceof J.VariableDeclarations.NamedVariable
            ) {
                Expression variable;
                if (ancestor instanceof J.Assignment) {
                    variable = ((J.Assignment) ancestor).getVariable();
                } else if (ancestor instanceof J.AssignmentOperation) {
                    variable = ((J.AssignmentOperation) ancestor).getVariable();
                } else {
                    variable = ((J.VariableDeclarations.NamedVariable) ancestor).getName();
                }
                variable = variable.unwrap();
                if (variable instanceof J.Identifier) {
                    String nextVariableName = ((J.Identifier) variable).getSimpleName();
                    identifierToFlow.put(nextVariableName, nextFlowGraph);
                    break;
                }
            }
        }
        return new VariableNameToFlowGraph(identifierToFlow, ancestorCursor, cursorPath);
    }

    /**
     * Routes flow out of a lambda's return value to a call's output node, per any matching
     * out-of-callback model, then continues the flow from that output node in the enclosing scope.
     */
    private static void tryOutOfCallbackFlow(FlowGraph currentFlow, Cursor callCursor, DataFlowSpec spec) {
        Object call = callCursor.getValue();
        List<Expression> arguments;
        Expression select = null;
        if (call instanceof J.MethodInvocation) {
            arguments = ((J.MethodInvocation) call).getArguments();
            select = ((J.MethodInvocation) call).getSelect();
        } else if (call instanceof J.NewClass) {
            arguments = ((J.NewClass) call).getArguments();
            if (arguments == null) {
                return;
            }
        } else {
            return;
        }
        List<CallbackFlowModel> models = spec.callbackFlowModels(currentFlow.getNode());
        if (models.isEmpty()) {
            return;
        }
        Cursor currentCursor = currentFlow.getNode().getCursor();
        for (int i = 0; i < arguments.size(); i++) {
            Expression unwrapped = arguments.get(i).unwrap();
            if (!(unwrapped instanceof J.Lambda)) {
                continue;
            }
            J.Lambda lambda = (J.Lambda) unwrapped;
            if (!isAncestor(lambda, currentCursor) || !LambdaReturns.isLambdaResult(currentCursor, lambda)) {
                continue;
            }
            // A single call/argument can match several OUT models. Map.computeIfAbsent, for instance,
            // maps the mapping function's return value both to the call result (`Argument[1].ReturnValue
            // -> ReturnValue`) and into the map's values (`Argument[1].ReturnValue -> Argument[-1].MapValue`,
            // which this content-insensitive engine resolves to the whole `map` qualifier). Selecting one
            // deterministically matters: the old code stopped at the first match, so the surviving flow
            // depended on model iteration order, which is not stable across runs.
            //
            // Prefer the call result (the precise forward flow); fall back to the other resolved targets
            // only when no ReturnValue model applies. Routing to both would also redundantly mark the
            // qualifier that the result expression already encloses.
            DataFlowNode returnValueTarget = null;
            List<DataFlowNode> otherTargets = new ArrayList<>();
            for (CallbackFlowModel model : models) {
                if (model.getDirection() != CallbackFlowModel.Direction.OUT || model.getCallbackArgument() != i) {
                    continue;
                }
                if (!callMatches(callCursor, model.getMatcher())) {
                    continue;
                }
                DataFlowNode target = resolveCallbackOutput(callCursor, select, arguments, model.getOther());
                if (target == null) {
                    continue;
                }
                if (model.getOther().getKind() == CallbackFlowModel.Position.Kind.RETURN_VALUE) {
                    returnValueTarget = target;
                } else {
                    otherTargets.add(target);
                }
            }
            // Continue the flow from the call's output node(s) in the enclosing scope (the source may
            // sit inside a lambda block body, which `findAllFlows` would not otherwise leave).
            List<DataFlowNode> targets = returnValueTarget != null ? singletonList(returnValueTarget) : otherTargets;
            if (!targets.isEmpty()) {
                for (DataFlowNode target : targets) {
                    findAllFlows(currentFlow.addEdge(target), spec);
                }
                return;
            }
        }
    }

    private static @Nullable DataFlowNode resolveCallbackOutput(
            Cursor callCursor,
            @Nullable Expression select,
            List<Expression> arguments,
            CallbackFlowModel.Position position
    ) {
        switch (position.getKind()) {
            case RETURN_VALUE:
                return DataFlowNode.of(callCursor).toNull();
            case QUALIFIER:
                if (select == null) {
                    return null;
                }
                return DataFlowNode.of(new Cursor(callCursor, select)).toNull();
            case ARGUMENT:
                int k = position.getArgument();
                if (k < 0 || k >= arguments.size()) {
                    return null;
                }
                return DataFlowNode.of(new Cursor(callCursor, arguments.get(k))).toNull();
            default:
                return null;
        }
    }

    private static boolean callMatches(Cursor callCursor, InvocationMatcher matcher) {
        return Call.viewOf(callCursor).map(c -> c.matches(matcher)).orSuccess(false);
    }

    /**
     * Routes flow into a lambda argument's parameter, per any matching into-callback model. Seeds a
     * sub-flow from the lambda parameter (reusing the lambda-parameter-source traversal) so flow
     * continues through the lambda body. Returns {@code true} when at least one such edge was added.
     */
    private static boolean tryIntoCallbackFlow(FlowGraph currentFlow, Cursor callCursor, Cursor inputCursor, DataFlowSpec spec) {
        if (!(callCursor.getValue() instanceof J.MethodInvocation)) {
            return false;
        }
        J.MethodInvocation mi = callCursor.getValue();
        List<CallbackFlowModel> models = spec.callbackFlowModels(currentFlow.getNode());
        if (models.isEmpty()) {
            return false;
        }
        Object input = inputCursor.getValue();
        boolean added = false;
        for (CallbackFlowModel model : models) {
            if (model.getDirection() != CallbackFlowModel.Direction.INTO ||
                !matchesInputPosition(model.getOther(), input, mi)) {
                continue;
            }
            int i = model.getCallbackArgument();
            if (i < 0 || i >= mi.getArguments().size()) {
                continue;
            }
            Expression argExpr = mi.getArguments().get(i).unwrap();
            if (!(argExpr instanceof J.Lambda) || !callMatches(callCursor, model.getMatcher())) {
                continue;
            }
            Cursor paramCursor = lambdaParameterCursor(callCursor, (J.Lambda) argExpr, model.getParameter());
            if (paramCursor == null) {
                continue;
            }
            DataFlowNode paramNode = DataFlowNode.of(paramCursor).toNull();
            if (paramNode == null) {
                continue;
            }
            FlowGraph paramFlow = currentFlow.addEdge(paramNode);
            findAllFlows(paramFlow, spec);
            added = true;
        }
        return added;
    }

    private static boolean matchesInputPosition(CallbackFlowModel.Position position, Object input, J.MethodInvocation mi) {
        switch (position.getKind()) {
            case QUALIFIER:
                return mi.getSelect() == input;
            case ARGUMENT:
                int k = position.getArgument();
                return k >= 0 && k < mi.getArguments().size() && mi.getArguments().get(k) == input;
            default:
                return false;
        }
    }

    private static @Nullable Cursor lambdaParameterCursor(Cursor callCursor, J.Lambda lambda, int parameterIndex) {
        J.Lambda.Parameters parameters = lambda.getParameters();
        List<J> parameterDeclarations = parameters.getParameters();
        if (parameterIndex < 0 || parameterIndex >= parameterDeclarations.size()) {
            return null;
        }
        J parameterDeclaration = parameterDeclarations.get(parameterIndex);
        if (!(parameterDeclaration instanceof J.VariableDeclarations)) {
            return null;
        }
        J.VariableDeclarations variableDeclarations = (J.VariableDeclarations) parameterDeclaration;
        if (variableDeclarations.getVariables().isEmpty()) {
            return null;
        }
        Cursor lambdaCursor = new Cursor(callCursor, lambda);
        Cursor parametersCursor = new Cursor(lambdaCursor, parameters);
        Cursor variableDeclarationsCursor = new Cursor(parametersCursor, variableDeclarations);
        return new Cursor(variableDeclarationsCursor, variableDeclarations.getVariables().get(0));
    }

    /** Holds if {@code maybeAncestor} appears on the cursor's path (i.e. encloses it). */
    private static boolean isAncestor(J maybeAncestor, Cursor cursor) {
        for (Iterator<Object> it = cursor.getPath(); it.hasNext(); ) {
            if (it.next() == maybeAncestor) {
                return true;
            }
        }
        return false;
    }
}
