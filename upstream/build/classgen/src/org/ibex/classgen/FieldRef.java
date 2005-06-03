package org.ibex.classgen;

/** This class represents Field references. It is used as an argument to the 
GETFIELD, PUTFIELD, GETSTATIC, and PUTSTATCI bytecodes 
@see CGConst#GETFIELD
@see CGConst#PUTFIELD
@see CGConst#GETSTATIC
@see CGConst#PUTSTATIC
*/
public class FieldRef extends ClassGen.FieldOrMethodRef {
    /** Create a reference to field <i>name</i> of class <i>c</i> with the type <i>t</i>  */    
    public FieldRef(Type.Object c, String name, Type t) { super(c,name,t.getDescriptor()); }
    /** Equivalent to FieldRef(new Type.Object(s),...)
        @see #FieldRef(Type.Object,String,Type,)
    */
    public FieldRef(String s, String name, Type t) { this(Type.fromDescriptor(s).asObject(), name, t); }
}
