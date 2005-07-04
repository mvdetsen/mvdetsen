package org.ibex.classgen;

import java.util.*;
import java.io.*;

import org.ibex.classgen.util.*;

class ConstantPool implements CGConst {
    private final Hashtable entries = new Hashtable();
    private Ent[] entriesByIndex; // only valid when stable
    
    private int usedSlots = 1; // 0 is reserved
    private int state = OPEN;
    private static final int OPEN = 0;
    private static final int STABLE = 1; // existing entries won't change
    private static final int SEALED = 2; // no new entries
    
    ConstantPool() { }
    
    public abstract class Ent {
        int n; // this is the refcount if state == OPEN, index if >= STABLE
        int tag;
        Object key;
        Ent(int tag) { this.tag = tag; }
        void dump(DataOutput o) throws IOException { o.writeByte(tag); }
        abstract Object _key();
        final Object key() { return key == null ? (key = _key()) : key; }
        int slots() { return 1; } // number of slots this ent takes up
        void ref() {
            if(state != OPEN) throw new IllegalStateException("cp is not open");
            n++;
        }
        void unref() {
            if(state != OPEN) throw new IllegalStateException("cp is not open");
            if(--n == 0) entries.remove(key());
        }
    }
    
    class Utf8Ent extends Ent {
        String s;
        Utf8Ent() { super(CONSTANT_UTF8); }
        Utf8Ent(String s) { this();  this.s = s; }
        public String toString() { return s; }
        void dump(DataOutput o) throws IOException { super.dump(o); o.writeUTF(s); }
        Object _key() { return new Utf8Key(s); }
    }
    
    class IntLitEnt extends Ent {
        final int i;
        IntLitEnt(int i) { super(CONSTANT_INTEGER); this.i = i; }
        void dump(DataOutput o) throws IOException { super.dump(o); o.writeInt(i);  }
        Object _key() { return new Integer(i); }
    }
    class FloatLitEnt extends Ent {
        final float f;
        FloatLitEnt(float f) { super(CONSTANT_FLOAT); this.f = f; }
        void dump(DataOutput o) throws IOException { super.dump(o); o.writeFloat(f);  }
        Object _key() { return new Float(f); }
    }
    class LongLitEnt extends Ent {
        final long l;
        LongLitEnt(long l) { super(CONSTANT_LONG); this.l = l; }
        void dump(DataOutput o) throws IOException { super.dump(o); o.writeLong(l); }
        Object _key() { return new Long(l); }
        int slots() { return 2; }
    }
    class DoubleLitEnt extends Ent {
        final double d;
        DoubleLitEnt(double d) { super(CONSTANT_DOUBLE); this.d = d; }
        void dump(DataOutput o) throws IOException { super.dump(o); o.writeDouble(d); }
        Object _key() { return new Double(d); }
        int slots() { return 2; }
    }
    class StringLitEnt extends Ent {
        Utf8Ent utf8;
        StringLitEnt() { super(CONSTANT_STRING); }
        StringLitEnt(String s) { this(); this.utf8 = (Utf8Ent)addUtf8(s); }
        void dump(DataOutput o) throws IOException { super.dump(o); o.writeShort(utf8.n); }
        Object _key() { return utf8.s; }
        void unref() { utf8.unref(); super.unref(); }
    }
    class ClassEnt extends Ent {
        Utf8Ent utf8;
        ClassEnt() { super(CONSTANT_CLASS); }
        ClassEnt(String s) { this(); this.utf8 = (Utf8Ent) addUtf8(s); }
        void dump(DataOutput o) throws IOException { super.dump(o); o.writeShort(utf8.n); }
        Type.Class getTypeClass() { return  (Type.Class) key(); }
        Object _key() {
            return Type.fromDescriptor(utf8.s.startsWith("[") ? utf8.s : "L" + utf8.s + ";"); 
        }
        void unref() { utf8.unref(); super.unref(); }
        public String toString() { return "[Class: " + utf8.s + "]"; }
    }
    
    class NameAndTypeEnt extends Ent {
        Utf8Ent name;
        Utf8Ent type;
        
        NameAndTypeEnt() { super(CONSTANT_NAMEANDTYPE); }
        NameAndTypeEnt(String name, String type) {
            this();
            this.name = (Utf8Ent) addUtf8(name);
            this.type = (Utf8Ent) addUtf8(type);
        }
        void dump(DataOutput o) throws IOException { super.dump(o); o.writeShort(name.n); o.writeShort(type.n); }
        Object _key() { return new NameAndTypeKey(name.s, type.s); }
        void unref() { name.unref(); type.unref(); super.unref(); }
    }
    
    class MemberEnt extends Ent {
        ClassEnt klass;
        NameAndTypeEnt member;
        
        MemberEnt(int tag) { super(tag); }
        MemberEnt(int tag, Type.Class klass, String name, String type) {
            this(tag);
            this.klass = (ClassEnt) add(klass); 
            this.member = addNameAndType(name,type);
        }
        
        void dump(DataOutput o) throws IOException { super.dump(o); o.writeShort(klass.n); o.writeShort(member.n); }
        
        Object _key() {
            if(member.name == null) throw new Error("should never happen");
            switch(tag) {
                case CONSTANT_FIELDREF:
                    return klass.getTypeClass().field(member.name.s, member.type.s);
                case CONSTANT_METHODREF:
                case CONSTANT_INTERFACEMETHODREF:
                    Type.Class.Method m = klass.getTypeClass().method(member.name.s,member.type.s);
                    return tag == CONSTANT_INTERFACEMETHODREF ? (Object) new InterfaceMethodKey(m) : (Object) m;
                default:
                    throw new Error("should never happen");
            }
        }
        void unref() { klass.unref(); member.unref(); super.unref(); }
    }
    
    /*
     * Cache Keys
     */
    static class Utf8Key {
        String s;
        public Utf8Key(String s) { this.s = s; }
        public boolean equals(Object o) { return o instanceof Utf8Key && ((Utf8Key)o).s.equals(s); }
        public int hashCode() { return ~s.hashCode(); }
    }
        
    static class NameAndTypeKey {
        String name;
        String type;
        NameAndTypeKey(String name, String type) { this.name = name; this.type = type; }
        public boolean equals(Object o_) {
            if (!(o_ instanceof NameAndTypeKey)) return false;
            NameAndTypeKey o = (NameAndTypeKey) o_;
            return o.name.equals(name) && o.type.equals(type);
        }
        public int hashCode() { return name.hashCode() ^ type.hashCode(); }
    }
    
    static class InterfaceMethodKey {
        Type.Class.Method method;
        InterfaceMethodKey(Type.Class.Method method) { this.method = method; }
        public int hashCode() { return ~method.hashCode(); }
        public boolean equals(Object o) { return o instanceof InterfaceMethodKey && ((InterfaceMethodKey)o).method.equals(method); }
    }
    
    /*
     * Methods
     */
    
    Ent get(Object o) { return (Ent) entries.get(o); }
    Utf8Ent getUtf8(String s) { return (Utf8Ent) get(new Utf8Key(s)); }
    
    int getIndex(Object o) {
        Ent e = get(o);
        if (e == null) throw new IllegalStateException("entry not found");
        return getIndex(e);
    }
    int getUtf8Index(String s) {
        Ent e = getUtf8(s);
        if (e == null) throw new IllegalStateException("entry not found");
        return getIndex(e);
    }
    int getIndex(Ent ent) {
        if (state < STABLE) throw new IllegalStateException("constant pool is not stable");
        return ent.n;
    }
    
    Ent getByIndex(int index) {
        if (state < STABLE) throw new IllegalStateException("constant pool is not stable");
        Ent e;
        if (index >= 65536 || index >= entriesByIndex.length || (e = entriesByIndex[index]) == null) 
            throw new IllegalArgumentException("invalid cp index: " + index + "/" + entriesByIndex.length);
        return e;
    }
    
    String getUtf8KeyByIndex(int index) {
        Ent e = getByIndex(index);
        if(!(e instanceof Utf8Ent)) throw new IllegalArgumentException("that isn't a utf8 (" + e.toString() + ")");
        return ((Utf8Ent)e).s;
    }
    
    Object getKeyByIndex(int index) {
        Ent e = getByIndex(index);
        return e == null ? null : e.key();
    }
    
    NameAndTypeEnt addNameAndType(String name, String descriptor) { return (NameAndTypeEnt) add(new NameAndTypeKey(name, descriptor)); }
    Utf8Ent addUtf8(String s) { return (Utf8Ent) add(new Utf8Key(s)); }
    
    Ent add(Object o) {
        boolean isInterfaceMethod;
        if (state == SEALED) throw new IllegalStateException("constant pool is sealed");
        
        Ent ent = get(o);
        if (ent != null) {
            if (state == OPEN) ent.n++;
            return ent;
        }
        
        if (o instanceof Type.Class) { ent = new ClassEnt(((Type.Class)o).internalForm()); }
        else if (o instanceof String) { ent = new StringLitEnt((String)o); }
        else if (o instanceof Integer) { ent = new IntLitEnt(((Integer)o).intValue()); }
        else if (o instanceof Float) { ent = new FloatLitEnt(((Float)o).floatValue()); }
        else if (o instanceof Long) { ent = new LongLitEnt(((Long)o).longValue()); }
        else if (o instanceof Double) { ent = new DoubleLitEnt(((Double)o).doubleValue()); }
        else if (o instanceof Utf8Key) { ent = new Utf8Ent(((Utf8Key)o).s); }
        else if (o instanceof NameAndTypeKey) {
            NameAndTypeKey key = (NameAndTypeKey) o;
            ent = new NameAndTypeEnt(key.name,key.type);
        }
        else if ((isInterfaceMethod = o instanceof InterfaceMethodKey) || o instanceof Type.Class.Member) {
            Type.Class.Member m = isInterfaceMethod ? ((InterfaceMethodKey)o).method : (Type.Class.Member) o;
            int tag = isInterfaceMethod              ? CONSTANT_INTERFACEMETHODREF
                    : m instanceof Type.Class.Field  ? CONSTANT_FIELDREF 
                    : m instanceof Type.Class.Method ? CONSTANT_METHODREF
                    : 0;
            if (tag == 0) throw new Error("should never happen");
            ent = new MemberEnt(tag, m.getDeclaringClass(), m.name, m.getTypeDescriptor());
        } 
        else {
            throw new IllegalArgumentException("Unknown type passed to add");
        }
        
        int spaces = ent.slots();        
        if (usedSlots + spaces > 65536) throw new ClassFile.Exn("constant pool full");
        
        ent.n = state == OPEN ? 1 : usedSlots; // refcount or index

        usedSlots += spaces;        

        entries.put(o, ent);
        return ent;
    }
    
    int slots() { return usedSlots; }

    void seal() { state = SEALED; }
    
    private Ent[] asArray() {
        int count = entries.size();
        Ent[] ents = new Ent[count];
        int i=0;
        Enumeration e = entries.keys();
        while(e.hasMoreElements()) ents[i++] = (Ent) entries.get(e.nextElement());
        if (i != count) throw new Error("should never happen");
        return ents;
    }
    
    private void assignIndex(Ent[] ents) {
        int index = 1;
        entriesByIndex = new Ent[ents.length*2];
        for(int i=0;i<ents.length;i++) {
            Ent ent = ents[i];
            ent.n = index;
            entriesByIndex[index] = ent;
            index += ent.slots();
        }
    }
        
    void stable() {
        if (state != OPEN) return;
        state = STABLE;
        assignIndex(asArray());
    } 

    private static final Sort.CompareFunc compareFunc = new Sort.CompareFunc() {
        public int compare(Object a_, Object b_) {
            return ((Ent)a_).n - ((Ent)b_).n;
        }
    };
    
    private static final Sort.CompareFunc reverseCompareFunc = new Sort.CompareFunc() {
        public int compare(Object a_, Object b_) {
            return ((Ent)b_).n - ((Ent)a_).n;
        }
    };
    
    void optimize() {
        if (state != OPEN) throw new IllegalStateException("can't optimize a stable constant pool");
        Ent[] ents = asArray();
        Sort.sort(ents, reverseCompareFunc);
        state = STABLE;
        assignIndex(ents);
    }
    
    void dump(DataOutput o) throws IOException {
        Ent[] ents = asArray();
        Sort.sort(ents, compareFunc);
        o.writeShort(usedSlots);
        for(int i=0;i<ents.length;i++) {
            //System.err.println("" + ents[i].n + ": " + ents[i].toString());
            ents[i].dump(o);
        }
    }
    
    ConstantPool(DataInput in) throws ClassFile.ClassReadExn, IOException {
        usedSlots = in.readUnsignedShort();
        if (usedSlots==0) throw new ClassFile.ClassReadExn("invalid used slots");
        
        // these are to remember the descriptor ents we have to fix up
        int[] e1s = new int[usedSlots];
        int[] e2s = new int[usedSlots];
        
        entriesByIndex = new Ent[usedSlots];
        
        for(int i=1;i<usedSlots;) {
            byte tag = in.readByte();
            Ent e;
            switch(tag) {
                case CONSTANT_CLASS: // Object Type
                    e = new ClassEnt();
                    e1s[i] = in.readUnsignedShort();
                    break;
                case CONSTANT_STRING: // String
                    e = new StringLitEnt();
                    e1s[i] = in.readUnsignedShort();
                    break;
                case CONSTANT_FIELDREF: // Type.Class.Field
                case CONSTANT_METHODREF: // Type.Class.Method
                case CONSTANT_INTERFACEMETHODREF: // Instance Method Ref
                case CONSTANT_NAMEANDTYPE:
                    e = tag == CONSTANT_NAMEANDTYPE ? (Ent) new NameAndTypeEnt() : (Ent) new MemberEnt(tag);
                    e1s[i] = in.readUnsignedShort();
                    e2s[i] = in.readUnsignedShort();
                    break;
                case CONSTANT_INTEGER: // Integer
                    e = new IntLitEnt(in.readInt());
                    break;
                case CONSTANT_FLOAT: // Float
                    e = new FloatLitEnt(in.readFloat());
                    break;
                case CONSTANT_LONG: // Long
                    e = new LongLitEnt(in.readLong());
                    break;
                case CONSTANT_DOUBLE: // Double
                    e = new DoubleLitEnt(in.readDouble());
                    break;
                case CONSTANT_UTF8: // Utf8
                    e = new Utf8Ent(in.readUTF());
                    break;
                default:
                    throw new ClassFile.ClassReadExn("invalid cp ent tag: " + tag + " (slot " + i + ")");
            }
            entriesByIndex[i] = e;
            i += e.slots();
        }
        
        for(int i=1;i<usedSlots;) {
            Ent e = entriesByIndex[i];
            if (e == null) throw new Error("should never happen: " + i + "/"+usedSlots);
            boolean isMem = e instanceof MemberEnt;
            boolean isString = e instanceof StringLitEnt;
            boolean isClass =  e instanceof ClassEnt;
            boolean isNameAndType = e instanceof NameAndTypeEnt;
            if (isMem || isClass || isString || isNameAndType) {
                if (e1s[i] == 0 || e1s[i] >= usedSlots) throw new ClassFile.ClassReadExn("invalid cp index");
                Ent e1 = entriesByIndex[e1s[i]];
                if(e1 == null) throw new ClassFile.ClassReadExn("invalid cp index");
                if(isClass) {
                    if(!(e1 instanceof Utf8Ent)) throw new ClassFile.ClassReadExn("expected a uft8ent");
                    ((ClassEnt)e).utf8 = (Utf8Ent) e1;
                } else if(isString) {
                    if(!(e1 instanceof Utf8Ent)) throw new ClassFile.ClassReadExn("expected a uft8ent");
                    ((StringLitEnt)e).utf8 = (Utf8Ent) e1;
                } else if(isMem || isNameAndType) {
                    if (e2s[i] == 0 || e2s[i] >= usedSlots) throw new ClassFile.ClassReadExn("invalid cp index");
                    Ent e2 = entriesByIndex[e2s[i]];
                    if(isMem) {
                        if(!(e1 instanceof ClassEnt)) throw new ClassFile.ClassReadExn("expected a classent");
                        if(!(e2 instanceof NameAndTypeEnt)) throw new ClassFile.ClassReadExn("expected a nameandtypeent, not " + e2);
                        MemberEnt me = (MemberEnt) e;
                        me.klass = (ClassEnt) e1;
                        me.member = (NameAndTypeEnt) e2;
                    } else if(isNameAndType) {
                        if(!(e1 instanceof Utf8Ent)) throw new ClassFile.ClassReadExn("expected a uft8ent");
                        if(!(e2 instanceof Utf8Ent)) throw new ClassFile.ClassReadExn("expected a uft8ent");
                        NameAndTypeEnt nte = (NameAndTypeEnt) e;
                        nte.name = (Utf8Ent) e1;
                        nte.type = (Utf8Ent) e2;
                    }
                }
            }
            i += e.slots();
        }
        for(int i=1; i<usedSlots;) {
            Ent e = entriesByIndex[i];
            entries.put(e.key(), e);
            i += e.slots();
        }
        state = STABLE;
    }
}
