package org.ibex.classgen;

/**
 *  a highly streamlined SSA-form intermediate representation of a
 *  sequence of JVM instructions; all stack manipulation is factored
 *  out.
 */
public class JSSA {

    // Constructor //////////////////////////////////////////////////////////////////////////////
    
    public JSSA(MethodGen mg) {
        Expr[] reg = new Expr[5];
        for(int i=0; i<mg.size(); i++) {
            int    op  = mg.get(i);
            Object arg = mg.getArg(i);
            addOp(mg, op, arg);
        }
    }

    // Instance Data; used ONLY during constructor; then thrown away /////////////////////////////////////////////////

    /** this models the JVM registers; it is only used for unwinding stack-ops into an SSA-tree, then thrown away */
    private Expr[] reg = new Expr[5];
    
    /** this models the JVM stack; it is only used for unwinding stack-ops into an SSA-tree, then thrown away */
    private Expr[] stack = new Expr[65535];

    /** JVM stack pointer */
    private int sp = 0;
    
    private Expr push(Expr e) { return stack[sp++] = e; }
    private Expr pop()        { return stack[--sp]; }


    // SSA-node classes /////////////////////////////////////////////////////////////////////////////////////////

    /** an purely imperative operation which does not generate data */
    public abstract class Op {
        //public abstract Op[] predecessors();  // not implemented yet
        //public abstract Op[] successors();    // not implemented yet
    }

    /** an operation which generates data */
    public abstract class Expr extends Op {
        //public abstract Expr[] contributors();  // not implemented yet
        //public abstract Expr[] dependents();    // not implemented yet

        /** every JSSA.Expr either remembers its type _OR_ knows how to figure it out (the latter is preferred to eliminate
         *  redundant information that could possibly "disagree" with itself -- this happened a LOT in Soot) */
        public abstract Type getType();
    }

    /**
     *  A "nondeterministic merge" -- for example when the first instruction in a loop reads from a local which could have been
     *  written to either by some instruction at the end of the previous iteration of the loop or by some instruction before
     *  the loop (on the first iteration).
     */
    public class Phi extends Expr {
        private final Expr[] inputs;
        public Phi(Expr[] inputs) {
            this.inputs = new Expr[inputs.length];
            System.arraycopy(inputs, 0, this.inputs, 0, inputs.length);
        }
        public Type getType() {
            // sanity check
            Type t = inputs[0].getType();

            // FIXME: actually this should check type-unifiability... fe, the "type of null" unifies with any Type.Ref
            for(int i=1; i<inputs.length; i++)
                if (inputs[i].getType() != t)
                    throw new Error("Phi node with disagreeing types!  Crisis!");
        }
    }


public class Cast extends Expr {
    final Expr e;
    final Type t;
    public Cast(Expr e, Type t) { this.e = e; this.t = t; }
    public Type getType() { return t; }
}

public class InstanceOf extends Expr {
    final Expr e;
    final Type t;
    public InstanceOf(Expr e, Type t) { this.e = e; this.t = t; }
    public Type getType() { return Type.BOOLEAN; }
}

public class Branch extends Op {
    public class Goto extends Branch { }
    public class GoSub extends Branch { }
    public class Ret extends Branch { }
    public class If extends Branch { }
}

/** represents a "returnaddr" pushed onto the stack */
public class Label extends Expr {
    public final Op op;
    public Type getType() { throw new Error("attempted to call getType() on a Label"); }
    public Label(Op op) { this.op = op; }
}

public class Allocate extends Expr {
    public final Type t;
    public Type getType() { return t; }
    public Allocate(Type t) { this.t = t; }
}

public class Return extends Op {
    final Expr e;
    public Return() { this(null); }
    public Return(Expr e) { this.e = e; }
}

/** GETFIELD and GETSTATIC */
public class Get extends Expr {
    final Type.Class.Field f;
    final Expr e;
    public Type getType() { return f.getType(); }
    public Get(Field f) { this(f, null); }
    public Get(Field f, Expr e) { this.f = f; this.e = e; }
}

/** PUTFIELD and PUTSTATIC */
public class Put extends Op {
    final Type.Class.Field f;
    final Expr v;
    final Expr e;
    public Put(Field f, Expr v) { this(f, v, null); }
    public Put(Field f, Expr v, Expr e) { this.f = f; this.v = v; this.e = e; }
}

public class ArrayPut extends Op {
    final Expr e, i, v;
    public ArrayPut(Expr e, Expr i, Expr v) { this.e = e; this.i = i; this.v = v; }
}

public class ArrayGet extends Expr {
    final Expr e, i;
    public ArrayGet(Expr e, Expr i) { this.e = e; this.i = i; this.v = v; }
    public Type getType() { return e.getType().asArray().elementType(); }
}

public class ArrayLength extends Expr {
    final Expr e;
    public ArrayLength(Expr e) { this.e = e; }
    public Type getType() { return Type.INTEGER; }
}

public abstract class Invoke extends Op {
    public final Expr[] arguments;
    public final Type.Class.Method method;
    protected Invoke(Type.Class.Method m, Expr[] a) { this.arguments = a; this.method m; } 

    public Type getType() { return method.getReturnType(); }

    public class Static    extends Invoke { public Static(Type.Class.Method m, Expr[] a) { super(m,a); } }
    public class Special   extends Virtual { public Special(Type.Class.Method m, Expr[] a, Expr e) { super(m,a,e); } }
    public class Interface extends Virtual { public Virtual(Type.Class.Method m, Expr[] a, Expr e) { super(m,a,e); } }
    public class Virtual   extends Invoke {
        public final Expr instance;
        public Virtual(Type.Class.Method m, Expr[] a, Expr e) { super(m, a); instance = e; }
    }
}

public static class Constant extends Expr {
    private final Object o;
    public Constant(Object o) { this.o = o; }
    public Type getType() {
        if (o instanceof Byte) return Type.BYTE;
        if (o instanceof Short) return Type.SHORT;
        if (o instanceof Char) return Type.CHAR;
        if (o instanceof Boolean) return Type.BOOLEAN;
        if (o instanceof Long) return Type.LONG;
        if (o instanceof Double) return Type.DOUBLE;
        if (o instanceof Float) return Type.FLOAT;
        if (o instanceof ConstantPool.Ent) throw new Error("unimplemented");
        throw new Error("this should not happen");
    }
}

    // Implementation //////////////////////////////////////////////////////////////////////////////

    private void addOp(MethodGen mg, int op, Object arg) {
        Number number = null;
        int i1 = 0;
        int i2 = 0;
        if (op==WIDE) {
            Wide w = (Wide)arg;
            op = w.op;
            arg = null;
            i1 = w.varNum;
            i2 = w.n;
        }
        if (op==IINC) {
            Pair p = (Pair)arg;
            arg = null;
            i1 = p.i1;
            i2 = p.i2;
        }
        if (arg != null && arg instanceof Number) number = (Number)arg;
        switch(op) {

            case NOP: return null;

                // Stack manipulations //////////////////////////////////////////////////////////////////////////////

            case ACONST_NULL:                                                      return stack[sp++] = new Constant(null);
            case ICONST_M1:                                                        return stack[sp++] = new Constant(-1);
            case ICONST_0: case LCONST_0: case FCONST_0: case DCONST_0:            return reg[0] = new Constant(i1);
            case ICONST_1: case LCONST_1: case FCONST_1: case DCONST_1:            return reg[1] = new Constant(i1);
            case ICONST_2: case FCONST_2:                                          return reg[2] = new Constant(i1);
            case ICONST_3:                                                         return reg[3] = new Constant(i1);
            case ICONST_4:                                                         return reg[4] = new Constant(i1);
            case ICONST_5:                                                         return reg[5] = new Constant(i1);
            case ILOAD:    case LLOAD:    case FLOAD:    case DLOAD:    case ALOAD:    return stack[sp++] = reg[i1];
            case ILOAD_0:  case LLOAD_0:  case FLOAD_0:  case DLOAD_0:  case ALOAD_0:  return stack[sp++] = reg[0]; 
            case ILOAD_1:  case LLOAD_1:  case FLOAD_1:  case DLOAD_1:  case ALOAD_1:  return stack[sp++] = reg[1]; 
            case ALOAD_2:  case DLOAD_2:  case FLOAD_2:  case LLOAD_2:  case ILOAD_2:  return stack[sp++] = reg[2]; 
            case ILOAD_3:  case LLOAD_3:  case FLOAD_3:  case DLOAD_3:  case ALOAD_3:  return stack[sp++] = reg[3]; 
            case ISTORE:   case LSTORE:   case FSTORE:   case DSTORE:   case ASTORE:   return reg[i1] = stack[sp++];
            case ISTORE_0: case LSTORE_0: case FSTORE_0: case DSTORE_0: case ASTORE_0: return reg[0] = stack[sp++]; 
            case ISTORE_1: case LSTORE_1: case FSTORE_1: case DSTORE_1: case ASTORE_1: return reg[1] = stack[sp++]; 
            case ASTORE_2: case DSTORE_2: case FSTORE_2: case LSTORE_2: case ISTORE_2: return reg[2] = stack[sp++]; 
            case ISTORE_3: case LSTORE_3: case FSTORE_3: case DSTORE_3: case ASTORE_3: return reg[3] = stack[sp++]; 
            case POP:      stack[--sp] = null;                    
            case POP2:     stack[--sp] = null; stack[--sp] = null;   /** fixme: pops a WORD, not an item */
            case DUP:      stack[sp] = stack[sp-1]; sp++;
            case DUP2:     stack[sp] = stack[sp-2]; stack[sp+1] = stack[sp-1]; sp+=2;

                // Conversions //////////////////////////////////////////////////////////////////////////////

                // coercions are added as-needed when converting from JSSA back to bytecode, so we can
                // simply discard them here (assuming the bytecode we're reading in was valid in the first place)

            case I2L: case F2L: case D2L:               return push(new Cast(pop(), Type.LONG));
            case I2F: case L2F: case D2F:               return push(new Cast(pop(), Type.FLOAT));
            case I2D: case L2D: case F2D:               return push(new Cast(pop(), Type.DOUBLE));
            case L2I: case F2I: case D2I:               return push(new Cast(pop(), Type.INT));
            case I2B:                                   return push(new Cast(pop(), Type.BYTE));
            case I2C:                                   return push(new Cast(pop(), Type.CHAR));
            case I2S:                                   return push(new Cast(pop(), Type.SHORT));
            case SWAP:                                  { Expr e1 = pop(), e2 = pop(); return push(e2); return push(e1); }

                // Math //////////////////////////////////////////////////////////////////////////////
                   
            case IADD: case LADD: case FADD: case DADD: return push(new Add(pop(), pop()));
            case ISUB: case LSUB: case FSUB: case DSUB: return push(new Sub(pop(), pop()));
            case IMUL: case LMUL: case FMUL: case DMUL: return push(new Mul(pop(), pop()));
            case IREM: case LREM: case FREM: case DREM: return push(new Rem(pop(), pop()));
            case INEG: case LNEG: case FNEG: case DNEG: return push(new Neg(pop(), pop()));
            case IDIV: case LDIV: case FDIV: case DDIV: return push(new Div(pop(), pop()));
            case ISHL: case LSHL:                       return push(new Shl(pop(), pop()));
            case ISHR: case LSHR:                       return push(new Shr(pop(), pop()));
            case IUSHR: case LUSHR:                     return push(new Ushr(pop(), pop()));
            case IAND: case LAND:                       return push(new And(pop(), pop()));
            case IOR:  case LOR:                        return push(new Or(pop(), pop()));
            case IXOR: case LXOR:                       return push(new Xor(pop(), pop()));
            case IINC:                                  return reg[i1] = new Add(reg[i1], new Constant(i2));

                // Control and branching //////////////////////////////////////////////////////////////////////////////

            case IFNULL:                                return new Branch(eq(pop(), new Constant(null)), new Label(arg));
            case IFNONNULL:                             return new Branch(not(eq(pop(), new Constant(null))), new Label(arg));
            case IFEQ:                                  return new Branch(    eq(new Constant(0), pop()),  arg);
            case IFNE:                                  return new Branch(not(eq(new Constant(0), pop())), arg);
            case IFLT:                                  return new Branch(    lt(new Constant(0), pop()),  arg);
            case IFGE:                                  return new Branch(not(lt(new Constant(0), pop())), arg);
            case IFGT:                                  return new Branch(    gt(new Constant(0), pop()),  arg);
            case IFLE:                                  return new Branch(not(gt(new Constant(0), pop())), arg);
            case IF_ICMPEQ:                             return new Branch(    eq(pop(), pop()),  arg);
            case IF_ICMPNE:                             return new Branch(not(eq(pop(), pop())), arg);
            case IF_ICMPLT:                             return new Branch(    lt(pop(), pop()),  arg);
            case IF_ICMPGE:                             return new Branch(not(lt(pop(), pop())), arg);
            case IF_ICMPGT:                             return new Branch(    gt(pop(), pop()),  arg);
            case IF_ICMPLE:                             return new Branch(not(gt(pop(), pop())), arg);
            case IF_ACMPEQ:                             return new Branch(    eq(pop(), pop()),  arg);
            case IF_ACMPNE:                             return new Branch(not(eq(pop(), pop())), arg);
            case ATHROW:                                return new Throw(pop());
            case GOTO:                                  return new Branch(new Label(arg));
            case JSR:                                   return new Branch.JSR(new Label(arg));
            case RET:                                   return new Branch.RET();
            case RETURN:                                return new Return();
            case IRETURN: case LRETURN: case FRETURN: case DRETURN: case ARETURN:
                return new Return(pop());

                // Array manipulations //////////////////////////////////////////////////////////////////////////////

            case IALOAD:  case LALOAD:  case FALOAD:  case DALOAD:  case AALOAD:
            case BALOAD:  case CALOAD:  case SALOAD:                                  return push(new ArrayGet(pop(), pop()));
            case IASTORE: case LASTORE: case FASTORE: case DASTORE: case AASTORE:
            case BASTORE: case CASTORE: case SASTORE:                                 return new ArrayPut(pop(), pop(), pop());

                // Invocation //////////////////////////////////////////////////////////////////////////////

            case INVOKEVIRTUAL: case INVOKESPECIAL: case INVOKESTATIC: case INVOKEINTERFACE: {
                Type.Class.Method method = (Type.Class.Method)arg;
                Expr args[] = new Expr[method.getNumArgs()];
                for(int i=0; i<args.length; i++) args[args.length-i] = pop();
                switch(op) {
                    case INVOKEVIRTUAL:   return push(new Invoke.Virtual(method, args), pop());  
                    case INVOKEINTERFACE: return push(new Invoke.Interface(method, args), pop());
                    case INVOKESPECIAL:   return push(new Invoke.Special(method, args), pop());  
                    case INVOKESTATIC:    return push(new Invoke.Static(method, args));          
                }
            }

                // Field Access //////////////////////////////////////////////////////////////////////////////

            case GETSTATIC:         return push(new Get((Type.Class.Field)arg, null));
            case PUTSTATIC:         new Put((Type.Class.Field)arg, pop(), null);
            case GETFIELD:          return push(new Get((Type.Class.Field)arg, pop()));
            case PUTFIELD:          new Put((Type.Class.Field)arg, pop(), pop());

                // Allocation //////////////////////////////////////////////////////////////////////////////

            case NEW:
            case NEWARRAY:          return push(new Allocate((Type)arg, pop()));
            case ANEWARRAY:         return push(new Allocate(Type.OBJECT.makeArray(), pop()));
            case MULTIANEWARRAY:    return push(new Allocate(Type.OBJECT.makeArray(i2), /* FIXME */));
            case ARRAYLENGTH:       return push(new ArrayLength(pop()));

                // Runtime Type information //////////////////////////////////////////////////////////////////////////////

            case CHECKCAST:         return push(new Cast(pop(), (Type)arg));
            case INSTANCEOF:        return push(new InstanceOf(pop(), (Type)arg));

            case LDC: case LDC_W: case LDC2_W: return push(new Constant(arg));

            case BIPUSH:    return push(new Constant(i1));  // FIXME
            case SIPUSH:    return push(new Constant(i1));  // FIXME

            case TABLESWITCH:    new Branch((MethodGen.Switch)arg);
            case LOOKUPSWITCH:   new Branch((MethodGen.Switch)arg);

            case MONITORENTER:   Op.monitorEnter(pop());
            case MONITOREXIT:    Op.monitorExit(pop());

            case DUP_X1:         throw new Error("unimplemented");
            case DUP_X2:         throw new Error("unimplemented");
            case DUP2_X1:         throw new Error("unimplemented");
            case DUP2_X2:         throw new Error("unimplemented");
            case LCMP:         throw new Error("unimplemented");
            case FCMPL:         throw new Error("unimplemented");
            case FCMPG:         throw new Error("unimplemented");
            case DCMPL:         throw new Error("unimplemented");
            case DCMPG:         throw new Error("unimplemented");
            case GOTO_W:         throw new Error("unimplemented");
            case JSR_W:         throw new Error("unimplemented");
            default:          throw new Error("unhandled");
        }
    }

}
