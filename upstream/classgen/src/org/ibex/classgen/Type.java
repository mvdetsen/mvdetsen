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
    public static final Type NULL = new Null();
    
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

    public static Type unify(Type t1, Type t2) {
        if(t1 == Type.NULL) return t2;
        if(t2 == Type.NULL) return t1;
        if((t1 == Type.INT && t2 == Type.BOOLEAN) || (t2 == Type.INT & t1 == Type.BOOLEAN)) return Type.BOOLEAN;
        if(t1 == t2) return t1;
        // FIXME: This needs to do a lot more (subclasses, etc)
        // it probably should be in Context.java
        return null;
    }
    
    // Protected/Private //////////////////////////////////////////////////////////////////////////////

    protected final String descriptor;
    
    protected Type(String descriptor) {
        this.descriptor = descriptor;
        instances.put(descriptor, this);
    }
    
    public static class Null extends Type {
        protected Null() { super(""); } // not really correct....
    }

    public static class Primitive extends Type {
        private String humanReadable;
        Primitive(String descriptor, String humanReadable) {
            super(descriptor);
            this.humanReadable = humanReadable;
        }
        public String toString() { return humanReadable; }
        public boolean     isPrimitive() { return true; }
    }
    
    public abstract static class Ref extends Type {
        protected Ref(String descriptor) { super(descriptor); }
        public abstract String toString();
        public    Type.Ref asRef() { return this; }
        public    boolean  isRef() { return true; }
    }

    public static class Array extends Type.Ref {
        public final Type base;
        protected Array(Type t) { super("[" + t.getDescriptor()); base = t; }
        public Type.Array asArray() { return this; }
        public boolean isArray() { return true; }
        public String toString() { return base.toString() + "[]"; }
        public Type getElementType() { return base; }
    }

    public static class Class extends Type.Ref {
        protected Class(String s) { super(_initHelper(s)); }
        public Type.Class asClass() { return this; }
        public boolean isClass() { return true; }
        public static Type.Class instance(String className) {
            return (Type.Class)Type.fromDescriptor("L"+className.replace('.', '/')+";"); }
        public boolean extendsOrImplements(Type.Class c, Context cx) {
            if (this==c) return true;
            if (this==OBJECT) return false;
            ClassFile cf = cx.resolve(getName());
            if (cf==null) {
                System.err.println("warning: could not resolve class " + getName());
                return false;
            }
            if (cf.superType == c) return true;
            for(int i=0; i<cf.interfaces.length; i++) if (cf.interfaces[i].extendsOrImplements(c,cx)) return true;
            if (cf.superType == null) return false;
            return cf.superType.extendsOrImplements(c, cx);
        }
        String internalForm() { return descriptor.substring(1, descriptor.length()-1); }
        public String toString() { return internalForm().replace('/','.'); }
        public String getName() { return internalForm().replace('/','.'); }
        public String getShortName() {
            int p = descriptor.lastIndexOf('/');
            return p == -1 ? descriptor.substring(1,descriptor.length()-1) : descriptor.substring(p+1,descriptor.length()-1);
        }
        private static String _initHelper(String s) {
            if (!s.startsWith("L") || !s.endsWith(";")) throw new Error("invalid: " + s);
            return s;
        }
        String[] components() {
            StringTokenizer st = new StringTokenizer(descriptor.substring(1, descriptor.length()-1), "/");
            String[] a = new String[st.countTokens()];
            for(int i=0;st.hasMoreTokens();i++) a[i] = st.nextToken();
            return a;
        }

        public Type.Class.Body getBody(Context cx) { return cx.resolve(this.getName()); }
        public abstract class Body extends HasAttributes {
            public abstract Type.Class.Method.Body[] methods();
            public abstract Type.Class.Field.Body[] fields();
            public Body(int flags, ClassFile.AttrGen attrs) {
                super(flags, attrs);
                if ((flags & ~(PUBLIC|FINAL|SUPER|INTERFACE|ABSTRACT)) != 0)
                    throw new IllegalArgumentException("invalid flags: " + Integer.toString(flags,16));
            }
        }

        public Field field(String name, Type type) { return new Field(name, type); }
        public Field field(String name, String descriptor) { return field(name,Type.fromDescriptor(descriptor)); }

        public Method method(String name, Type returnType, Type[] argTypes) { return new Method(name, returnType, argTypes); }

        /** see JVM Spec section 2.10.2 */
        public Method method(String name, String descriptor) {
            // FEATURE: This parser is ugly but it works (and shouldn't be a problem) might want to clean it up though
            String s = descriptor;
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
            public abstract String toString();
            public abstract int hashCode();
            public abstract boolean equals(Object o);
        }
    
        public class Field extends Member {
            public final Type type;
            private Field(String name, Type t) { super(name); this.type = t; }
            public String getTypeDescriptor() { return type.getDescriptor(); }
            public Type getType() { return type; }
            public String toString() { return getDeclaringClass().toString()+"."+name+"["+type.toString()+"]"; }
            public class Body extends HasAttributes {
                public Field getField() { return Field.this; }
                public Body(int flags, ClassFile.AttrGen attrs) {
                    super(flags, attrs);
                    if ((flags & ~VALID_FIELD_FLAGS) != 0) throw new IllegalArgumentException("invalid flags");
                }
            }
            public int hashCode() {
                return type.hashCode() ^ name.hashCode() ^ getDeclaringClass().hashCode();
            }
            public boolean equals(Object o_) {
                if(o_ == this) return true;
                if(!(o_ instanceof Field)) return false;
                Field o = (Field) o_;
                return o.getDeclaringClass() == getDeclaringClass() && o.type == type && o.name.equals(name);
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
            
            public String toString() {
                StringBuffer sb = new StringBuffer();
                if (name.equals("<clinit>")) sb.append("static ");
                else {
                    if (name.equals("<init>"))
                        sb.append(Class.this.getShortName());
                    else
                        sb.append(returnType.toString()).append(" ").append(name);
                    sb.append("(");
                    for(int i=0; i<argTypes.length; i++)
                        sb.append((i==0?"":", ")+argTypes[i].toString());
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
            public abstract class Body extends HasAttributes {
                public abstract java.util.Hashtable getThrownExceptions();
                public abstract void debugBodyToString(StringBuffer sb);
                public Method getMethod() { return Method.this; }
                public Body(int flags, ClassFile.AttrGen attrs) {
                    super(flags, attrs);
                    if ((flags & ~VALID_METHOD_FLAGS) != 0) throw new IllegalArgumentException("invalid flags");
                }
                public boolean isConcrete() { return !isAbstract() && !isNative() /*FIXME: !inAnInterface*/; }
                public void toString(StringBuffer sb, String constructorName) {
                    int flags = getFlags();
                    sb.append("  ").append(ClassFile.flagsToString(flags,false));
                    sb.append(Method.this.toString());
                    java.util.Hashtable thrownExceptions = getThrownExceptions();
                    if (thrownExceptions.size() > 0) {
                        sb.append("throws");
                        for(java.util.Enumeration e = thrownExceptions.keys();e.hasMoreElements();)
                            sb.append(" ").append(((Type.Class)e.nextElement()).toString()).append(",");
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
            public int hashCode() {
                int h = returnType.hashCode() ^ name.hashCode() ^ getDeclaringClass().hashCode();
                for(int i=0;i<argTypes.length;i++) h ^= argTypes[i].hashCode();
                return h;
            }
            public boolean equals(Object o_) {
                if(o_ == this) return true;
                if(!(o_ instanceof Method)) return false;
                Method o = (Method) o_;
                if(!(o.getDeclaringClass() == getDeclaringClass() && o.returnType == returnType && o.name.equals(name))) return false;
                if(o.argTypes.length != argTypes.length) return false;
                for(int i=0;i<argTypes.length;i++)
                    if(o.argTypes[i] != argTypes[i]) return false;
                return true;
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
