package org.ibex.classgen;

/** This class represents Method references. It is used as an argument to the 
    INVOKESTATIC, INVOKEVIRTUAL, INVOKESPEICAL, and INVOKEINTERFACE bytecodes 
    @see CGConst#INVOKESTATIC
    @see CGConst#INVOKEVIRTUAL
    @see CGConst#INVOKESPECIAL
    @see CGConst#INVOKEINTERFACE
*/
public class MethodRef extends ClassGen.FieldOrMethodRef {
    /** Create a reference to method <i>name</i> of class <i>c</i> with the return type <i>ret</i> and the
        arguments <i>args</i> */
    public MethodRef(Type.Class c, String name, Type ret, Type[] args) {
        super(c, name, getDescriptor(ret, args));
    }
    /** Equivalent to MethodRef(new Type.Class(s), ...)
        @see #MethodRef(Type.Class, String, Type, Type[])
    */
    public MethodRef(String s, String name, Type ret, Type[] args) {
        this(Type.instance(s).asClass(), name, ret, args);
    }
    MethodRef(MethodRef i) { super(i); }
    
    static String getDescriptor(Type ret, Type[] args) {
        StringBuffer sb = new StringBuffer(args.length*4);
        sb.append("(");
        for(int i=0;i<args.length;i++) sb.append(args[i].getDescriptor());
        sb.append(")");
        sb.append(ret.getDescriptor());
        return sb.toString();
    }
    
    /** MethodRef class used for the INVOKEINTERFACE bytecode. Although this contains the same
        data as a normal MethodRef, these are treated differently in the byte code format. In general,
        users don't need to be concerned with this though because MethodRef's are automatically converted
        to MethodRef.I's when they are applied to an INVOKEINTERFACE bytecode */
    public static class I extends MethodRef {
        public I(Type.Class c, String name, Type ret, Type[] args) { super(c, name, ret, args); }
        public I(String s, String name, Type ret, Type[] args) { super(s, name, ret, args); }
        I(MethodRef m) { super(m); }
    }
}
