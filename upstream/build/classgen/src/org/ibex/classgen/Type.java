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
        if (d.startsWith("[")) return new Type.Array(instance(d.substring(1)));
        return new Type.Class(d);
    }

    public       String  toString() { return toString; }
    public       String  debugToString() { return toString; }
    public final String  getDescriptor() { return descriptor; }
    public       int     hashCode() { return descriptor.hashCode(); }
    public       boolean equals(java.lang.Object o) { return this==o; }

    public Type.Array  makeArray() { return (Type.Array)instance("["+descriptor); }

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

    public static class Array extends Type.Ref {
        protected Array(Type t) { super("[" + t.getDescriptor(), t.toString() + "[]"); }
        public Type.Array asArray() { return this; }
        public boolean isArray() { return true; }
        public int dimension() { return getDescriptor().lastIndexOf('['); }
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

        public Field  field(String name, Type type) { return new Field(name, type); }
        public Method method(String name, Type returnType, Type[] argTypes) { return new Method(name, returnType, argTypes); }
        public Method method(String signature) {
            // FEATURE: This parser is ugly but it works (and shouldn't be a problem) might want to clean it up though
            String s = signature;
            String name = s.startsWith("(") ? "" : s.substring(0, s.indexOf('('));
            s = s.substring(s.indexOf('('));
            int p = s.indexOf(')');
            if(p == -1) throw new IllegalArgumentException("invalid method type descriptor");
            String argsDesc = s.substring(1,p);
            String retDesc = s.substring(p+1);
            Type[] argsBuf = new Type[argsDesc.length()];
            int i;
            for(i=0,p=0;argsDesc.length() > 0;i++,p=0) {
                while(p < argsDesc.length() && argsDesc.charAt(p) == '[') p++;
                if(p == argsDesc.length())  throw new IllegalArgumentException("invalid method type descriptor");
                if(argsDesc.charAt(p) == 'L') {
                    p = argsDesc.indexOf(';');
                    if(p == -1) throw new IllegalArgumentException("invalid method type descriptor");
                }
                argsBuf[i] = Type.instance(argsDesc.substring(0,p+1));
                argsDesc = argsDesc.substring(p+1);
            }
            Type args[] = new Type[i];
            System.arraycopy(argsBuf,0,args,0,i);
            return method(name, Type.instance(retDesc), args);
        }

        public abstract class Member {
            public final String name;
            private Member(String name) { this.name = name; }
            public Type.Class getDeclaringClass() { return Type.Class.this; }
            public abstract String getDescriptor();
            public boolean equals(Object o_) {
                if(!(o_ instanceof Member)) return false;
                Member o = (Member) o_;
                return o.getDeclaringClass().equals(getDeclaringClass()) &&
                    o.name.equals(name) &&
                    o.getDescriptor().equals(getDescriptor());
            }
            public int hashCode() { return getDeclaringClass().hashCode() ^ name.hashCode() ^ getDescriptor().hashCode(); }
        }
    
        public class Field extends Member {
            public final Type type;
            private Field(String name, Type t) { super(name); this.type = t; }
            public String getDescriptor() { return name; }
        }

        public class Method extends Member {
            final Type[] argTypes;
            public final Type   returnType;
            public Type getArgType(int i) { return argTypes[i]; }
            public int  getNumArgs()      { return argTypes.length; }
            private Method(String name, Type returnType, Type[] argTypes) {
                super(name);
                this.argTypes = argTypes;
                this.returnType = returnType;
            }
            public String getDescriptor() {
                StringBuffer sb = new StringBuffer(argTypes.length*4);
                sb.append("(");
                for(int i=0;i<argTypes.length;i++) sb.append(argTypes[i].getDescriptor());
                sb.append(")");
                sb.append(returnType.getDescriptor());
                return sb.toString();
            }
        }
    }
    
    // FEATURE: This probably isn't the best place for these
    static String methodTypeDescriptor(Type[] argTypes, Type returnType) {
        StringBuffer sb = new StringBuffer(argTypes.length*4);
        sb.append("(");
        for(int i=0;i<argTypes.length;i++) sb.append(argTypes[i].getDescriptor());
        sb.append(")");
        sb.append(returnType.getDescriptor());
        return sb.toString();
    }

}
