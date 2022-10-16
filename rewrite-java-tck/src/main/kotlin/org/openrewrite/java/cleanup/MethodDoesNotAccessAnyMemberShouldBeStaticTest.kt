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
package org.openrewrite.java.cleanup

import org.junit.jupiter.api.Test
import org.openrewrite.java.JavaParser
import org.openrewrite.java.JavaRecipeTest

interface MethodDoesNotAccessAnyMemberShouldBeStaticTest : JavaRecipeTest {

    @Test
    fun testCompliantPrivateSerializableImplementationSuperClass(jp: JavaParser) = assertUnchanged(
            jp,
            dependsOn = arrayOf(
                    """
                package com.abc;
                public class A implements java.io.Serializable {
                }
            """,
                    """
                package com.abc;
                public class B extends A {
                }
            """,
            ),
            recipe = MethodDoesNotAccessAnyMemberShouldBeStatic(),
            before = """
            package com.abc;
            public class C extends B {
                public static String STATIC = "static";
                private void writeObject(java.io.ObjectOutputStream stream) throws IOException {
                    stream.writeObject(STATIC);
                }
                private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
                     STATIC = (String) in.readObject();
                }
                private void readObjectNoData() throws ObjectStreamException {
                    STATIC = "From Serializable";
                }
            }
            """
    )


    @Test
    fun testCompliantPrivateSerializableImplementation(jp: JavaParser) = assertUnchanged(
            jp,
            recipe = MethodDoesNotAccessAnyMemberShouldBeStatic(),
            before = """
            package com.abc;
            public class A implements java.io.Serializable {
                public static String STATIC = "static";
                private void writeObject(java.io.ObjectOutputStream stream) throws IOException {
                    stream.writeObject(STATIC);
                }
                private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
                     STATIC = (String)stream.readObject(STATIC);
                }
                private void readObjectNoData() throws ObjectStreamException {
                    STATIC = "From Serializable";
                }
            }
            """
    )


    @Test
    fun testCompliantFinalCallSuperClassNonStaticMethod(jp: JavaParser) = assertUnchanged(
            jp,
            dependsOn = arrayOf("""
                package com.abc;
                public class A {
                    private int i = 0;
                    protected int getValue(){
                        return i;
                    }
                }
            """
            ),
            recipe = MethodDoesNotAccessAnyMemberShouldBeStatic(),
            before = """
            package com.abc;
            public class A1 extends A {
                public static String STATIC = "static";
                public final String nonCompliantFinal() {
                    getValue();
                    return STATIC;
                }
            }
            """
    )

    @Test
    fun testCompliantFinalCallSameClassNonStaticMethod(jp: JavaParser) = assertUnchanged(
            jp,
            recipe = MethodDoesNotAccessAnyMemberShouldBeStatic(),
            before = """
            package com.abc;
            public class A1 {
                private int i = 0;
                public static String STATIC = "static";
                public int getValue(){
                    return i;
                }
                public final String nonCompliantFinal() {
                    getValue();
                    return STATIC;
                }
            }
            """
    )

    @Test
    fun testCompliantFinalAccessSuperClassVariable(jp: JavaParser) = assertUnchanged(
            jp,
            dependsOn = arrayOf("""
                package com.abc;
                public class A {
                  protected int i = 0;
                }
            """
            ),
            recipe = MethodDoesNotAccessAnyMemberShouldBeStatic(),
            before = """
            package com.abc;
            public class A1 extends A {
                public static String STATIC = "static";
                public final String nonCompliantFinal() {
                    i = i + 1;
                    return STATIC;
                }
            }
            """
    )

    @Test
    fun testCompliantFinalMethodAccessSameClassVariable(jp: JavaParser) = assertUnchanged(
            jp,
            recipe = MethodDoesNotAccessAnyMemberShouldBeStatic(),
            before = """
            package com.abc;
            public class A1 {
                private int i = 0;
                public static String STATIC = "static";
                public final String nonCompliantFinal() {
                    if(i == 0) {
                        i = i + 1;
                    }
                    return STATIC;
                }
            }
            """
    )


    @Test
    fun testNonCompliantPrivateMethodOnlyAccessStaticVariable(jp: JavaParser) = assertChanged(
            jp,
            recipe = MethodDoesNotAccessAnyMemberShouldBeStatic(),
            before = """
            package com.abc;
            public class A1 {
                private static String BASE_URL = "http://abc.com";
                private static String ENDPOINT = "/test";
                private void getUrl() {
                    return BASE_URL + ENDPOINT;
                }
            }
            """,
            after = """
            package com.abc;
            public class A1 {
                private static String BASE_URL = "http://abc.com";
                private static String ENDPOINT = "/test";
                private static void getUrl() {
                    return BASE_URL + ENDPOINT;
                }
            }
            """
    )

    @Test
    fun testNonCompliantFinalMethodOnlyAccessStaticVariable(jp: JavaParser) = assertChanged(
            jp,
            recipe = MethodDoesNotAccessAnyMemberShouldBeStatic(),
            before = """
            package com.abc;
            public class A1 {
                public static String STATIC = "static";
                public final String nonCompliantFinal() {
                    return STATIC;
                }
            }
            """,
            after = """
            package com.abc;
            public class A1 {
                public static String STATIC = "static";
                public static String nonCompliantFinal() {
                    return STATIC;
                }
            }
            """
    )

    @Test
    fun testNonCompliantFinalMethodOnlyCallStaticMethod(jp: JavaParser) = assertChanged(
            jp,
            recipe = MethodDoesNotAccessAnyMemberShouldBeStatic(),
            before = """
            package com.abc;
            public class A1 {
                public static String STATIC = "static";
                private static String getStatic(){
                    return STATIC;
                }
                public final String nonCompliantFinal() {
                    return getStatic();
                }
            }
            """,
            after = """
            package com.abc;
            public class A1 {
                public static String STATIC = "static";
                private static String getStatic(){
                    return STATIC;
                }
                public static String nonCompliantFinal() {
                    return getStatic();
                }
            }
            """
    )

    @Test
    fun testNonCompliantFinalMethodOnlyAccessStaticVariableUpdateCaller(jp: JavaParser) = assertChanged(
            jp,
            dependsOn = arrayOf("""
            package com.abc;
            public class A1 {
                public static String STATIC = "static";
                public final String nonCompliantFinal() {
                    return STATIC;
                }
            }
            """
            ),
            recipe = MethodDoesNotAccessAnyMemberShouldBeStatic(),
            before = """
                package com.xyz;
                import com.abc.A1;
                public class B1 {
                   public void method() {
                        new A1().nonCompliantFinal();
                   }
                }
            """,
            after = """
                package com.xyz;
                import com.abc.A1;
                public class B1 {
                   public void method() {
                        A1.nonCompliantFinal();
                   }
                }
            """
    )

    @Test
    fun testNonCompliantFinalMethodOnlyCallStaticMethodChainReaction(jp: JavaParser) = assertChanged(
            jp,
            recipe = MethodDoesNotAccessAnyMemberShouldBeStatic(),
            before = """
            package com.abc;
            public class A1 {
                public static String STATIC = "static";
                
                public final String nonCompliantFinal() {
                    return getStatic();
                }
                private static String getStatic(){
                    return STATIC;
                }
                public final String anotherNonCompliantFinal() {
                    return nonCompliantFinal();
                }
            }
            """,
            after = """
            package com.abc;
            public class A1 {
                public static String STATIC = "static";
                
                public static String nonCompliantFinal() {
                    return getStatic();
                }
                private static String getStatic(){
                    return STATIC;
                }
                public static String anotherNonCompliantFinal() {
                    return nonCompliantFinal();
                }
            }
            """,
            cycles = 2,
            expectedCyclesThatMakeChanges = 2
    )
}