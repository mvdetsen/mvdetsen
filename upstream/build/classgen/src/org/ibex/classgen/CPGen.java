package org.ibex.classgen;

import java.util.*;
import java.io.*;

import org.ibex.classgen.util.*;

class CPGen {
    private final Hashtable entries = new Hashtable();
    private Ent[] entriesByIndex; // only valid when stable
    
    private int usedSlots = 1; // 0 is reserved
    private int state = OPEN;
    private static final int OPEN = 0;
    private static final int STABLE = 1; // existing entries won't change
    private static final int SEALED = 2; // no new entries
    
    CPGen() { }
    
    /*
     * Entries 
     */
    public abstract class Ent {
        int n; // this is the refcount if state == OPEN, index if >= STABLE
        int tag;
        
        Ent(int tag) { this.tag = tag; }
        
        void dump(DataOutput o) throws IOException { o.writeByte(tag); }
        String debugToString() { return toString(); } // so we can remove this method when not debugging
        abstract Object key() throws ClassGen.ClassReadExn; // be careful using this, it drags in a bunch of code
    }
    
    // INVARIANTS: tag == 3 || tag == 4
    class IntEnt extends Ent {
        int i;
        IntEnt(int tag) { super(tag); }
        void dump(DataOutput o) throws IOException { super.dump(o); o.writeInt(i);  }
        Object key() {
            switch(tag) {
                case 3: return new Integer(i);
                case 4: return new Float(Float.intBitsToFloat(i));
                default: throw new Error("should never happen");
            }
        }
    }
    
    // INVARIANTS: tag == 5 || tag == 6
    class LongEnt extends Ent {
        long l;
        LongEnt(int tag) { super(tag); }
        void dump(DataOutput o) throws IOException { super.dump(o); o.writeLong(l); }
        Object key() {
            switch(tag) {
                case 5: return new Long(l);
                case 6: return new Double(Double.longBitsToDouble(l));
                default: throw new Error("should never happen");
            }
        }
    }
    
    /* INVARIANTS:
        tag >= 7 && tag <= 12
        if(tag == 7 || tag == 8) e0 instanceof Utf8Ent
        if(tag == 9 || tag == 10 || tag == 11) {
            e0 instanceof CPRefEnt && e0.tag == 7
            e1 instanceof CPRefEnt && e0.tag == 12
        }
        if(tag == 12) {
            e0 instanceof Utf8Ent
        }
    */
    class CPRefEnt extends Ent {
        Ent e1;
        Ent e2;
        CPRefEnt(int tag) { super(tag); }
        
        String debugToString() { return "[" + e1.n + ":" + e1.debugToString() + (e2 == null ? "" : " + " + e2.n + ":" + e2.debugToString()) + "]"; }
        
        void dump(DataOutput o) throws IOException {
            super.dump(o);
            o.writeShort(e1.n);
            if(e2 != null) o.writeShort(e2.n);
        }
        
        private String fixme() { throw new Error("fixme"); }
        Object key() throws ClassGen.ClassReadExn {
            switch(tag) {
                case 7: return Type.instance(((Utf8Ent)e1).s);
                case 8: return (((Utf8Ent)e1).s);
                case 9: {
                    NameAndTypeKey nt = (NameAndTypeKey) e2.key();
                    Type t = Type.instance(nt.type);
                    if(t == null) throw new ClassGen.ClassReadExn("invalid type descriptor");
                    return new FieldRef((Type.Class)e1.key(), nt.name, t);
                }
                case 10: case 11: {
                    NameAndTypeKey nt = (NameAndTypeKey) e2.key();
                    if (e1.key() == null) throw new Error(e1.tag + " => " + e1.key());
                    return new MethodRef((Type.Class)e1.key(), "methodname", Type.VOID, new Type[0]); // FIXME FIXME
                }
                case 12: {
                    return new NameAndTypeKey(((Utf8Ent)e1).s, ((Utf8Ent)e2).s); 
                }
            }
            throw new Error("FIXME " + tag);
        }
    }
        
    class Utf8Ent extends Ent {
        String s;
        Utf8Ent() { super(1); }
        String debugToString() { return s; }
        void dump(DataOutput o) throws IOException { super.dump(o); o.writeUTF(s); }
        Object key() {
            return s;
        }
    }
    
    /*
     * Cache Keys
     */
    public static class Utf8Key {
        String s;
        public Utf8Key(String s) { this.s = s; }
        public boolean equals(Object o) { return o instanceof Utf8Key && ((Utf8Key)o).s.equals(s); }
        public int hashCode() { return ~s.hashCode(); }
    }
        
    public static class NameAndTypeKey {
        String name;
        String type;
        NameAndTypeKey(String name, String type) { this.name = name; this.type = type; }
        public boolean equals(Object o_) {
            if(!(o_ instanceof NameAndTypeKey)) return false;
            NameAndTypeKey o = (NameAndTypeKey) o_;
            return o.name.equals(name) && o.type.equals(type);
        }
        public int hashCode() { return name.hashCode() ^ type.hashCode(); }
    }
    
    /*
     * Methods
     */
    
    public final Ent get(Object o) { return (Ent) entries.get(o); }
    public final Ent getUtf8(String s) { return get(new Utf8Key(s)); }
    public final int getIndex(Object o) {
        Ent e = get(o);
        if(e == null) throw new IllegalStateException("entry not found");
        return getIndex(e);
    }
    public final String getUtf8ByIndex(int i) {
        return ((Utf8Ent)getByIndex(i)).s;
    }
    public final int getUtf8Index(String s) {
        Ent e = getUtf8(s);
        if(e == null) throw new IllegalStateException("entry not found");
        return getIndex(e);
    }
    public final int getIndex(Ent ent) {
        if(state < STABLE) throw new IllegalStateException("constant pool is not stable");
        return ent.n;
    }

    public final Type getType(int index) throws ClassGen.ClassReadExn {
        Ent e = getByIndex(index);
        if (e instanceof Utf8Ent) return Type.instance(((Utf8Ent)e).s);
        else return (Type)e.key();
    }

    public final Ent getByIndex(int index) {
        if(state < STABLE) throw new IllegalStateException("constant pool is not stable");
        Ent e;
        if(index >= 65536 || index >= entriesByIndex.length || (e = entriesByIndex[index]) == null) 
            throw new IllegalStateException("invalid cp index");
        return e;
    }
    
    public final Ent addNameAndType(String name, String descriptor) { return add(new NameAndTypeKey(name, descriptor)); }
    public final Ent addUtf8(String s) { return add(new Utf8Key(s)); }
    
    public final Ent add(Object o) {
        if(state == SEALED) throw new IllegalStateException("constant pool is sealed");
            
        Ent ent = get(o);
        if(ent != null) {
            if(state == OPEN) ent.n++;
            return ent;
        }
        
        if(o instanceof Type.Class) {
            CPRefEnt ce = new CPRefEnt(7);
            ce.e1 = addUtf8(((Type.Class)o).internalForm());
            ent = ce;
        } else if(o instanceof String) {
            CPRefEnt ce = new CPRefEnt(8);
            ce.e1 = addUtf8((String)o);
            ent = ce;
        } else if(o instanceof Integer) {
            IntEnt ue = new IntEnt(3);
            ue.i = ((Integer)o).intValue();
            ent = ue;
        } else if(o instanceof Float) {
            IntEnt ue = new IntEnt(4);
            ue.i = Float.floatToIntBits(((Float)o).floatValue());
            ent = ue;
        } else if(o instanceof Long) {
            LongEnt le = new LongEnt(5);
            le.l = ((Long)o).longValue();
            ent = le;
        } else if(o instanceof Double) {
            LongEnt le = new LongEnt(6);
            le.l = Double.doubleToLongBits(((Double)o).doubleValue());
            ent = le;
        } else if(o instanceof Utf8Key) {
            Utf8Ent ue = new Utf8Ent();
            ue.s = ((Utf8Key)o).s;
            ent = ue;
        } else if(o instanceof NameAndTypeKey) {
            CPRefEnt ce = new CPRefEnt(12);
            NameAndTypeKey key = (NameAndTypeKey) o;
            ce.e1 = addUtf8(key.name);
            ce.e2 = addUtf8(key.type);
            ent = ce;
        } else if(o instanceof ClassGen.FieldOrMethodRef) {
            ClassGen.FieldOrMethodRef key = (ClassGen.FieldOrMethodRef) o;
            int tag = o instanceof FieldRef ? 9 : o instanceof MethodRef ? 10 : o instanceof MethodRef.I ? 11 : 0;
            if(tag == 0) throw new Error("should never happen");
            CPRefEnt ce = new CPRefEnt(tag);
            ce.e1 = add(key.klass);
            ce.e2 = addNameAndType(key.name, key.descriptor);
            ent = ce;
        } else {
            throw new IllegalArgumentException("Unknown type passed to add");
        }
        
        int spaces = ent instanceof LongEnt ? 2 : 1;        
        if(usedSlots + spaces > 65536) throw new ClassGen.Exn("constant pool full");
        
        ent.n = state == OPEN ? 1 : usedSlots; // refcount or index

        usedSlots += spaces;        

        entries.put(o, ent);
        return ent;
    }
    
    public int slots() { return usedSlots; }

    public void seal() { state = SEALED; }
    
    private Ent[] asArray() {
        int count = entries.size();
        Ent[] ents = new Ent[count];
        int i=0;
        Enumeration e = entries.keys();
        while(e.hasMoreElements()) ents[i++] = (Ent) entries.get(e.nextElement());
        if(i != count) throw new Error("should never happen");
        return ents;
    }
    
    private void assignIndex(Ent[] ents) {
        int index = 1;
        entriesByIndex = new Ent[ents.length*2];
        for(int i=0;i<ents.length;i++) {
            Ent ent = ents[i];
            ent.n = index;
            entriesByIndex[index] = ent;
            index += ent instanceof LongEnt ? 2 : 1;
        }
    }
        
    public void stable() {
        if(state != OPEN) return;
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
    
    public void optimize() {
        if(state != OPEN) throw new IllegalStateException("can't optimize a stable constant pool");
        Ent[] ents = asArray();
        Sort.sort(ents, reverseCompareFunc);
        state = STABLE;
        assignIndex(ents);
    }
    
    public void unsafeReopen() {
        if(state == OPEN) return;
        for(int i=1;i<entriesByIndex.length;i++) {
            Ent e = entriesByIndex[i];
            if(e == null) continue;
            e.n = 0;
        }
        entriesByIndex = null;
        state = OPEN;
    }
    
    public void dump(DataOutput o) throws IOException {
        Ent[] ents = asArray();
        Sort.sort(ents, compareFunc);
        o.writeShort(usedSlots);
        for(int i=0;i<ents.length;i++) {
            //System.err.println("" + ents[i].n + ": " + ents[i].debugToString());
            ents[i].dump(o);
        }
    }
    
    CPGen(DataInput in) throws ClassGen.ClassReadExn, IOException {
        usedSlots = in.readUnsignedShort();
        if(usedSlots==0) throw new ClassGen.ClassReadExn("invalid used slots");
        
        // these are to remember the CPRefEnt e1 and e2s we have to fix up
        int[] e1s = new int[usedSlots];
        int[] e2s = new int[usedSlots];
        
        entriesByIndex = new Ent[usedSlots];
        
        for(int index=1;index<usedSlots;index++) {
            byte tag = in.readByte();
            Ent e;
            switch(tag) {
                case 7: // Object Type
                case 8: // String
                case 9: // FieldRef
                case 10: // MethodRef
                case 11: // Instance Method Ref
                case 12: // NameAndType
                {
                    e = new CPRefEnt(tag);
                    e1s[index] = in.readUnsignedShort();
                    if(tag != 7 && tag != 8) e2s[index] = in.readUnsignedShort();
                    break;
                }
                case 3: // Integer
                case 4: // Float
                {
                    IntEnt ie;
                    e = ie = new IntEnt(tag);
                    ie.i = in.readInt();
                    break;
                }
                case 5: // Long
                case 6: // Double
                {
                    LongEnt le;
                    e = le = new LongEnt(tag);
                    le.l = in.readLong();
                    break;
                }
                case 1: // Utf8
                {
                    Utf8Ent ue;
                    e = ue = new Utf8Ent();
                    ue.s = in.readUTF();
                    break;
                }
                default:
                    throw new ClassGen.ClassReadExn("invalid cp ent tag");
            }
            entriesByIndex[index] = e;
            if (e instanceof LongEnt) index++;
        }
        
        for(int index=1;index<usedSlots;index++) {
            int i = index;
            Ent e = entriesByIndex[index];
            if (e == null) throw new Error("should never happen: " + i + "/"+usedSlots);
            if (e instanceof LongEnt) {
                index++;
                continue;
            }
            if (!(e instanceof CPRefEnt)) continue;
            CPRefEnt ce = (CPRefEnt) e;
            if(e1s[i] == 0 || e1s[i] >= usedSlots) throw new ClassGen.ClassReadExn("invalid cp index");
            ce.e1 = entriesByIndex[e1s[i]];
            if(ce.e1 == null)  throw new ClassGen.ClassReadExn("invalid cp index");
            if(ce.tag != 7 && ce.tag != 8) {
                if(e2s[i] == 0 || e2s[i] >= usedSlots) throw new ClassGen.ClassReadExn("invalid cp index");
                ce.e2 = entriesByIndex[e2s[i]];
                if(ce.e2 == null)  throw new ClassGen.ClassReadExn("invalid cp index");
            }
            switch(ce.tag) {
                case 7:
                case 8:
                    if(!(ce.e1 instanceof Utf8Ent)) throw new ClassGen.ClassReadExn("expected a utf8 ent");
                    break;
                case 9:
                case 10:
                case 11:
                    if(!(ce.e1 instanceof CPRefEnt) || ((CPRefEnt)ce.e1).tag != 7)
                        throw new ClassGen.ClassReadExn("expected a type ent");
                    if(!(ce.e2 instanceof CPRefEnt) || ((CPRefEnt)ce.e2).tag != 12)
                        throw new ClassGen.ClassReadExn("expected a name and type ent");
                    break;
                case 12:
                    if(!(ce.e1 instanceof Utf8Ent)) throw new ClassGen.ClassReadExn("expected a utf8 ent");
                    if(!(ce.e2 instanceof Utf8Ent)) throw new ClassGen.ClassReadExn("expected a utf8 ent");
                    break;
            }
        }
        for(int i=1; i<usedSlots; i++) {
            Ent e = entriesByIndex[i];
            entries.put(e.key(), e);
            if (e instanceof LongEnt) i++;
        }
        state = STABLE;
    }
}
