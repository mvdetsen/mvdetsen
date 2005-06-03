package org.ibex.classgen;

import java.util.*;
import java.io.*;

/** Class generation object representing the whole classfile */
public class ClassGen implements CGConst {
    private final Type.Class thisType;
    private final Type.Class superType;
    private final Type.Class[] interfaces;
    private short minor;
    private short major;
    final int flags;
    
    private String sourceFile; 
    private final Vector fields = new Vector();
    private final Vector methods = new Vector();
    
    final CPGen cp;
    private final AttrGen attributes;

    public static String flagsToString(int flags) {
        String ret = "";
        if ((flags & ACC_PUBLIC) != 0)       ret += "public ";
        if ((flags & ACC_PRIVATE) != 0)      ret += "private ";
        if ((flags & ACC_PROTECTED) != 0)    ret += "protected ";
        if ((flags & ACC_STATIC) != 0)       ret += "static ";
        if ((flags & ACC_FINAL) != 0)        ret += "final ";
        if ((flags & ACC_ABSTRACT) != 0)     ret += "abstract ";
        if ((flags & ACC_SYNCHRONIZED) != 0) ret += "synchronized ";
        if ((flags & ACC_NATIVE) != 0)       ret += "native ";
        if ((flags & ACC_STRICT) != 0)       ret += "strictfp ";
        if ((flags & ACC_VOLATILE) != 0)     ret += "volatile ";
        if ((flags & ACC_TRANSIENT) != 0)    ret += "transient ";
        return ret;
    }
  
    public String toString() { StringBuffer sb = new StringBuffer(); toString(sb); return sb.toString(); }
    public void   toString(StringBuffer sb) {
        sb.append(flagsToString(flags));
        sb.append((flags & ACC_INTERFACE) != 0 ? "interface " : "class ");
        sb.append(thisType);
        if (superType != null) sb.append(" extends " + superType);
        if (interfaces != null && interfaces.length > 0) sb.append(" implements");
        for(int i=0; i<interfaces.length; i++) sb.append((i==0?" ":", ")+interfaces[i]);
        sb.append(" {");
        sb.append(" // [jcf v"+major+"."+minor+"]");
        if (sourceFile != null) sb.append(" from " + sourceFile);
        sb.append("\n");
        for(int i=0; i<fields.size(); i++) {
            sb.append("  ");
            ((FieldGen)fields.elementAt(i)).toString(sb);
            sb.append("\n");
        }
        for(int i=0; i<methods.size(); i++) {
            sb.append("  ");
            ((MethodGen)methods.elementAt(i)).toString(sb, thisType.getShortName());
            sb.append("\n");
        }
        sb.append("}");
    }

    /** @see #ClassGen(Type.Class, Type.Class, int) */
    public ClassGen(String name, String superName, int flags) {
        this(Type.instance(name).asClass(), Type.instance(superName).asClass(), flags);
    }

    /** @see #ClassGen(Type.Class, Type.Class, int, Type.Class[]) */
    public ClassGen(Type.Class thisType, Type.Class superType, int flags) {
        this(thisType, superType, flags, null);
    }
    
    /** Creates a new ClassGen object 
        @param thisType The type of the class to generate
        @param superType The superclass of the generated class (commonly Type.OBJECT) 
        @param flags The access flags for this class (ACC_PUBLIC, ACC_FINAL, ACC_SUPER, ACC_INTERFACE, and ACC_ABSTRACT)
    */
    public ClassGen(Type.Class thisType, Type.Class superType, int flags, Type.Class[] interfaces) {
        if((flags & ~(ACC_PUBLIC|ACC_FINAL|ACC_SUPER|ACC_INTERFACE|ACC_ABSTRACT)) != 0)
            throw new IllegalArgumentException("invalid flags");
        this.thisType = thisType;
        this.superType = superType;
        this.interfaces = interfaces;
        this.flags = flags;
        this.minor = 3;
        this.major = 45;
        
        cp = new CPGen();
        attributes = new AttrGen(cp);
    }
    
    /** Adds a new method to this class 
        @param name The name of the method (not the signature, just the name)
        @param ret The return type of the method
        @param args The arguments to the method
        @param flags The flags for the method
                     (ACC_PUBLIC, ACC_PRIVATE, ACC_PROTECTED, ACC_STATIC, ACC_SYNCHRONIZED, ACC_NATIVE, ACC_ABSTRACT, ACC_STRICT)
        @return A new MethodGen object for the method
        @exception IllegalArgumentException if illegal flags are specified
        @see MethodGen
        @see CGConst
    */
    public final MethodGen addMethod(String name, Type ret, Type[] args, int flags) {
        MethodGen mg = new MethodGen(this, name, ret, args, flags);
        methods.addElement(mg);
        return mg;
    }
    
    /** Adds a new field to this class 
        @param name The name of the filed (not the signature, just the name)
        @param type The type of the field
        @param flags The flags for the field
        (ACC_PUBLIC, ACC_PRIVATE, ACC_PROTECTED, ACC_STATIC, ACC_FINAL, ACC_VOLATILE, ACC_TRANSIENT)
        @return A new FieldGen object for the method
        @exception IllegalArgumentException if illegal flags are specified
        @see FieldGen
        @see CGConst
        */  
    public final FieldGen addField(String name, Type type, int flags) {
        FieldGen fg = new FieldGen(this, name, type, flags);
        fields.addElement(fg);
        return fg;
    }
    
    /** Sets the source value of the SourceFile attribute of this class 
        @param sourceFile The string to be uses as the SourceFile of this class
    */
    public void setSourceFile(String sourceFile) { this.sourceFile = sourceFile; }
    
    /** Writes the classfile data to the file specifed
        @see ClassGen#dump(OutputStream)
    */
    public void dump(String file) throws IOException { dump(new File(file)); }
    
    /** Writes the classfile data to the file specified
        If <i>f</i> is a directory directory components under it are created for the package the class is in (like javac's -d option)
        @see ClassGen#dump(OutputStream)
    */
    public void dump(File f) throws IOException {
        if(f.isDirectory()) {
            String[] a = thisType.components();
            int i;
            for(i=0;i<a.length-1;i++) {
                f = new File(f, a[i]);
                f.mkdir();
            }
            f = new File(f, a[i] + ".class");
        }
        OutputStream os = new FileOutputStream(f);
        dump(os);
        os.close();
    }
   
    /** Writes the classfile data to the outputstream specified
        @param os The stream to write the class to
        @exception IOException if an IOException occures while writing the class data
        @exception IllegalStateException if the data for a method is in an inconsistent state (required arguments missing, etc)
        @exception Exn if the classfile could not be written for any other reason (constant pool full, etc)
    */
    public void dump(OutputStream os) throws IOException {
        DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(os));
        _dump(dos);
        dos.flush();
    }
    
    private void _dump(DataOutput o) throws IOException {
        cp.optimize();
        cp.stable();
        
        cp.add(thisType);
        cp.add(superType);
        if(interfaces != null) for(int i=0;i<interfaces.length;i++) cp.add(interfaces[i]);
        if(sourceFile != null && !attributes.contains("SourceFile")) attributes.add("SourceFile", cp.addUtf8(sourceFile));
                
        for(int i=0;i<methods.size();i++) ((MethodGen)methods.elementAt(i)).finish();
        for(int i=0;i<fields.size();i++) ((FieldGen)fields.elementAt(i)).finish();
        
        cp.seal();
        
        o.writeInt(0xcafebabe); // magic
        o.writeShort(minor); // minor_version
        o.writeShort(major); // major_version
        
        cp.dump(o); // constant_pool
        
        o.writeShort(flags);
        o.writeShort(cp.getIndex(thisType)); // this_class
        o.writeShort(cp.getIndex(superType)); // super_class
        
        o.writeShort(interfaces==null ? 0 : interfaces.length); // interfaces_count
        if(interfaces != null) for(int i=0;i<interfaces.length;i++) o.writeShort(cp.getIndex(interfaces[i])); // interfaces
        
        o.writeShort(fields.size()); // fields_count
        for(int i=0;i<fields.size();i++) ((FieldGen)fields.elementAt(i)).dump(o); // fields

        o.writeShort(methods.size()); // methods_count
        for(int i=0;i<methods.size();i++) ((MethodGen)methods.elementAt(i)).dump(o); // methods
        
        o.writeShort(attributes.size()); // attributes_count
        attributes.dump(o); // attributes        
    }
    
    public ClassGen read(File f) throws ClassReadExn, IOException {
        InputStream is = new FileInputStream(f);
        ClassGen ret = read(is);
        is.close();
        return ret;
    }
    
    public ClassGen read(InputStream is) throws ClassReadExn, IOException {
        return new ClassGen(new DataInputStream(new BufferedInputStream(is)));
    }

    ClassGen(DataInput i) throws ClassReadExn, IOException {
        int magic = i.readInt();
        if (magic != 0xcafebabe) throw new ClassReadExn("invalid magic: " + Long.toString(0xffffffffL & magic, 16));
        minor = i.readShort();
        //if (minor != 3) throw new ClassReadExn("invalid minor version: " + minor);
        major = i.readShort();
        //if (major != 45 && major != 46) throw new ClassReadExn("invalid major version");
        cp = new CPGen(i);
        flags = i.readShort();
        thisType = (Type.Class)cp.getType(i.readShort());
        superType = (Type.Class)cp.getType(i.readShort());
        interfaces = new Type.Class[i.readShort()];
        for(int j=0; j<interfaces.length; j++) interfaces[j] = (Type.Class)cp.getType(i.readShort());
        int numFields = i.readShort();
        for(int j=0; j<numFields; j++) fields.add(new FieldGen(cp, i));
        int numMethods = i.readShort();
        for(int j=0; j<numMethods; j++) methods.add(new MethodGen(cp, i));
        attributes = new AttrGen(cp, i);
        sourceFile = (String)attributes.get("SourceFile");
    }
    
    /** Thrown when class generation fails for a reason not under the control of the user
        (IllegalStateExceptions are thrown in those cases */
    public static class Exn extends RuntimeException {
        public Exn(String s) { super(s); }
    }
    
    public static class ClassReadExn extends IOException {
        public ClassReadExn(String s) { super(s); }
    }
    
    /** A class representing a field or method reference. This is used as an argument to the INVOKE*, GET*, and PUT* bytecodes
        @see MethodRef
        @see FieldRef
        @see MethodRef.I
        @see FieldRef
    */
    public static abstract class FieldOrMethodRef {
        Type.Class klass;
        String name;
        String descriptor;
        
        FieldOrMethodRef(Type.Class klass, String name, String descriptor) { this.klass = klass; this.name = name; this.descriptor = descriptor; }
        FieldOrMethodRef(FieldOrMethodRef o) { this.klass = o.klass; this.name = o.name; this.descriptor = o.descriptor; }
        public boolean equals(Object o_) {
            if(!(o_ instanceof FieldOrMethodRef)) return false;
            FieldOrMethodRef o = (FieldOrMethodRef) o_;
            return o.klass.equals(klass) && o.name.equals(name) && o.descriptor.equals(descriptor);
        }
        public int hashCode() { return klass.hashCode() ^ name.hashCode() ^ descriptor.hashCode(); }
    }
    
    static class AttrGen {
        private final CPGen cp;
        private final Hashtable ht = new Hashtable();
        
        public AttrGen(CPGen cp) { this.cp = cp; }
        public AttrGen(CPGen cp, DataInput in) throws IOException {
            this(cp);
            int size = in.readShort();
            for(int i=0; i<size; i++) {
                String name = null;
                int idx = in.readShort();
                CPGen.Ent e = cp.getByIndex(idx);
                Object key = e.key();
                if (key instanceof String) name = (String)key;
                else name = ((Type)key).getDescriptor();

                int length = in.readInt();
                if (length==2) {   // FIXME might be wrong assumption
                    ht.put(name, cp.getByIndex(in.readShort()));
                } else {
                    byte[] buf = new byte[length];
                    in.readFully(buf);
                    ht.put(name, buf);
                }
            }
        }

        public Object get(String s) {
            Object ret = ht.get(s);
            if (ret instanceof CPGen.Utf8Ent) return ((CPGen.Utf8Ent)ret).s;
            return ret;
        }
        
        public void add(String s, Object data) {
            cp.addUtf8(s);
            ht.put(s, data);
        }
        
        public boolean contains(String s) { return ht.get(s) != null; }
        
        public int size() { return ht.size(); }
        
        public void dump(DataOutput o) throws IOException {
            for(Enumeration e = ht.keys(); e.hasMoreElements();) {
                String name = (String) e.nextElement();
                Object val = ht.get(name);
                o.writeShort(cp.getUtf8Index(name));
                if(val instanceof byte[]) {
                    byte[] buf = (byte[]) val;
                    o.writeInt(buf.length);
                    o.write(buf);
                } else if(val instanceof CPGen.Ent) {
                    o.writeInt(2);
                    o.writeShort(cp.getIndex((CPGen.Ent)val));
                } else {
                    throw new Error("should never happen");
                }
            }
        }
    }
    
    public static void main(String[] args) throws Exception {
        if (args.length==1) {
            if (args[0].endsWith(".class")) {
                System.out.println(new ClassGen(new DataInputStream(new FileInputStream(args[0]))));
            } else {
                InputStream is = Class.forName(args[0]).getClassLoader().getResourceAsStream(args[0].replace('.', '/')+".class");
                System.out.println(new ClassGen(new DataInputStream(is)));
            }
        } else {
            /*
            Type.Class me = new Type.Class("Test");
            ClassGen cg = new ClassGen("Test", "java.lang.Object", ACC_PUBLIC|ACC_SUPER|ACC_FINAL);
            FieldGen fg = cg.addField("foo", Type.INT, ACC_PUBLIC|ACC_STATIC);
        
            MethodGen mg = cg.addMethod("main", Type.VOID, new Type[]{Type.arrayType(Type.STRING)}, ACC_STATIC|ACC_PUBLIC);
            mg.setMaxLocals(1);
            mg.addPushConst(0);
            //mg.add(ISTORE_0);
            mg.add(PUTSTATIC, fieldRef(me, "foo", Type.INT));
            int top = mg.size();
            mg.add(GETSTATIC, cg.fieldRef(new Type.Class("java.lang.System"), "out", new Type.Class("java.io.PrintStream")));
            //mg.add(ILOAD_0);
            mg.add(GETSTATIC, cg.fieldRef(me, "foo", Type.INT));
            mg.add(INVOKEVIRTUAL, cg.methodRef(new Type.Class("java.io.PrintStream"), "println",
                                               Type.VOID, new Type[]{Type.INT}));
            //mg.add(IINC, new int[]{0, 1});
            //mg.add(ILOAD_0);
            mg.add(GETSTATIC, cg.fieldRef(me, "foo", Type.INT));
            mg.addPushConst(1);
            mg.add(IADD);
            mg.add(DUP);
            mg.add(PUTSTATIC, cg.fieldRef(me, "foo", Type.INT));       
            mg.addPushConst(10);
            mg.add(IF_ICMPLT, top);
            mg.add(RETURN);
            cg.dump("Test.class");
            */
        }
    }
}
