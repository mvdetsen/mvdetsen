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
    Type.Class klass;
    String name;
    String descriptor;
        
    MemberRef(Type.Class klass, String name, String descriptor) { this.klass = klass; this.name = name; this.descriptor = descriptor; }
    MemberRef(MemberRef o) { this.klass = o.klass; this.name = o.name; this.descriptor = o.descriptor; }
    public boolean equals(Object o_) {
        if(!(o_ instanceof MemberRef)) return false;
        MemberRef o = (MemberRef) o_;
        return o.klass.equals(klass) && o.name.equals(name) && o.descriptor.equals(descriptor);
    }
    public int hashCode() { return klass.hashCode() ^ name.hashCode() ^ descriptor.hashCode(); }
}
    
