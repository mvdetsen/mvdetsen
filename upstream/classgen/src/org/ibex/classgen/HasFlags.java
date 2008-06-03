package org.ibex.classgen;

public abstract class HasFlags implements CGConst {

    public HasFlags(int flags) { this.flags = flags; }

    public final int flags;
    public int getFlags() { return flags; }

    public boolean isPublic() { return (getFlags() & PUBLIC) != 0; }
    public boolean isPrivate() { return (getFlags() & PRIVATE) != 0; }
    public boolean isProtected() { return (getFlags() & PROTECTED) != 0; }
    public boolean isStatic() { return (getFlags() & STATIC) != 0; }
    public boolean isFinal() { return (getFlags() & FINAL) != 0; }
    public boolean isInterface() { return (getFlags() & INTERFACE) != 0; }

    public boolean isAbstract() { return (getFlags() & ABSTRACT) != 0; }
    public boolean isNative() { return (getFlags() & NATIVE) != 0; }

    public boolean isVolatile() { return (getFlags() & VOLATILE) != 0; }
    public boolean isTransient() { return (getFlags() & TRANSIENT) != 0; }
}
