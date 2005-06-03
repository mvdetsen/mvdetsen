package org.ibex.classgen;

import java.util.StringTokenizer;
import java.util.Hashtable;

public class Type {

    private static Hashtable instances = new Hashtable();  // this has to appear at the top of the file

    // Public API //////////////////////////////////////////////////////////////////////////////

    public static final Type VOID = new Type("V", "void");
    public static final Type INT = new Type("I", "int");
    public static final Type LONG = new Type("J", "long");
    public static final Type BOOLEAN = new Type("Z", "boolean");
    public static final Type DOUBLE = new Type("D", "double");
    public static final Type FLOAT = new Type("F", "float");
    public static final Type BYTE = new Type("B", "byte");
    public static final Type CHAR = new Type("C", "char");
    public static final Type SHORT = new Type("S", "short");
    
    public static final Type.Class OBJECT = new Type.Class("java.lang.Object");
    public static final Type.Class STRING = new Type.Class("java.lang.String");
    public static final Type.Class STRINGBUFFER = new Type.Class("java.lang.StringBuffer");
    public static final Type.Class INTEGER_OBJECT = new Type.Class("java.lang.Integer");
    public static final Type.Class DOUBLE_OBJECT = new Type.Class("java.lang.Double");
    public static final Type.Class FLOAT_OBJECT = new Type.Class("java.lang.Float");
    
    /** A zero element Type[] array (can be passed as the "args" param when a method takes no arguments */
    public static final Type[] NO_ARGS = new Type[0];
    
    /** guarantee: there will only be one instance of Type for a given descriptor ==> equals() and == are interchangeable */
    public static Type instance(String d) {
        Type ret = (Type)instances.get(d);
        if (ret != null) return ret;
        if (d.endsWith("[")) return new Type.Array(instance(d.substring(d.length()-1)));
        return new Type.Class(d);
    }

    public       String  toString() { return toString; }
    public final String  getDescriptor() { return descriptor; }
    public       int     hashCode() { return descriptor.hashCode(); }
    public       boolean equals(java.lang.Object o) { return this==o; }

    public Type.Array  makeArray() { return (Type.Array)instance(descriptor+"["); }

    public Type.Ref    asRef()       { throw new RuntimeException("attempted to use "+this+" as a Type.Ref, which it is not"); }
    public Type.Class  asClass()     { throw new RuntimeException("attempted to use "+this+" as a Type.Class, which it is not"); }
    public Type.Array  asArray()     { throw new RuntimeException("attempted to use "+this+" as a Type.Array, which it is not"); }
    public boolean     isPrimitive() { return !isRef(); }
    public boolean     isRef()       { return false; }
    public boolean     isClass()     { return false; }
    public boolean     isArray()     { return false; }

    // Protected/Private //////////////////////////////////////////////////////////////////////////////

    protected final String descriptor;
    protected final String toString;
    protected Type(String descriptor) { this(descriptor, descriptor); }
    protected Type(String descriptor, String humanReadable) {
        this.toString = humanReadable;
        instances.put(this.descriptor = descriptor, this);
    }

    public static class Ref extends Type {
        protected Ref(String descriptor) { super(descriptor); }
        protected Ref(String descriptor, String humanReadable) { super(descriptor, humanReadable); }
        public    Type.Ref asRef() { return this; }
        public    boolean  isRef() { return true; }
    }

    public static class Class extends Type.Ref {
        protected Class(String s) { super(_initHelper(s), _initHelper2(s)); }
        public Type.Class asClass() { return this; }
        public boolean isClass() { return true; }
        public String getShortName() { return toString.substring(toString.lastIndexOf('.')+1); }
        String internalForm() { return descriptor.substring(1, descriptor.length()-1); }
        private static String _initHelper(String s) {
            if (!s.startsWith("L") || !s.endsWith(";")) s = "L" + s.replace('.', '/') + ";";
            return s;
        }
        private static String _initHelper2(String s) {
            if (s.startsWith("L") && s.endsWith(";")) s = s.substring(1, s.length()-1);
            return s.replace('/', '.');
        }
        String[] components() {
            StringTokenizer st = new StringTokenizer(descriptor.substring(1, descriptor.length()-1), "/");
            String[] a = new String[st.countTokens()];
            for(int i=0;st.hasMoreTokens();i++) a[i] = st.nextToken();
            return a;
        }
    }    

    public static class Array extends Type.Ref {
        protected Array(Type t) { super(t.getDescriptor() + "[", t.toString() + "[]"); }
        public Type.Array asArray() { return this; }
        public boolean isArray() { return true; }
        public int dimension() { return descriptor.length() - descriptor.indexOf('['); }
    }

}
