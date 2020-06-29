/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.impl;

import static com.oracle.truffle.espresso.classfile.Constants.ACC_FINALIZER;

import java.lang.reflect.Modifier;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.runtime.Attribute;

// Structural shareable klass (superklass in superinterfaces resolved and linked)
// contains shape, field locations.
// Klass shape, vtable and field locations can be computed at the structural level.
public final class LinkedKlass {

    public static final LinkedKlass[] EMPTY_ARRAY = new LinkedKlass[0];
    private final ParserKlass parserKlass;

    // Linked structural references.
    private final LinkedKlass superKlass;

    @CompilationFinal(dimensions = 1) //
    private final LinkedKlass[] interfaces;

    @CompilationFinal(dimensions = 1) //
    private final LinkedMethod[] methods;

    private final boolean hasFinalizer;

    @CompilationFinal(dimensions = 2) //
    private final int[][] leftoverHoles;

    // instance fields declared in this class (no hidden fields)
    @CompilationFinal(dimensions = 1) //
    private final LinkedField[] instanceFields;

    // static fields declared in this class (no hidden fields)
    @CompilationFinal(dimensions = 1) //
    private final LinkedField[] staticFields;

    // hidden fields declared in this class
    @CompilationFinal(dimensions = 1) //
    private final LinkedField[] hiddenFields;

    private final int primitiveFieldTotalByteCount;
    private final int primitiveStaticFieldTotalByteCount;

    private final int fieldTableLength;
    private final int objectFields;
    private final int staticObjectFields;

    public LinkedKlass(ParserKlass parserKlass, LinkedKlass superKlass, LinkedKlass[] interfaces) {

        this.parserKlass = parserKlass;
        this.superKlass = superKlass;
        this.interfaces = interfaces;

        // Streams are forbidden in Espresso.
        // assert Arrays.stream(interfaces).allMatch(i -> Modifier.isInterface(i.getFlags()));
        assert superKlass == null || !Modifier.isInterface(superKlass.getFlags());

        // Super interfaces are not checked for finalizers; a default .finalize method will be
        // resolved to Object.finalize, making the finalizer not observable.
        this.hasFinalizer = ((parserKlass.getFlags() & ACC_FINALIZER) != 0) || (superKlass != null && (superKlass.getFlags() & ACC_FINALIZER) != 0);
        assert !this.hasFinalizer || !Type.java_lang_Object.equals(parserKlass.getType()) : "java.lang.Object cannot be marked as finalizable";

        final int methodCount = parserKlass.getMethods().length;
        LinkedMethod[] linkedMethods = new LinkedMethod[methodCount];

        for (int i = 0; i < methodCount; ++i) {
            ParserMethod parserMethod = parserKlass.getMethods()[i];
            // TODO(peterssen): Methods with custom constant pool should spawned here, but not
            // supported.
            linkedMethods[i] = new LinkedMethod(parserMethod);
        }

        this.methods = linkedMethods;

        LinkedFieldTable.CreationResult fieldCR = LinkedFieldTable.create(this);

        this.instanceFields = fieldCR.instanceFields;
        this.staticFields = fieldCR.staticFields;
        this.hiddenFields = fieldCR.hiddenFields;

        this.primitiveFieldTotalByteCount = fieldCR.primitiveFieldTotalByteCount;
        this.primitiveStaticFieldTotalByteCount = fieldCR.primitiveStaticFieldTotalByteCount;
        this.fieldTableLength = fieldCR.fieldTableLength;
        this.objectFields = fieldCR.objectFields;
        this.staticObjectFields = fieldCR.staticObjectFields;

        this.leftoverHoles = fieldCR.leftoverHoles;
    }

    int getFlags() {
        int flags = parserKlass.getFlags();
        if (hasFinalizer) {
            flags |= ACC_FINALIZER;
        }
        return flags;
    }

    ConstantPool getConstantPool() {
        return parserKlass.getConstantPool();
    }

    public Attribute getAttribute(Symbol<Name> name) {
        return parserKlass.getAttribute(name);
    }

    Symbol<Type> getType() {
        return parserKlass.getType();
    }

    public Symbol<Name> getName() {
        return parserKlass.getName();
    }

    public ParserKlass getParserKlass() {
        return parserKlass;
    }

    public LinkedKlass getSuperKlass() {
        return superKlass;
    }

    public LinkedKlass[] getInterfaces() {
        return interfaces;
    }

    public int getMajorVersion() {
        return getConstantPool().getMajorVersion();
    }

    public int getMinorVersion() {
        return getConstantPool().getMinorVersion();
    }

    protected LinkedMethod[] getLinkedMethods() {
        return methods;
    }

    public LinkedField[] getInstanceFields() {
        return instanceFields;
    }

    protected LinkedField[] getStaticFields() {
        return staticFields;
    }

    protected LinkedField[] getHiddenFields() {
        return hiddenFields;
    }

    public int getFieldTableLength() {
        return fieldTableLength;
    }

    public int getObjectFieldsCount() {
        return objectFields;
    }

    public int getPrimitiveFieldTotalByteCount() {
        return primitiveFieldTotalByteCount;
    }

    public int getStaticObjectFieldsCount() {
        return staticObjectFields;
    }

    public int getPrimitiveStaticFieldTotalByteCount() {
        return primitiveStaticFieldTotalByteCount;
    }

    public int[][] getLeftoverHoles() {
        return leftoverHoles;
    }
}
