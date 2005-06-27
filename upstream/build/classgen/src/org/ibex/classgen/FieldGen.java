package org.ibex.classgen;

import java.io.*;

/** Class representing a field in a generated classfile
    @see ClassFile#addField */
public class FieldGen implements CGConst {
    private final String name;
    private final Type type;
    private final int flags;
    private final ClassFile.AttrGen attrs;

    StringBuffer debugToString(StringBuffer sb) {
        sb.append(ClassFile.flagsToString(flags,false));
        sb.append(type.debugToString());
        sb.append(" ");
        sb.append(name);
        if(attrs.contains("ConstantValue"))
            sb.append(" = \"").append(attrs.get("ConstantValue")).append("\"");
        sb.append(";");
        return sb;
    }
    
    FieldGen(DataInput in, ConstantPool cp) throws IOException {
        flags = in.readShort();
        if((flags & ~(ACC_PUBLIC|ACC_PRIVATE|ACC_PROTECTED|ACC_VOLATILE|ACC_TRANSIENT|ACC_STATIC|ACC_FINAL)) != 0)
            throw new ClassFile.ClassReadExn("invalid flags");        
        name = cp.getUtf8KeyByIndex(in.readShort());
        type = Type.instance(cp.getUtf8KeyByIndex(in.readShort()));
        attrs = new ClassFile.AttrGen(in,cp);
    }

    FieldGen(ClassFile owner, String name, Type type, int flags) {
        if((flags & ~(ACC_PUBLIC|ACC_PRIVATE|ACC_PROTECTED|ACC_VOLATILE|ACC_TRANSIENT|ACC_STATIC|ACC_FINAL)) != 0)
            throw new IllegalArgumentException("invalid flags");
        this.name = name;
        this.type = type;
        this.flags = flags;
        this.attrs = new ClassFile.AttrGen();        
   }
    
    /** Sets the ContantValue attribute for this field. 
        @param val The value to set this field to. Must be an Integer, Long, Float, Double, or String */
    public void setConstantValue(Object val) {
        if((flags & ACC_STATIC) == 0) throw new IllegalStateException("field does not have the ACC_STATIC bit set");
        attrs.put("ConstantValue",val);
    }
    
    void finish(ConstantPool cp) {
        cp.addUtf8(name);
        cp.addUtf8(type.getDescriptor());
        
        attrs.finish(cp);
    }
    
    void dump(DataOutput o, ConstantPool cp) throws IOException {
        o.writeShort(flags);
        o.writeShort(cp.getUtf8Index(name));
        o.writeShort(cp.getUtf8Index(type.getDescriptor()));
        attrs.dump(o,cp);
    }
}
