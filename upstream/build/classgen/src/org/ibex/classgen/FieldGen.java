package org.ibex.classgen;

import java.io.*;

/** Class representing a field in a generated classfile
    @see ClassFile#addField */
public class FieldGen implements CGConst {
    private final ConstantPool cp;
    private final String name;
    private final Type type;
    private final int flags;
    private final ClassFile.AttrGen attrs;
    
    private Object constantValue;

    public String toString() { StringBuffer sb = new StringBuffer(); toString(sb); return sb.toString(); }
    public void   toString(StringBuffer sb) {
        sb.append(ClassFile.flagsToString(flags));
        sb.append(type);
        sb.append(" ");
        sb.append(name);
        sb.append(";");
        // FIXME: attrs
    }
    
    FieldGen(ConstantPool cp, DataInput in) throws IOException {
        this.cp = cp;
        flags = in.readShort();
        name = cp.getUtf8ByIndex(in.readShort());
        type = cp.getType(in.readShort());
        attrs = new ClassFile.AttrGen(cp, in);
    }

    FieldGen(ClassFile owner, String name, Type type, int flags) {
        if((flags & ~(ACC_PUBLIC|ACC_PRIVATE|ACC_PROTECTED|ACC_VOLATILE|ACC_TRANSIENT|ACC_STATIC|ACC_FINAL)) != 0)
            throw new IllegalArgumentException("invalid flags");
        this.cp = owner.cp;
        this.name = name;
        this.type = type;
        this.flags = flags;
        this.attrs = new ClassFile.AttrGen(cp);
        
        cp.addUtf8(name);
        cp.addUtf8(type.getDescriptor());
    }
    
    /** Sets the ContantValue attribute for this field. 
        @param val The value to set this field to. Must be an Integer, Long, Float, Double, or String */
    public void setConstantValue(Object val) {
        if((flags & ACC_STATIC) == 0) throw new IllegalStateException("field does not have the ACC_STATIC bit set");
        constantValue = val;
    }
    
    void finish() {
        if(constantValue != null && !attrs.contains("ConstantValue"))
            attrs.add("ConstantValue", cp.add(constantValue));
    }
    
    void dump(DataOutput o) throws IOException {
        o.writeShort(flags);
        o.writeShort(cp.getUtf8Index(name));
        o.writeShort(cp.getUtf8Index(type.getDescriptor()));
        o.writeShort(attrs.size());
        attrs.dump(o);
    }
}
