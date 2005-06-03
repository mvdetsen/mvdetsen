package org.ibex.classgen;
public class Code {

    /**
     *  This should actually be called "ValueProducer", but that
     *  requires more typing.
     *
     *  A Value is anything that generates an item on the stack.
     *  Every instruction that causes something new to appear on the
     *  stack implements Value.  Every instruction that uses
     *  something(s) on the stack will hold a reference to the thing
     *  (almost always an instruction) that generates the thing(s) it
     *  removes from the stack.
     */
    public interface Value {
        // commented out so that the rest of this file compiles
        // Instruction[] getUsers();
    }

    /**
     *  A "nondeterministic box" -- for example when the first
     *  instruction in a loop reads from a local which could have been
     *  written to either by some instruction at the end of the
     *  previous iteration of the loop or by some instruction before
     *  the loop (on the first iteration).
     */
    public class Phi implements Value {
        public Phi(Value[] inputs) { }
        public Instruction[] getUsers() { return null; /* FIXME */ }
    }

    /** any instruction which does not -- by itself -- remove anything from the stack */
    public class Instruction { }

    /** any instruction that consumes one item from the stack */
    public class OneOp           extends Instruction { Value in;  }

    /** any instruction that consumes two items from the stack */
    public class TwoOp           extends Instruction { Value in1, in2; }
    
    /** a sequence of instructions */
    public class Sequence        extends Instruction { Instruction[] code; }
    public class Monitor         extends Sequence    { Value objectToSynchronizeOn; }
    public class TryCatchFinally extends Sequence    { OneOp[] catchBodies;  Instruction finallyBody; }

    public class Goto            extends Instruction                  { Instruction target; }
    public class JSR             extends Instruction implements Value { Instruction target; }
    public class Ret             extends OneOp                        { }
    public class Nop             extends Instruction                  { }

    public class Constant        extends Instruction implements Value { /** ??? **/ }
    public class LDC             extends Instruction implements Value { Type.Class klass; }
    public class New             extends Instruction implements Value { Type.Ref   type; }
    public class ArrayLength     extends OneOp       implements Value { }
    public class ArrayIndex      extends Instruction implements Value { }
    public class Return          extends OneOp                        { }
    public class ReturnVoid      extends Instruction                  { }
    public class CheckCast       extends OneOp       implements Value { Type.Ref   castTarget; }
    public class Instanceof      extends OneOp       implements Value { Type.Class klass; }
    public class If              extends OneOp                        { Instruction thenTarget, elseTarget; }
    public class Switch          extends OneOp {
        public class Table extends Switch { }
        public class Lookup extends Switch { }
    }

    public abstract class Invoke extends Instruction {
        Value[] arguments;
        Type.Class.Method method;
        public class Static  extends Invoke implements Value { }
        public class Special extends Invoke implements Value { }
        public class Virtual extends Invoke implements Value { Value instance; }
    }

}
