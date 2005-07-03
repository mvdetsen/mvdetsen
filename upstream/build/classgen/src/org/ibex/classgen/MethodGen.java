package org.ibex.classgen;

import java.io.*;
import java.util.*;

/** A class representing a method in a generated classfile
    @see ClassFile#addMethod */
public class MethodGen extends Type.Class.Method.Body {
    private final static boolean EMIT_NOPS = false;
    
    private static final int NO_CODE = -1;

    public final Type.Class.Method method;
    private final ClassFile.AttrGen codeAttrs;
    private final Vector exnTable = new Vector();
    private final Hashtable thrownExceptions = new Hashtable();
    
    int maxStack = 16;
    int maxLocals;
    
    private int size;
    private int capacity;
    private byte[] op;
    private Object[] arg;
    private ConstantPool.Ent[] cparg;

   
    // Constructors //////////////////////////////////////////////////////////////////////////////

    MethodGen(Type.Class.Method method, int flags) {
        method.super(flags, new ClassFile.AttrGen());
        this.method = method;
        codeAttrs = new ClassFile.AttrGen();
        if (!isConcrete()) size = capacity = -1;
        maxLocals = Math.max(method.getNumArgs() + (flags&STATIC)==0 ? 1 : 0, 4);
    }

    MethodGen(Type.Class c, DataInput in, ConstantPool cp) throws IOException {
        this(in.readShort(), cp.getUtf8KeyByIndex(in.readShort()), c, in, cp); }

    private MethodGen(short flags, String name, Type.Class c, DataInput in, ConstantPool cp) throws IOException {
        this(flags, name, c.method(name,cp.getUtf8KeyByIndex(in.readShort())), c, in, cp); }
    private MethodGen(short flags, String name, Type.Class.Method m,
                      Type.Class c, DataInput in, ConstantPool cp) throws IOException {
        m.super(flags, new ClassFile.AttrGen(in,cp));
        this.method = m;
        
        if (isConcrete())  {
            byte[] codeAttr = (byte[]) attrs.get("Code");
            if (codeAttr == null) throw new ClassFile.ClassReadExn("code attr expected");
            DataInputStream ci = new DataInputStream(new ByteArrayInputStream(codeAttr));
            maxStack = ci.readUnsignedShort();
            maxLocals = ci.readUnsignedShort();
            int codeLen = ci.readInt();
            int[] bytecodeMap = parseCode(ci,codeLen,cp);
            int numExns = ci.readUnsignedShort();
            while(numExns-- > 0)
                exnTable.addElement(new ExnTableEnt(ci,cp,bytecodeMap));
            codeAttrs = new ClassFile.AttrGen(ci,cp);
            // FEATURE: Support these
            // NOTE: Until we can support them properly we HAVE to delete them,
            //       they'll be incorrect after we rewrite the constant pool, etc
            codeAttrs.remove("LineNumberTable");
            codeAttrs.remove("LocalVariableTable");
            
        } else {
            codeAttrs = new ClassFile.AttrGen();
        }

        if (attrs.contains("Exceptions")) {
            DataInputStream ei = new DataInputStream(new ByteArrayInputStream((byte[]) attrs.get("Exceptions")));
            int exnCount = ei.readUnsignedShort();
            while(exnCount-- > 0) {
                Type.Class t = (Type.Class) cp.getKeyByIndex(ei.readUnsignedShort());
                thrownExceptions.put(t,t);
            }
        }
    }

    // Parsing //////////////////////////////////////////////////////////////////////////////
        
    final int[] parseCode(DataInputStream in, int codeLen, ConstantPool cp) throws IOException {
        int[] map = new int[codeLen];
        int pc;
        for(pc=0;pc<map.length;pc++) map[pc] = -1;
        for(pc=0;pc<codeLen;) {
            byte op = in.readByte();
            int opdata = OP_DATA[op&0xff];
            //System.err.println("Processing " + Integer.toString(op&0xff,16) + " at " + pc);
            if ((opdata&OP_VALID_FLAG)==0) throw new ClassFile.ClassReadExn("invalid bytecode " + (op&0xff));
            int argLength = opdata & OP_ARG_LENGTH_MASK;
            int mypc = pc;
            map[mypc] = size();
            pc += 1 + (argLength == 7 ? 0 : argLength);
            if (argLength == 0)  { add(op); continue; }
            Object arg;
            switch(op) {
                case IINC:
                    arg = new Pair(in.readUnsignedByte(),in.readByte());
                    break;
                case TABLESWITCH:
                case LOOKUPSWITCH:
                    Switch si;
                    for(;(pc&3) != 0;pc++) if (in.readByte() != 0) throw new ClassFile.ClassReadExn("invalid padding");
                    int def = in.readInt() + mypc;
                    pc += 4;
                    if (op == LOOKUPSWITCH) {
                        Switch.Lookup lsi = new Switch.Lookup(in.readInt());
                        pc += 4;
                        for(int i=0;i<lsi.size();i++) {
                            lsi.setVal(i,in.readInt());
                            lsi.setTarget(i,in.readInt() + mypc);
                            pc += 8;
                        }
                        si = lsi;
                    } else {
                        int lo = in.readInt();
                        int hi = in.readInt();
                        pc += 8;
                        Switch.Table tsi = new Switch.Table(lo,hi);
                        for(int i=0;i<tsi.size();i++) { tsi.setTarget(i,in.readInt() + mypc); pc += 4; }
                        si = tsi;
                    }
                    si.setDefaultTarget(def);
                    arg = si;
                    break;
                case WIDE: {
                    byte wideop = in.readByte();
                    arg = wideop == IINC 
                        ? new Wide(wideop,in.readUnsignedShort(),in.readShort()) 
                        : new Wide(wideop,in.readUnsignedShort());
                    pc += wideop == IINC ? 5 : 3;
                    break;
                }
                case MULTIANEWARRAY:
                    arg = new MultiANewArray((Type.Array)cp.getKeyByIndex(in.readUnsignedShort()),in.readUnsignedByte());
                    break;
                case INVOKEINTERFACE: {
                    ConstantPool.Ent ent = cp.getByIndex(in.readUnsignedShort());
                    if (ent.tag != CONSTANT_INTERFACEMETHODREF) throw new ClassFile.ClassReadExn("illegal argument to bytecode");
                    arg = ((ConstantPool.InterfaceMethodKey)ent.key()).method;
                    if (in.readByte() == 0 || in.readByte() != 0)
                        throw new ClassFile.ClassReadExn("illegal count or 0 arg to invokeinterface");
                    break;
                }
                default:
                    if ((opdata&OP_CPENT_FLAG)!=0) {
                        ConstantPool.Ent ent =
                            cp.getByIndex(argLength == 2 ? in.readUnsignedShort() : argLength == 1 ? in.readUnsignedByte() : -1);
                        int tag = ent.tag;
                        Object key = ent.key();
                        switch(op) {
                            case LDC:
                            case LDC_W:
                            case LDC2_W:
                                switch(tag) {
                                    case CONSTANT_INTEGER:
                                    case CONSTANT_FLOAT:
                                    case CONSTANT_LONG:
                                    case CONSTANT_DOUBLE:
                                    case CONSTANT_STRING:
                                    case CONSTANT_CLASS:
                                        break;
                                    default:
                                        throw new ClassFile.ClassReadExn("illegal argument to bytecode 0x" +
                                                                         Integer.toString(op&0xff,16));
                                }
                                break;
                            case GETSTATIC:
                            case PUTSTATIC:
                            case GETFIELD:
                            case PUTFIELD:
                                if (tag != CONSTANT_FIELDREF)
                                    throw new ClassFile.ClassReadExn("illegal argument to bytecode 0x" +
                                                                     Integer.toString(op&0xff,16));
                                break;
                            case INVOKEVIRTUAL:
                            case INVOKESPECIAL:
                            case INVOKESTATIC:
                                if (tag != CONSTANT_METHODREF)
                                    throw new ClassFile.ClassReadExn("illegal argument to bytecode 0x" +
                                                                     Integer.toString(op&0xff,16));
                                break;
                            case NEW:
                            case ANEWARRAY:
                            case CHECKCAST:
                            case INSTANCEOF:
                                if (tag != CONSTANT_CLASS)
                                    throw new ClassFile.ClassReadExn("illegal argument to bytecode 0x" +
                                                                     Integer.toString(op&0xff,16));
                                break;                        
                            default:
                                throw new Error("should never happen");
                        }
                        arg = key;
                    } else {
                        // treat everything else (including branches for now) as plain old ints
                        int n;
                        boolean unsigned = (opdata&OP_UNSIGNED_FLAG)!=0;
                        if (argLength == 1) n = unsigned ? in.readUnsignedByte() : in.readByte();
                        else if (argLength == 2) n = unsigned ? in.readUnsignedShort() : in.readShort();
                        else throw new Error("should never happen");
                        if ((opdata&OP_BRANCH_FLAG)!=0) n += mypc;
                        arg = N(n);
                    }
                    break;
            }
            add(op,arg);
        }
        if (pc != codeLen)
            throw new ClassFile.ClassReadExn("didn't read enough code (" + pc + "/" + codeLen + " in " + method.name + ")");
        for(int i=0;i<size();i++) {
            switch(op[i]) {
                case TABLESWITCH:
                case LOOKUPSWITCH:
                {
                    Switch si = (Switch) arg[i];
                    
                    int pos = map[si.getDefaultTarget()];
                    if (pos < 0)
                        throw new ClassFile.ClassReadExn("default target points to invalid bytecode: " + si.getDefaultTarget());
                    si.setDefaultTarget(pos);
                    
                    for(int j=0;j<si.size();j++) {
                        pos = map[si.getTarget(j)];
                        if (pos < 0)  throw new ClassFile.ClassReadExn("target points to invalid bytecode");
                        si.setTarget(j,pos);
                    }
                    break;
                }
                default:
                    if (OP_BRANCH(op[i])) {
                        int pos = map[((Integer)arg[i]).intValue()];
                        if (pos < 0)  throw new ClassFile.ClassReadExn("branch points to invalid bytecode");
                        arg[i] = N(pos);
                    }
                    break;
            }
        }
        return map;
    }

    // Exception Table //////////////////////////////////////////////////////////////////////////////
   
    class ExnTableEnt {
        final int start;
        final int end;
        final int handler;
        final Type.Class type; // null type means all exceptions (for finally)
        
        ExnTableEnt(DataInput in, ConstantPool cp, int[] bytecodeMap) throws IOException {
            int startPC = in.readUnsignedShort();
            int endPC = in.readUnsignedShort();
            int handlerPC = in.readUnsignedShort();
            int index = in.readUnsignedShort();
            this.type = index == 0 ? null : (Type.Class) cp.getKeyByIndex(index);
            int max = bytecodeMap.length;
            if (startPC >= max || bytecodeMap[startPC] < 0) throw new ClassFile.ClassReadExn("invalid startPC");
            if (endPC >= max || bytecodeMap[endPC] < 0) throw new ClassFile.ClassReadExn("invalid startPC");
            if (handlerPC >= max || bytecodeMap[handlerPC] < 0) throw new ClassFile.ClassReadExn("invalid startPC");
            this.start = bytecodeMap[startPC];
            this.end = bytecodeMap[endPC];
            this.handler = bytecodeMap[handlerPC];
        }
        ExnTableEnt(int start, int end, int handler, Type.Class type) {
            this.start = start;
            this.end = end;
            this.handler = handler;
            this.type = type;
        }
        void finish(ConstantPool cp) { if (type != null) cp.add(type); }
        void dump(DataOutput o, int[] pc, int endPC, ConstantPool cp) throws IOException {
            o.writeShort(pc[start]);
            o.writeShort(end==pc.length ? endPC : pc[end]);
            o.writeShort(pc[handler]);
            o.writeShort(type == null ? 0 : cp.getIndex(type));
        }
    }
    
    /** Adds an exception handler for the range [<i>start</i>, <i>end</i>) pointing to <i>handler</i>
        @param start The instruction to start at (inclusive)
        @param end The instruction to end at (exclusive)
        @param handler The instruction of the excepton handler
        @param type The type of exception that is to be handled (MUST inherit from Throwable)
    */
    public final void addExceptionHandler(int start, int end, int handler, Type.Class type) {
        exnTable.addElement(new ExnTableEnt(start, end, handler, type));
    }
    
    /** Adds a exception type that can be thrown from this method
        NOTE: This isn't enforced by the JVM. This is for reference
        only. A method can throw exceptions not declared to be thrown
        @param type The type of exception that can be thrown 
    */
    public final void addThrow(Type.Class type) { thrownExceptions.put(type, type); }
    
    private final void grow() { if (size == capacity) grow(size+1); }
    private final void grow(int newCap) {
        if (capacity == NO_CODE) throw new IllegalStateException("method can't have code");
        if (newCap <= capacity) return;
        newCap = Math.max(newCap, capacity == 0 ? 256 : capacity*2);
        
        byte[] op2 = new byte[newCap];
        if (capacity != 0) System.arraycopy(op, 0, op2, 0, size);
        op = op2;
        
        Object[] arg2 = new Object[newCap];
        if (capacity != 0) System.arraycopy(arg, 0, arg2, 0, size);
        arg = arg2;
        
        capacity = newCap;
    }

    // Accessors //////////////////////////////////////////////////////////////////////////////
    
    public int getFlags() { return flags; }
    public Hashtable getThrownExceptions() { return thrownExceptions; }

    /** Returns the size (in instructions) of this method 
        @return The size of the method (in instructions)
    */
    public final int size() { return size; }
    
    // These two are optimized for speed, they don't call set() below
    /** Add a bytecode (with no argument) to the method */
    public final int add(byte op) {
        int s = size;
        if (s == capacity) grow();
        this.op[s] = op;
        size++;
        return s;
    }

    /** Set the bytecode at position <i>pos</i> to <i>op</i> */
    public final void set(int pos, byte op) { this.op[pos] = op; }
        
    /** Adds a bytecode, <i>op</i>, with argument <i>arg</i> to the method 
        @return The position of the new bytecode
        */
    public final int add(byte op, Object arg) { if (capacity == size) grow(); set(size, op, arg); return size++; }

    /** Adds a bytecode with a boolean argument - equivalent to add(op, arg?1:0);
        @return The position of the new bytecode
        @see #add(byte, int)
    */
    public final int add(byte op, boolean arg) { if (capacity == size) grow(); set(size, op, arg); return size++; }

    /** Adds a bytecode with an integer argument. This is equivalent
     * to add(op, new Integer(arg)), but optimized to prevent the
     * allocation when possible
        @return The position of the new bytecode
        @see #add(byte, Object)
    */
    public final int add(byte op, int arg) { if (capacity == size) grow(); set(size, op, arg); return size++; }
    
    /** Gets the bytecode at position <i>pos</i>
        @exception ArrayIndexOutOfBoundException if pos < 0 || pos >= size()
    */
    public final byte get(int pos) { return op[pos]; }
    
    /** Gets the bytecode at position <i>pos</i>. NOTE: This isn't necessarily the same object that was set with add or set.
        Arguments for instructions which access the constant pool (LDC, INVOKEVIRTUAL, etc) are converted to a more efficient
        interal form when they are added. The value returned from this method for these instruction can be reused, but there
        is no way to retrieve the original object 
        @exception ArrayIndexOutOfBoundException if pos < 0 || pos >= size()
    */    
    public final Object getArg(int pos) { return arg[pos]; }
    
    /** Sets the argument for <i>pos</i> to <i>arg</i>. This is
     * equivalent to set(pos, op, new Integer(arg)), but optimized to
     * prevent the allocation when possible.
        @exception ArrayIndexOutOfBoundException if pos < 0 || pos >= size()
        @see #setArg(int, Object) */
    public final void setArg(int pos, int arg) { set(pos, op[pos], N(arg)); }

    /** Sets the argument for <i>pos</i> to <i>arg</i>.
        @exception ArrayIndexOutOfBoundException if pos < 0 || pos >= size() */
    public final void setArg(int pos, Object arg) { set(pos, op[pos], arg); }
    
    /** Sets the bytecode and argument  at <i>pos</i> to <i>op</i> and <i>arg</i> respectivly. 
        This is equivalent to set(pos, op, arg?1:0) 
        @exception ArrayIndexOutOfBoundException if pos < 0 || pos >= size()
    */
    public final void set(int pos, byte op, boolean arg) { set(pos, op, arg?1:0); }
    
    // This MUST handle x{LOAD, STORE} and LDC with an int arg WITHOUT falling back to set(int, byte, Object)
    /** Sets the bytecode and argument  at <i>pos</i> to <i>op</i> and <i>n</i> respectivly.
        This is equivalent to set(pos, op, new Integer(n)), but optimized to prevent the allocation when possible.
        @exception ArrayIndexOutOfBoundException if pos < 0 || pos >= size()
    */
    public final void set(int pos, byte op, int n) {
        Object arg = null;
        OUTER: switch(op) {
            case LDC:
                switch(n) {
                    case -1: op = ICONST_M1; break OUTER;
                    case 0:  op = ICONST_0;  break OUTER;
                    case 1:  op = ICONST_1;  break OUTER;
                    case 2:  op = ICONST_2;  break OUTER; 
                    case 3:  op = ICONST_3;  break OUTER;
                    case 4:  op = ICONST_4;  break OUTER;
                    case 5:  op = ICONST_5;  break OUTER;
                }
                if (n >= -128 && n <= 127) { op = BIPUSH; arg = N(n); } 
                else if (n >= -32768 && n <= 32767) { op = SIPUSH; arg = N(n); }
                else { arg = N(n); }
                break;
            case ILOAD: case ISTORE: case LLOAD: case LSTORE: case FLOAD:
            case FSTORE: case DLOAD: case DSTORE: case ALOAD: case ASTORE:
                if (n >= maxLocals) maxLocals = n + 1;
                if (n >= 0 && n <= 3) {
                    byte base = 0;
                    switch(op) {
                        case ILOAD:  base = ILOAD_0;  break;
                        case ISTORE: base = ISTORE_0; break;
                        case LLOAD:  base = LLOAD_0;  break;
                        case LSTORE: base = LSTORE_0; break; 
                        case FLOAD:  base = FLOAD_0;  break;
                        case FSTORE: base = FSTORE_0; break;
                        case DLOAD:  base = DLOAD_0;  break;
                        case DSTORE: base = DSTORE_0; break;
                        case ALOAD:  base = ALOAD_0;  break;
                        case ASTORE: base = ASTORE_0; break;
                    }
                    op = (byte)((base&0xff) + n);
                } else {
                    arg = N(n);
                }
                break;
            default:
                set(pos, op, N(n));
                return;
        }            
        this.op[pos] = op;
        this.arg[pos] = arg;
    }
    
    /** Sets the bytecode and argument  at <i>pos</i> to <i>op</i> and <i>arg</i> respectivly.
        @exception ArrayIndexOutOfBoundException if pos < 0 || pos >= size()
        */
    public final void set(int pos, byte op, Object arg) {
        switch(op) {
            case ILOAD: case ISTORE: case LLOAD: case LSTORE: case FLOAD:
            case FSTORE: case DLOAD: case DSTORE: case ALOAD: case ASTORE:
                // set(int, byte, int) always handles these ops itself
                set(pos, op, ((Integer)arg).intValue());
                return;
            case LDC:
                // set(int, byte, int) always handles these opts itself
                if (arg instanceof Integer) { set(pos, op, ((Integer)arg).intValue()); return; }
                if (arg instanceof Boolean) { set(pos, op, ((Boolean)arg).booleanValue()); return; }
                
                if (arg instanceof Long) {
                    long l = ((Long)arg).longValue();
                    if (l == 0L || l == 1L) {
                        this.op[pos] = l == 0L ? LCONST_0 : LCONST_1;
                        this.arg[pos] = null; 
                        return;
                    }
                    op = LDC2_W;
                } else if (arg instanceof Double) {
                    op = LDC2_W;
                }
                break;
        }
        if ((OP_DATA[op&0xff]&OP_VALID_FLAG) == 0) throw new IllegalArgumentException("unknown bytecode");
        this.op[pos] = op;
        this.arg[pos] = arg;
    }
    
    /** Sets the maximum number of locals in the function to
        <i>maxLocals</i>. NOTE: This defaults to 0 and is
        automatically increased as necessary when *LOAD/*STORE
        bytecodes are added. You do not need to call this function in
        most cases */
    public void setMaxLocals(int maxLocals) { this.maxLocals = maxLocals; }

    /** Sets the maxinum size of th stack for this function to
     * <i>maxStack</i>. This defaults to 16< */
    public void setMaxStack(int maxStack) { this.maxStack = maxStack; }
    

    // Bytecode-Specific inner classes ////////////////////////////////////////////////////////////////////////////////

    public static abstract class Switch {
        public final Object[] targets;
        public Object defaultTarget;

        Switch(int size) { targets = new Object[size]; }
        public void setTarget(int pos, Object val) { targets[pos] = val; }
        public void setTarget(int pos, int val) { targets[pos] = N(val); }
        public void setDefaultTarget(int val) { setDefaultTarget(N(val)); }
        public void setDefaultTarget(Object o) { defaultTarget = o; }
        public int size() { return targets.length; }
        
        public int getTarget(int pos) { return ((Integer)targets[pos]).intValue(); }
        public int getDefaultTarget() { return ((Integer)defaultTarget).intValue(); }   
        
        abstract int length();
    
        public static class Table extends Switch {
            public final int lo;
            public final int hi;
            public Table(int lo, int hi) {
                super(hi-lo+1);
                this.lo = lo;
                this.hi = hi;
            }
            public void setTargetForVal(int val, Object o) { setTarget(val-lo, o); }
            public void setTargetForVal(int val, int n) { setTarget(val-lo, n); }
            
            int length() { return 12 + targets.length * 4; } // 4bytes/target, hi, lo, default
        }
    
        public static class Lookup extends Switch {
            public final int[] vals;
            public Lookup(int size) {
                super(size);
                this.vals = new int[size];
            }
            public final void setVal(int pos, int val) { vals[pos] = val; }
            
            int length() { return 8 + targets.length * 8; } // key/val per target, default, count
        }
    }
    
    /** This class represents the arguments to byecodes that take two integer arguments. */
    public static class Pair {
        public int i1;
        public int i2;
        public Pair(int i1, int i2) { this.i1 = i1; this.i2 = i2; }
    }
    
    public static class MultiANewArray {
        public Type.Array type;
        public int dims;
        public MultiANewArray(Type.Array type, int dims) { this.type = type; this.dims = dims; }
    }
    
    public static class Wide {
        public final byte op;
        public final int varNum;
        public final int n;
        Wide(byte op, int varNum) { this(op, varNum, 0); }
        Wide(byte op, int varNum, int n) { this.op = op; this.varNum = varNum; this.n = n; }
    }


    // Emitting Bits //////////////////////////////////////////////////////////////////////////////
   
    private Object resolveTarget(Object arg) {
        int target;
        if (arg instanceof PhantomTarget) {
            target = ((PhantomTarget)arg).getTarget();
            if (target == -1) throw new IllegalStateException("unresolved phantom target");
            arg = N(target);
        } else {
            target = ((Integer)arg).intValue();
        }
        if (target < 0 || target >= size)
            throw new IllegalStateException("invalid target address " + target + "/" + size);
        return arg;
    }
    
    /** Computes the final bytecode for this method. 
        @exception IllegalStateException if the data for a method is in an inconsistent state (required arguments missing, etc)
        @exception Exn if the byteocode could not be generated for any other reason (constant pool full, etc)
    */
    void finish(ConstantPool cp) {
        cp.addUtf8(method.name);
        cp.addUtf8(method.getTypeDescriptor());
        
        for(Enumeration e = thrownExceptions.keys();e.hasMoreElements();)
            cp.add(e.nextElement());
        
        if (size == NO_CODE) return;
        for(int i=0;i<exnTable.size();i++)
            ((ExnTableEnt)exnTable.elementAt(i)).finish(cp);
        
        // We'll set these correctly later
        if ((flags & (NATIVE|ABSTRACT))==0) attrs.put("Code","");
        if (thrownExceptions.size() > 0) attrs.put("Exceptions","");
        attrs.finish(cp);
        codeAttrs.finish(cp);
        
        cparg = new ConstantPool.Ent[size];
        
        for(int i=0, p=0;i<size;i++) {
            switch(op[i]) {
                case LDC:
                case LDC_W:
                case LDC2_W:
                case GETSTATIC:
                case PUTSTATIC:
                case GETFIELD:
                case PUTFIELD:
                case INVOKEVIRTUAL:
                case INVOKESPECIAL:
                case INVOKESTATIC:
                case NEW:
                case ANEWARRAY:
                case CHECKCAST:
                case INSTANCEOF:
                    cparg[i] = cp.add(arg[i]);
                    break;
                case INVOKEINTERFACE:
                    cparg[i] = cp.add(new ConstantPool.InterfaceMethodKey((Type.Class.Method)arg[i]));
                    break;
                case MULTIANEWARRAY:
                    cparg[i] = cp.add(((MultiANewArray)arg[i]).type);
                    break;
            }
        }
    }

    private void generateCode(ConstantPool cp) {
        try {
            _generateCode(cp);
        } catch(IOException e) {
            throw new Error("should never happen");
        }
    }
    
    private void _generateCode(ConstantPool cp) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutput o = new DataOutputStream(baos);
    
        int[] pc = new int[size];
        int[] maxpc = pc;
        int p, i;
        
        // Pass1 - Calculate maximum pc of each bytecode, widen some insns, resolve any unresolved jumps, etc
        for(i=0, p=0;i<size;i++) {
            byte op = this.op[i];
            int opdata = OP_DATA[op&0xff];
            int j;
            maxpc[i] = p;
            
            if ((opdata & OP_BRANCH_FLAG)!= 0) { 
                try { 
                    arg[i] = resolveTarget(arg[i]);
                } catch(RuntimeException e) {
                    System.err.println("WARNING: Error resolving target for " + Integer.toHexString(op&0xff));
                    throw e;
                }
            }
            
            switch(op) {
                // Speical caculations
                case GOTO:
                case JSR: {
                    int arg = ((Integer)this.arg[i]).intValue();
                    if (arg < i && p - maxpc[arg] <= 32768) p += 3; 
                    else p += 5;
                    continue;
                }
                case NOP:
                    if (EMIT_NOPS) p++;
                    continue;
                case LOOKUPSWITCH:
                case TABLESWITCH: {
                    Switch si = (Switch) arg[i];
                    Object[] targets = si.targets;
                    for(j=0;j<targets.length;j++) targets[j] = resolveTarget(targets[j]);
                    si.defaultTarget = resolveTarget(si.defaultTarget);
                    p += 1 + 3 + si.length(); // opcode itself, padding, data
                    if (op == LOOKUPSWITCH) { // verify sanity of lookupswitch vals
                        int[] vals = ((Switch.Lookup)si).vals;
                        for(j=1;j<vals.length;j++)
                            if (vals[j] <= vals[j-1])
                                throw new IllegalStateException("out of order/duplicate lookupswitch values");
                    }
                    continue;
                }
                case WIDE:
                    p += ((Wide)arg[i]).op == IINC ? 5 : 3;
                    continue;
                // May need widening
                case ILOAD: case ISTORE: case LLOAD: case LSTORE: case FLOAD:
                case FSTORE: case DLOAD: case DSTORE: case ALOAD: case ASTORE:
                case RET: {
                    int arg = ((Integer)this.arg[i]).intValue();
                    if (arg > 255) {
                        this.op[i] = WIDE;
                        this.arg[i] = new Wide(op, arg);
                        p += 4;
                        continue;
                    }
                    break;
                }
                case IINC: {
                    Pair pair = (Pair) this.arg[i];
                    if (pair.i1 > 255 || pair.i2 < -128 || pair.i2 > 127) {
                        this.op[i] = WIDE;
                        this.arg[i] = new Wide(IINC, pair.i1, pair.i2);
                        p += 6;
                        continue;
                    }
                    break;
                }
                case LDC:
                    j = cp.getIndex(cparg[i]);
                    if (j >= 256) {
                        this.op[i] = op = LDC_W;
                        p += 3;
                        continue;
                    }
                    break;
                default:
            }
            if ((j = (opdata&OP_ARG_LENGTH_MASK)) == 7) throw new Error("shouldn't be here " + Integer.toString(op&0xff,16));
            p += 1 + j;
        }
        
        // Pass2 - Widen instructions if they can possibly be too short
        for(i=0;i<size;i++) {
            switch(op[i]) {
                case GOTO:
                case JSR: {
                    int arg = ((Integer)this.arg[i]).intValue();
                    int diff = maxpc[arg] - maxpc[i];
                    if (diff < -32768 || diff > 32767)
                        op[i] = op[i] == GOTO ? GOTO_W : JSR_W;
                    break;
                }
            }
        }
        
        // Pass3 - Calculate actual pc
        for(i=0, p=0;i<size;i++) {
            byte op = this.op[i];
            pc[i] = p;
            switch(op) {
                case NOP:
                    if (EMIT_NOPS) p++;
                    break;
                case TABLESWITCH:
                case LOOKUPSWITCH: {
                    Switch si = (Switch) arg[i];
                    p++; // opcode itself
                    p = (p + 3) & ~3; // padding
                    p += 4; // default
                    if (op == TABLESWITCH) p += 4 + 4 + si.size() * 4; // lo, hi, targets
                    else p += 4 + si.size() * 4 * 2; // count, key, val * targets
                    break;
                }
                case WIDE:
                    p += 2 + (((Wide)arg[i]).op == IINC ? 4 : 2);
                    break;                
                default: {
                    int l = OP_DATA[op&0xff] & OP_ARG_LENGTH_MASK;
                    if (l == 7) throw new Error("shouldn't be here");
                    p += 1 + l;                    
                }
            }
        }
        int codeSize = p;
        
        if (codeSize >= 65536) throw new ClassFile.Exn("method too large in size");
        
        o.writeShort(maxStack);
        o.writeShort(maxLocals);
        o.writeInt(codeSize);
        
        // Pass 4 - Actually write the bytecodes
        for(i=0;i<size;i++) {
            byte op = this.op[i];
            int opdata = OP_DATA[op&0xff];
            if (op == NOP && !EMIT_NOPS) continue;
            o.writeByte(op);
            int argLength = opdata & OP_ARG_LENGTH_MASK;
            
            if (argLength == 0) continue; // skip if no args
            
            // Write args
            Object arg = this.arg[i];  
            
            switch(op) {
                case IINC: {
                    Pair pair = (Pair) arg;
                    if (pair.i1 > 255 || pair.i2 < -128 || pair.i2 > 127) throw new ClassFile.Exn("overflow of iinc arg"); 
                    o.writeByte(pair.i1);
                    o.writeByte(pair.i2);
                    break;
                }
                case TABLESWITCH:
                case LOOKUPSWITCH: {
                    Switch si = (Switch) arg;
                    int mypc = pc[i];
                    for(p = pc[i]+1;(p&3)!=0;p++) o.writeByte(0);
                    o.writeInt(pc[si.getDefaultTarget()] - mypc);
                    if (op == LOOKUPSWITCH) {
                        int[] vals = ((Switch.Lookup)si).vals;
                        o.writeInt(si.size());
                        for(int j=0;j<si.size();j++) {
                            o.writeInt(vals[j]);
                            o.writeInt(pc[si.getTarget(j)] - mypc);
                        }
                    } else {
                        Switch.Table tsi = (Switch.Table) si;
                        o.writeInt(tsi.lo);
                        o.writeInt(tsi.hi);
                        for(int j=0;j<tsi.size();j++) o.writeInt(pc[tsi.getTarget(j)] - mypc);
                    }
                    break;
                }
                case WIDE: {
                    Wide wide = (Wide) arg;
                    o.writeByte(wide.op);
                    o.writeShort(wide.varNum);
                    if (wide.op == IINC) o.writeShort(wide.n);
                    break;
                }
                case MULTIANEWARRAY: {
                    o.writeShort(cp.getIndex(cparg[i]));
                    int v = ((MultiANewArray) arg).dims;
                    if (v >= 256) throw new ClassFile.Exn("overflow of dimensions in multianewarray");
                    o.writeByte(v);
                    break;
                }
                case INVOKEINTERFACE:
                    o.writeShort(cp.getIndex(cparg[i]));
                    o.writeByte(((Type.Class.Method)arg).argTypes.length + 1);
                    o.writeByte(0);
                    break;
                default:
                    if ((opdata & OP_BRANCH_FLAG) != 0) {
                        int v = pc[((Integer)arg).intValue()] - pc[i];
                        if (argLength == 2) {
                            if (v < -32768 || v > 32767) throw new ClassFile.Exn("overflow of s2 offset");
                            o.writeShort(v);
                        } else if (argLength == 4) {
                            o.writeInt(v);
                        } else {
                            throw new Error("should never happen");
                        }
                    } else if ((opdata & OP_CPENT_FLAG) != 0) {
                        int v = cp.getIndex(cparg[i]);
                        if (argLength == 1) o.writeByte(v);
                        else if (argLength == 2) o.writeShort(v);
                        else throw new Error("should never happen");
                    } else if (argLength == 7) {
                        throw new Error("should never happen - variable length instruction not explicitly handled");
                    } else {
                        int iarg  = ((Integer)arg).intValue();
                        if (argLength == 1) {
                            if ((opdata & OP_UNSIGNED_FLAG) != 0 ? iarg >= 256 : (iarg < -128 || iarg >= 128))
                                throw new ClassFile.Exn("overflow of s/u1 option");
                            o.writeByte(iarg);
                        } else if (argLength == 2) {
                            if ((opdata & OP_UNSIGNED_FLAG) != 0 ? iarg >= 65536 : (iarg < -32768 || iarg >= 32768))
                                throw new ClassFile.Exn("overflow of s/u2 option");
                            o.writeShort(iarg);
                        } else {
                            throw new Error("should never happen");
                        }
                    }
                    break;
            }
        }

        //if (baos.size() - 8 != codeSize) throw new Error("we didn't output what we were supposed to");
        
        o.writeShort(exnTable.size());
        for(i=0;i<exnTable.size();i++)
            ((ExnTableEnt)exnTable.elementAt(i)).dump(o, pc, codeSize, cp);
        
        codeAttrs.dump(o,cp);
        baos.close();
        
        byte[] codeAttribute = baos.toByteArray();
        attrs.put("Code", codeAttribute);        
    }
        
    void generateExceptions(ConstantPool cp) throws IOException {
        if (thrownExceptions.size() > 0) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream o = new DataOutputStream(baos);
            o.writeShort(thrownExceptions.size());
            for(Enumeration e = thrownExceptions.keys();e.hasMoreElements();)
                o.writeShort(cp.getIndex(thrownExceptions.get(e.nextElement())));
            baos.close();
            attrs.put("Exceptions", baos.toByteArray());
        }
    }
    
    void dump(DataOutput o, ConstantPool cp) throws IOException {
        if ((flags & (NATIVE|ABSTRACT))==0) generateCode(cp);
        generateExceptions(cp);
        
        o.writeShort(flags);
        o.writeShort(cp.getUtf8Index(method.name));
        o.writeShort(cp.getUtf8Index(method.getTypeDescriptor()));
        attrs.dump(o,cp);
    }
    
   
    /** Class that represents a target that isn't currently know. The
        target MUST be set with setTarget() before the classfile is
        written.  This class is more or less a mutable integer */
    public static class PhantomTarget {
        private int target = -1;
        public void setTarget(int target) { this.target = target; }
        public int getTarget() { return target; }
    }
    
    private static Integer N(int n) { return new Integer(n); }
    private static Long N(long n) { return new Long(n); }
    private static Float N(float f) { return new Float(f); }
    private static Double N(double d) { return new Double(d); }
    private static int max(int a, int b) { return a > b ? a : b; }
    
    private static final int OP_BRANCH_FLAG = 1<<3;
    private static final int OP_CPENT_FLAG = 1<<4;
    private static final int OP_UNSIGNED_FLAG = 1<<5;
    private static final int OP_VALID_FLAG = 1<<6;
    private static final int OP_ARG_LENGTH_MASK = 7;
    private static final boolean OP_VALID(byte op) { return (OP_DATA[op&0xff] & OP_VALID_FLAG) != 0; }
    private static final int OP_ARG_LENGTH(byte op) { return (OP_DATA[op&0xff]&OP_ARG_LENGTH_MASK); }
    private static final boolean OP_CPENT(byte op) { return (OP_DATA[op&0xff]&OP_CPENT_FLAG) != 0; }
    private static final boolean OP_BRANCH(byte op) { return (OP_DATA[op&0xff]&OP_BRANCH_FLAG ) != 0; }
    private static final boolean OP_UNSIGNED(byte op) { return (OP_DATA[op&0xff]&OP_UNSIGNED_FLAG ) != 0; }
    
    // Run perl -x src/org/ibex/classgen/CGConst.java to generate this
    private static final byte[] OP_DATA = {
        0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 
        0x41, 0x42, 0x51, 0x52, 0x52, 0x61, 0x61, 0x61, 0x61, 0x61, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 
        0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 
        0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x61, 0x61, 0x61, 0x61, 0x61, 0x40, 0x40, 0x40, 0x40, 0x40, 
        0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 
        0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 
        0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 
        0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 
        0x40, 0x40, 0x40, 0x40, 0x42, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 
        0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x4a, 0x4a, 0x4a, 0x4a, 0x4a, 0x4a, 0x4a, 
        0x4a, 0x4a, 0x4a, 0x4a, 0x4a, 0x4a, 0x4a, 0x4a, 0x4a, 0x41, 0x47, 0x47, 0x40, 0x40, 0x40, 0x40, 
        0x40, 0x40, 0x52, 0x52, 0x52, 0x52, 0x52, 0x52, 0x52, 0x54, 0x01, 0x52, 0x41, 0x52, 0x40, 0x40, 
        0x52, 0x52, 0x40, 0x40, 0x47, 0x53, 0x4a, 0x4a, 0x4c, 0x4c, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 
        0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 
        0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 
        0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01
    };

    // Debugging //////////////////////////////////////////////////////////////////////////////

    public void debugBodyToString(StringBuffer sb) {
        // This is intentionally a local variable so it can be removed by gcclass
        final String[] OP_NAMES = new String[]{
            "nop", "aconst_null", "iconst_m1", "iconst_0", "iconst_1", "iconst_2", 
            "iconst_3", "iconst_4", "iconst_5", "lconst_0", "lconst_1", "fconst_0", 
            "fconst_1", "fconst_2", "dconst_0", "dconst_1", "bipush", "sipush", 
            "ldc", "ldc_w", "ldc2_w", "iload", "lload", "fload", 
            "dload", "aload", "iload_0", "iload_1", "iload_2", "iload_3", 
            "lload_0", "lload_1", "lload_2", "lload_3", "fload_0", "fload_1", 
            "fload_2", "fload_3", "dload_0", "dload_1", "dload_2", "dload_3", 
            "aload_0", "aload_1", "aload_2", "aload_3", "iaload", "laload", 
            "faload", "daload", "aaload", "baload", "caload", "saload", 
            "istore", "lstore", "fstore", "dstore", "astore", "istore_0", 
            "istore_1", "istore_2", "istore_3", "lstore_0", "lstore_1", "lstore_2", 
            "lstore_3", "fstore_0", "fstore_1", "fstore_2", "fstore_3", "dstore_0", 
            "dstore_1", "dstore_2", "dstore_3", "astore_0", "astore_1", "astore_2", 
            "astore_3", "iastore", "lastore", "fastore", "dastore", "aastore", 
            "bastore", "castore", "sastore", "pop", "pop2", "dup", 
            "dup_x1", "dup_x2", "dup2", "dup2_x1", "dup2_x2", "swap", 
            "iadd", "ladd", "fadd", "dadd", "isub", "lsub", 
            "fsub", "dsub", "imul", "lmul", "fmul", "dmul", 
            "idiv", "ldiv", "fdiv", "ddiv", "irem", "lrem", 
            "frem", "drem", "ineg", "lneg", "fneg", "dneg", 
            "ishl", "lshl", "ishr", "lshr", "iushr", "lushr", 
            "iand", "land", "ior", "lor", "ixor", "lxor", 
            "iinc", "i2l", "i2f", "i2d", "l2i", "l2f", 
            "l2d", "f2i", "f2l", "f2d", "d2i", "d2l", 
            "d2f", "i2b", "i2c", "i2s", "lcmp", "fcmpl", 
            "fcmpg", "dcmpl", "dcmpg", "ifeq", "ifne", "iflt", 
            "ifge", "ifgt", "ifle", "if_icmpeq", "if_icmpne", "if_icmplt", 
            "if_icmpge", "if_icmpgt", "if_icmple", "if_acmpeq", "if_acmpne", "goto", 
            "jsr", "ret", "tableswitch", "lookupswitch", "ireturn", "lreturn", 
            "freturn", "dreturn", "areturn", "return", "getstatic", "putstatic", 
            "getfield", "putfield", "invokevirtual", "invokespecial", "invokestatic", "invokeinterface", 
            "", "new", "newarray", "anewarray", "arraylength", "athrow", 
            "checkcast", "instanceof", "monitorenter", "monitorexit", "wide", "multianewarray", 
            "ifnull", "ifnonnull", "goto_w", "jsr_w", "", "", 
            "", "", "", "", "", "", 
            "", "", "", "", "", "", 
            "", "", "", "", "", "", 
            "", "", "", "", "", "", 
            "", "", "", "", "", "", 
            "", "", "", "", "", "", 
            "", "", "", "", "", "", 
            "", "", "", "", "", "", 
            "", "", "", ""
        };
        for(int i=0;i<size();i++) {
            sb.append("    ");
            for(int j=i==0?1:i;j<10000;j*=10) sb.append(" ");
            sb.append(i).append(": ");
            sb.append(OP_NAMES[op[i]&0xff]);
            String s = null;
            if (arg[i] instanceof Type) s = ((Type)arg[i]).toString();
            else if (arg[i] instanceof Type.Class.Member) s = ((Type.Class.Member)arg[i]).toString();
            else if (arg[i] instanceof String) s = "\"" + arg[i] + "\"";
            else if (arg[i] != null) s = arg[i].toString();
            if (s != null) sb.append(" ").append(s);
            sb.append("\n");
        }
    }

    // Unused //////////////////////////////////////////////////////////////////////////////

    /** Negates the IF* instruction, <i>op</i>  (IF_ICMPGT -> IF_ICMPLE, IFNE -> IFEQ,  etc)
        @exception IllegalArgumentException if <i>op</i> isn't an IF* instruction */
    public static byte negate(byte op) {
        switch(op) {
            case IFEQ: return IFNE;
            case IFNE: return IFEQ;
            case IFLT: return IFGE;
            case IFGE: return IFLT;
            case IFGT: return IFLE;
            case IFLE: return IFGT;
            case IF_ICMPEQ: return IF_ICMPNE;
            case IF_ICMPNE: return IF_ICMPEQ;
            case IF_ICMPLT: return IF_ICMPGE;
            case IF_ICMPGE: return IF_ICMPLT;
            case IF_ICMPGT: return IF_ICMPLE;
            case IF_ICMPLE: return IF_ICMPGT;
            case IF_ACMPEQ: return IF_ACMPNE;
            case IF_ACMPNE: return IF_ACMPEQ;
            
            default:
                throw new IllegalArgumentException("Can't negate " + Integer.toHexString(op));
        }
    }


}
