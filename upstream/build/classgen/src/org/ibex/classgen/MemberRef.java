package org.ibex.classgen;

import java.util.*;
import java.io.*;

/** A class representing a field or method reference. This is used as an argument to the INVOKE*, GET*, and PUT* bytecodes
    @see MethodRef
    @see FieldRef
    @see MethodRef.I
    @see FieldRef
*/
public abstract class MemberRef {
    public final Type.Class klass;
    public final String name;
        
    MemberRef(Type.Class klass, String name) {
        this.klass = klass;
        this.name = name;
    }
    public abstract String getDescriptor();
    public boolean equals(Object o_) {
        if(!(o_ instanceof MemberRef)) return false;
        MemberRef o = (MemberRef) o_;
        return o.klass.equals(klass) && o.name.equals(name) && o.getDescriptor().equals(getDescriptor());
    }
    public int hashCode() { return klass.hashCode() ^ name.hashCode() ^ getDescriptor().hashCode(); }
}
    
