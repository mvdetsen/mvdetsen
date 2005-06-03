package org.ibex.classgen;

/** This class represents Field references. It is used as an argument to the 
GETFIELD, PUTFIELD, GETSTATIC, and PUTSTATCI bytecodes 
@see CGConst#GETFIELD
@see CGConst#PUTFIELD
@see CGConst#GETSTATIC
@see CGConst#PUTSTATIC
*/
public class FieldRef extends MemberRef {
    /** Create a reference to field <i>name</i> of class <i>c</i> with the type <i>t</i>  */    
    public final Type type;
    public FieldRef(Type.Class c, String name, Type t) { super(c, name); this.type = t; }
    public String getDescriptor() { return name; }
}

