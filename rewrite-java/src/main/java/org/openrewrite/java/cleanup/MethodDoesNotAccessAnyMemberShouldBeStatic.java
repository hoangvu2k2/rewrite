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
package org.openrewrite.java.cleanup;

import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.ChangeMethodTargetToStatic;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.Flag;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.marker.Markers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class MethodDoesNotAccessAnyMemberShouldBeStatic extends Recipe {

    @Override
    public String getDisplayName() {
        return "\"private\" and \"final\" methods that don't access instance data should be \"static\"";
    }

    @Override
    public String getDescription() {
        return "Non-overridable methods (private or final) that don't access instance data can be static to prevent any misunderstanding about the contract of the method.";
    }

    @Override
    public Set<String> getTags() {
        return Collections.singleton("RSPEC-4551");
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(5);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext executionContext) {
                J.MethodDeclaration m = super.visitMethodDeclaration(method, executionContext);
                if (!isSerializableImplementationMethod(method) &&
                        !method.getMethodType().hasFlags(Flag.Static) &&
                        (method.getMethodType().hasFlags(Flag.Private) || method.getMethodType().hasFlags(Flag.Final))) {
                    AtomicBoolean nonStaticMethodInvocationOrNonStaticReference = new AtomicBoolean(false);
                    new NonStaticMethodInvocationOrNonStaticReferenceVisitor().visit(method, nonStaticMethodInvocationOrNonStaticReference);

                    if (!nonStaticMethodInvocationOrNonStaticReference.get()) {
                        List<J.Modifier> modifiers = new ArrayList<>(m.getModifiers());
                        Space singleSpace = Space.build(" ", Collections.emptyList());
                        modifiers.add(new J.Modifier(Tree.randomId(), singleSpace, Markers.EMPTY,
                                J.Modifier.Type.Static, Collections.emptyList()));
                        modifiers = ModifierOrder.sortModifiers(modifiers.stream().filter(it -> !it.getType().equals(J.Modifier.Type.Final)).collect(Collectors.toList()));

                        JavaType.Method transformedType = m.getMethodType();
                        Set<Flag> flags = new LinkedHashSet<>(method.getMethodType().getFlags());
                        flags.add(Flag.Static);
                        flags = flags.stream().filter( f -> !f.equals(Flag.Final)).collect(Collectors.toSet());
                        transformedType = transformedType.withFlags(flags);

                        m = m.withMethodType(transformedType).withModifiers(modifiers);
                        doNext(new ChangeMethodTargetToStatic(MethodMatcher.methodPattern(method),
                                method.getMethodType().getDeclaringType().getFullyQualifiedName(),
                                method.getReturnTypeExpression().getType().toString(), false));
                    }
                }
                return m;
            }

            private boolean isSerializableImplementationMethod(J.MethodDeclaration method) {
                String type = method.getMethodType().getDeclaringType().getFullyQualifiedName();
                MethodMatcher writeObject = new MethodMatcher(String.format("%s writeObject(java.io.ObjectOutputStream)", type), false);
                MethodMatcher readObject = new MethodMatcher(String.format("%s readObject(java.io.ObjectInputStream)", type), false);
                MethodMatcher readObjectNoData = new MethodMatcher(String.format("%s readObjectNoData()", type), false);
                return method.getMethodType().getDeclaringType().isAssignableTo("java.io.Serializable") &&
                        (writeObject.matches(method.getMethodType()) ||
                                readObject.matches(method.getMethodType()) ||
                                readObjectNoData.matches(method.getMethodType()));
            }
        };
    }

    private static class NonStaticMethodInvocationOrNonStaticReferenceVisitor extends JavaIsoVisitor<AtomicBoolean> {
        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, AtomicBoolean atomicBoolean) {
            Cursor parent = getCursor().dropParentUntil(is -> is instanceof J.MethodDeclaration);
            J.MethodDeclaration currentMethod = parent.getValue();
            if (!method.getMethodType().hasFlags(Flag.Static) &&
                    currentMethod.getMethodType().getDeclaringType()
                            .isAssignableTo(method.getMethodType().getDeclaringType().getFullyQualifiedName())
            ) {
                atomicBoolean.set(true);
            }
            return method;
        }

        @Override
        public J.Identifier visitIdentifier(J.Identifier identifier, AtomicBoolean atomicBoolean) {
            Cursor parent = getCursor().dropParentUntil(is -> is instanceof J.MethodDeclaration);
            J.MethodDeclaration currentMethod = parent.getValue();
            if (identifier.getFieldType() != null && !identifier.getFieldType().hasFlags(Flag.Static) &&
                    currentMethod.getMethodType().getDeclaringType().
                            isAssignableTo(identifier.getFieldType().getOwner().toString())
            ) {
                atomicBoolean.set(true);
            }
            return identifier;
        }
    }
}
