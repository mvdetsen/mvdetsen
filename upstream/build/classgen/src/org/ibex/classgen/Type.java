package org.ibex.classgen;

import java.util.StringTokenizer;
import java.util.Hashtable;

public abstract class Type implements CGConst {

    private static Hashtable instances = new Hashtable();  // this has to appear at the top of the file

    // Public API //////////////////////////////////////////////////////////////////////////////

    public static final Type VOID = new Primitive("V", "void");
    public static final Type INT = new Primitive("I", "int");
    public static final Type LONG = new Primitive("J", "long");
    public static final Type BOOLEAN = new Primitive("Z", "boolean");
    public static final Type DOUBLE = new Primitive("D", "double");
    public static final Type FLOAT = new Primitive("F", "float");
    public static final Type BYTE = new Primitive("B", "byte");
    public static final Type CHAR = new Primitive("C", "char");
    public static final Type SHORT = new Primitive("S", "short");
    
    public static final Type.Class OBJECT = Type.Class.instance("java.lang.Object");
    public static final Type.Class STRING = Type.Class.instance("java.lang.String");
    public static final Type.Class STRINGBUFFER = Type.Class.instance("java.lang.StringBuffer");
    public static final Type.Class INTEGER_OBJECT = Type.Class.instance("java.lang.Integer");
    public static final Type.Class DOUBLE_OBJECT = Type.Class.instance("java.lang.Double");
    public static final Type.Class FLOAT_OBJECT = Type.Class.instance("java.lang.Float");
    
    /** A zero element Type[] array (can be passed as the "args" param when a method takes no arguments */
    public static final Type[] NO_ARGS = new Type[0];
    
    /** 
     *  A "descriptor" is the classfile-mangled text representation of a type (see JLS section 4.3)
     *  guarantee: there will only be one instance of Type for a given descriptor ==> equals() and == are interchangeable
     */
    public static Type fromDescriptor(String d) {
        Type ret = (Type)instances.get(d);
        if (ret != null) return ret;
        if (d.startsWith("[")) return new Type.Array(Type.fromDescriptor(d.substring(1)));
        return new Type.Class(d);
    }

    public final String  toString() { return super.toString(); }
    public abstract String debugToString();
    
    public final String  getDescriptor() { return descriptor; }

    public Type.Array  makeArray() { return (Type.Array)Type.fromDescriptor("["+descriptor); }
    public Type.Array  makeArray(int i) { return i==0 ? (Type.Array)this : makeArray().makeArray(i-1); }

    public Type.Ref    asRef()       { throw new RuntimeException("attempted to use "+this+" as a Type.Ref, which it is not"); }
    public Type.Class  asClass()     { throw new RuntimeException("attempted to use "+this+" as a Type.Class, which it is not"); }
    public Type.Array  asArray()     { throw new RuntimeException("attempted to use "+this+" as a Type.Array, which it is not"); }
    public boolean     isPrimitive() { return false; }
    public boolean     isRef()       { return false; }
    public boolean     isClass()     { return false; }
    public boolean     isArray()     { return false; }

    // Protected/Private //////////////////////////////////////////////////////////////////////////////

    protected final String descriptor;
    
    protected Type(String descriptor) {
        this.descriptor = descriptor;
        instances.put(descriptor, this);
    }

    public static class Primitive extends Type {
        private String humanReadable;
        Primitive(String descriptor, String humanReadable) {
            super(descriptor);
            this.humanReadable = humanReadable;
        }
        public String debugToString() { return humanReadable; }
        public boolean     isPrimitive() { return true; }
    }
    
    public abstract static class Ref extends Type {
        protected Ref(String descriptor) { super(descriptor); }
        public abstract String debugToString();
        public    Type.Ref asRef() { return this; }
        public    boolean  isRef() { return true; }
    }

    public static class Array extends Type.Ref {
        public final Type base;
        protected Array(Type t) { super("[" + t.getDescriptor()); base = t; }
        public Type.Array asArray() { return this; }
        public boolean isArray() { return true; }
        public String debugToString() { return base.debugToString() + "[]"; }
        public Type getElementType() { return Type.fromDescriptor(getDescriptor().substring(0, getDescriptor().length()-1)); }
    }

    public static class Class extends Type.Ref {
        protected Class(String s) { super(_initHelper(s)); }
        public Type.Class asClass() { return this; }
        public boolean isClass() { return true; }
        public static Type.Class instance(String className) {
            return (Type.Class)Type.fromDescriptor("L"+className.replace('.', '/')+";"); }
        //public boolean extendsOrImplements(Type.Class c, Context cx) { }
        String internalForm() { return descriptor.substring(1, descriptor.length()-1); }
        public String debugToString() { return internalForm().replace('/','.'); }
        public String getShortName() {
            int p = descriptor.lastIndexOf('/');
            return p == -1 ? descriptor.substring(1,descriptor.length()-1) : descriptor.substring(p+1,descriptor.length()-1);
        }
        private static String _initHelper(String s) {
            if (!s.startsWith("L") || !s.endsWith(";")) s = "L" + s.replace('.', '/') + ";";
            return s;
        }
        String[] components() {
            StringTokenizer st = new StringTokenizer(descriptor.substring(1, descriptor.length()-1), "/");
            String[] a = new String[st.countTokens()];
            for(int i=0;st.hasMoreTokens();i++) a[i] = st.nextToken();
            return a;
        }

        public Field field(String name, Type type) { return new Field(name, type); }
        public abstract class Body extends HasFlags {
        }

        public Method method(String name, Type returnType, Type[] argTypes) { return new Method(name, returnType, argTypes); }
        public Method method(String leftCrap, String rightCrap) { return method(leftCrap+rightCrap); }

        /** see JVM Spec section 2.10.2 */
        public Method method(String signature) {
            // FEATURE: This parser is ugly but it works (and shouldn't be a problem) might want to clean it up though
            String name = signature.substring(0, signature.indexOf('('));
            String s = signature.substring(signature.indexOf('('));
            if(!s.startsWith("(")) throw new IllegalArgumentException("invalid method type descriptor");
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
                argsBuf[i] = Type.fromDescriptor(argsDesc.substring(0,p+1));
                argsDesc = argsDesc.substring(p+1);
            }
            Type args[] = new Type[i];
            System.arraycopy(argsBuf,0,args,0,i);
            return method(name, Type.fromDescriptor(retDesc), args);
        }

        public abstract class Member {
            public final String name;
            private Member(String name) { this.name = name; }
            public Type.Class getDeclaringClass() { return Type.Class.this; }
            public String getName() { return name; }
            public abstract String getTypeDescriptor();
            public abstract String debugToString();
        }
    
        public class Field extends Member {
            public final Type type;
            private Field(String name, Type t) { super(name); this.type = t; }
            public String getTypeDescriptor() { return type.getDescriptor(); }
            public Type getType() { return type; }
            public String debugToString() { return getDeclaringClass().debugToString()+"."+name+"["+type.debugToString()+"]"; }
            public class Body extends HasFlags {
                public final int flags;
                public Body(int flags) {
                    if ((flags & ~VALID_FIELD_FLAGS) != 0) throw new IllegalArgumentException("invalid flags");
                    this.flags = flags;
                }
                public int getFlags() { return flags; }
                public Field getField() { return Field.this; }
            }
        }

        public class Method extends Member {
            final Type[] argTypes;
            public final Type   returnType;
            public Type getReturnType()   { return returnType; }
            public int  getNumArgs()      { return argTypes.length; }
            public Type getArgType(int i) { return argTypes[i]; }
            public Type[] getArgTypes()   {
                Type[] ret = new Type[argTypes.length];
                System.arraycopy(argTypes, 0, ret, 0, ret.length);
                return ret;
            }
            public boolean isConstructor() { return getName().equals("<init>"); }
            public boolean isClassInitializer() { return getName().equals("<clinit>"); }
            public String debugToString() {
                StringBuffer sb = new StringBuffer();
                if (name.equals("<clinit>")) sb.append("static ");
                else {
                    if (name.equals("<init>"))
                        sb.append(Class.this.getShortName());
                    else
                        sb.append(returnType.debugToString()).append(".").append(name);
                    sb.append("(");
                    for(int i=0; i<argTypes.length; i++)
                        sb.append((i==0?"":", ")+argTypes[i].debugToString());
                    sb.append(") ");
                }
                return sb.toString();
            }
            private Method(String name, Type returnType, Type[] argTypes) {
                super(name);
                this.argTypes = argTypes;
                this.returnType = returnType;
            }
            //public Method.Body getBody(Context cx) { }
            public String getTypeDescriptor() {
                StringBuffer sb = new StringBuffer(argTypes.length*4);
                sb.append("(");
                for(int i=0;i<argTypes.length;i++) sb.append(argTypes[i].getDescriptor());
                sb.append(")");
                sb.append(returnType.getDescriptor());
                return sb.toString();
            }
            public abstract class Body extends HasFlags {
                public abstract java.util.Hashtable getThrownExceptions();
                public abstract void debugBodyToString(StringBuffer sb);
                public void debugToString(StringBuffer sb, String constructorName) {
                    int flags = getFlags();
                    sb.append("  ").append(ClassFile.flagsToString(flags,false));
                    sb.append(Method.this.debugToString());
                    java.util.Hashtable thrownExceptions = getThrownExceptions();
                    if (thrownExceptions.size() > 0) {
                        sb.append("throws");
                        for(java.util.Enumeration e = thrownExceptions.keys();e.hasMoreElements();)
                            sb.append(" ").append(((Type.Class)e.nextElement()).debugToString()).append(",");
                        sb.setLength(sb.length()-1);
                        sb.append(" ");
                    }
                    if ((flags & (NATIVE|ABSTRACT))==0) {
                        sb.append("{\n");
                        debugBodyToString(sb);
                        sb.append("  }\n");
                    } else {
                        sb.append(";");
                    }
                }
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
