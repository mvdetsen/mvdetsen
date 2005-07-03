package org.ibex.classgen;
import java.io.*;

public abstract class HasAttributes extends HasFlags {
    public ClassFile.AttrGen attrs;
    public HasAttributes(int flags, ClassFile.AttrGen attrs) { super(flags); this.attrs = attrs; }
    public void readAttributes(DataInput i, ConstantPool cp) throws IOException { attrs = new ClassFile.AttrGen(i, cp); }
}
