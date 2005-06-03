package org.ibex.classgen;

/** This class represents Method references. It is used as an argument to the 
    INVOKESTATIC, INVOKEVIRTUAL, INVOKESPEICAL, and INVOKEINTERFACE bytecodes 
    @see CGConst#INVOKESTATIC
    @see CGConst#INVOKEVIRTUAL
    @see CGConst#INVOKESPECIAL
    @see CGConst#INVOKEINTERFACE
*/
public class MethodRef extends MemberRef {
    
    final Type[] argTypes;
    final Type   returnType;

    /** Create a reference to method <i>name</i> of class <i>c</i> with the return type <i>ret</i> and the
        arguments <i>args</i> */
    public MethodRef(Type.Class c, String name, Type returnType, Type[] argTypes) {
        super(c, name);
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
