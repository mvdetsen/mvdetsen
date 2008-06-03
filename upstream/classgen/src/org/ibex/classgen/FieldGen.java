package org.ibex.classgen;

import java.io.*;

/** Class representing a field in a generated classfile
    @see ClassFile#addField */
public class FieldGen extends Type.Class.Field.Body {

    StringBuffer toString(StringBuffer sb) {
        sb.append(ClassFile.flagsToString(flags, false));
        sb.append(getField().getType().toString());
        sb.append(" ");
        sb.append(getField().getName());
        if (attrs.contains("ConstantValue")) sb.append(" = \"").append(attrs.get("ConstantValue")).append("\"");
        sb.append(";");
        return sb;
    }
    
    FieldGen(Type.Class c, DataInput in, ConstantPool cp) throws IOException {
        this(in.readShort(),
             c.field(cp.getUtf8KeyByIndex(in.readShort()),
                     Type.fromDescriptor(cp.getUtf8KeyByIndex(in.readShort()))),
             new ClassFile.AttrGen(in, cp));
    }

    private FieldGen(int flags, Type.Class.Field field, ClassFile.AttrGen attrs) { this(field, flags, attrs); }
    FieldGen(Type.Class.Field field, int flags) { this(field, flags, new ClassFile.AttrGen()); }
    FieldGen(Type.Class.Field field, int flags, ClassFile.AttrGen attrs) { field.super(flags, attrs); }
    
    /** Sets the ContantValue attribute for this field. 
        @param val The value to set this field to. Must be an Integer, Long, Float, Double, or String */
    public void setConstantValue(Object val) {
        if (!isStatic()) throw new IllegalStateException("field does not have the STATIC bit set");
        attrs.put("ConstantValue",val);
    }
    
    void finish(ConstantPool cp) {
        cp.addUtf8(getField().getName());
        cp.addUtf8(getField().getType().getDescriptor());
        
        attrs.finish(cp);
    }
    
    void dump(DataOutput o, ConstantPool cp) throws IOException {
        o.writeShort(getFlags());
        o.writeShort(cp.getUtf8Index(getField().getName()));
        o.writeShort(cp.getUtf8Index(getField().getType().getDescriptor()));
        attrs.dump(o,cp);
    }
}
