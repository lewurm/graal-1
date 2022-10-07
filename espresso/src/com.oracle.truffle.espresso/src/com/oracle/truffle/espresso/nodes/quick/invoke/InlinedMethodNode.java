/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.oracle.truffle.espresso.nodes.quick.invoke;

import com.oracle.truffle.espresso.bytecode.Bytecodes;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.nodes.BytecodeNode;
import com.oracle.truffle.espresso.substitutions.JavaSubstitution;
import com.oracle.truffle.espresso.substitutions.Substitutions;

public abstract class InlinedMethodNode extends InvokeQuickNode {
    // Data needed to revert to the generic case.
    protected final int opcode;
    protected final int statementIndex;

    protected InlinedMethodNode(Method inlinedMethod, int top, int opcode, int callerBCI, int statementIndex) {
        super(inlinedMethod, top, callerBCI);
        this.opcode = opcode;
        this.statementIndex = statementIndex;
    }

    public static InlinedMethodNode createFor(Method resolutionSeed, int top, int opcode, int curBCI, int statementIndex) {
        if (!isInlineCandidate(resolutionSeed, opcode)) {
            return null;
        }
        if (resolutionSeed.isInlinableGetter()) {
            return InlinedGetterNode.create(resolutionSeed, top, opcode, curBCI, statementIndex);
        }
        if (resolutionSeed.isInlinableSetter()) {
            return InlinedSetterNode.create(resolutionSeed, top, opcode, curBCI, statementIndex);
        }
        if (isUncoditionalInlineCandidate(resolutionSeed, opcode)) {
            // Try to inline trivial substitutions.
            JavaSubstitution.Factory factory = Substitutions.lookupSubstitution(resolutionSeed);
            if (factory != null && factory.isTrivial()) {
                return InlinedSubstitutionNode.create(resolutionSeed, top, opcode, curBCI, statementIndex, factory);
            }
        }
        return null;
    }

    public final void revertToGeneric(BytecodeNode parent) {
        parent.generifyInlinedMethodNode(top, opcode, getCallerBCI(), statementIndex, method.getMethod());
    }

    protected static boolean isInlineCandidate(Method resolutionSeed, int opcode) {
        if (opcode == Bytecodes.INVOKESTATIC || opcode == Bytecodes.INVOKESPECIAL) {
            return true;
        }
        if (opcode == Bytecodes.INVOKEVIRTUAL) {
            // InvokeVirtual can be bytecode-level inlined if method is final, or there are no
            // overrides yet.
            if ((resolutionSeed.isFinalFlagSet() || resolutionSeed.isPrivate() || resolutionSeed.getDeclaringKlass().isFinalFlagSet()) ||
                            resolutionSeed.getContext().getClassHierarchyOracle().isLeafMethod(resolutionSeed).isValid()) {
                return true;
            }
        }
        return false;
    }

    protected static boolean isUncoditionalInlineCandidate(Method resolutionSeed, int opcode) {
        if (opcode == Bytecodes.INVOKESTATIC || opcode == Bytecodes.INVOKESPECIAL) {
            return true;
        }
        if (opcode == Bytecodes.INVOKEVIRTUAL) {
            // InvokeVirtual can be unconditionally bytecode-level inlined if method can never be
            // overriden.
            if (resolutionSeed.isFinalFlagSet() || resolutionSeed.isPrivate() || resolutionSeed.getDeclaringKlass().isFinalFlagSet()) {
                return true;
            }
        }
        return false;
    }
}
