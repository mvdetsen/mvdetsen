package org.ibex.classgen;

import java.util.*;
import java.io.*;

/** Class generation object representing the whole classfile */
public class ClassFile extends Type.Class.Body {
    private final Type.Class thisType;
    final Type.Class superType;
    final Type.Class[] interfaces;
    private final short minor;
    private final short major;
    
    private final Vector fields = new Vector();
    private final Vector methods = new Vector();

    public Type.Class.Method.Body[] methods() {
        Type.Class.Method.Body[] ret = new Type.Class.Method.Body[methods.size()];
        methods.copyInto(ret);
        return ret;
    }

    public Type.Class.Field.Body[] fields() {
        Type.Class.Field.Body[] ret = new Type.Class.Field.Body[fields.size()];
        fields.copyInto(ret);
        return ret;
    }
    
    static String flagsToString(int flags, boolean isClass) {
        StringBuffer sb = new StringBuffer(32);
        if ((flags & PUBLIC) != 0)       sb.append("public ");
        if ((flags & PRIVATE) != 0)      sb.append("private ");
        if ((flags & PROTECTED) != 0)    sb.append("protected ");
        if ((flags & STATIC) != 0)       sb.append("static ");
        if ((flags & FINAL) != 0)        sb.append("final ");
        if ((flags & ABSTRACT) != 0)     sb.append("abstract ");
        if (!isClass && (flags & SYNCHRONIZED) != 0) sb.append("synchronized ");
        if (!isClass && (flags & NATIVE) != 0)       sb.append("native ");
        if (!isClass && (flags & STRICT) != 0)       sb.append("strictfp ");
        if (!isClass && (flags & VOLATILE) != 0)     sb.append("volatile ");
        if (!isClass && (flags & TRANSIENT) != 0)    sb.append("transient ");
        return sb.toString();
    }

    public Type.Class getType() { return thisType; }
    public int getFlags() { return flags; }
    
    public String toString() { return toString(new StringBuffer(4096)).toString(); }
    StringBuffer toString(StringBuffer sb) {
        sb.append(flagsToString(flags,true));
        sb.append((flags & INTERFACE) != 0 ? "interface " : "class ");
        sb.append(thisType.toString());
        if (superType != null) sb.append(" extends " + superType.toString());
        if (interfaces != null && interfaces.length > 0) sb.append(" implements");
        for(int i=0; i<interfaces.length; i++) sb.append((i==0?" ":", ")+interfaces[i].toString());
        sb.append(" {");
        sb.append(" // v"+major+"."+minor);
        ConstantPool.Utf8Key sourceFile = (ConstantPool.Utf8Key) attrs.get("SourceFile");
        if (sourceFile != null) sb.append(" from " + sourceFile.s);
        sb.append("\n");
        for(int i=0; i<fields.size(); i++) {
            sb.append("  ");
            ((FieldGen)fields.elementAt(i)).toString(sb);
            sb.append("\n");
        }
        for(int i=0; i<methods.size(); i++) {
            ((MethodGen)methods.elementAt(i)).toString(sb,thisType.getShortName());
            sb.append("\n");
        }
        sb.append("}");
        return sb;
    }

    public ClassFile(Type.Class thisType, Type.Class superType, int flags) { this(thisType, superType, flags, null); }
    public ClassFile(Type.Class thisType, Type.Class superType, int flags, Type.Class[] interfaces) {
        thisType.super(flags, new AttrGen());
        this.thisType = thisType;
        this.superType = superType;
        this.interfaces = interfaces;
        this.minor = 3;
        this.major = 45;        
    }
    
    /** Adds a new method to this class 
        @param name The name of the method (not the signature, just the name)
        @param ret The return type of the method
        @param args The arguments to the method
        @param flags The flags for the method
                     (PUBLIC, PRIVATE, PROTECTED, STATIC, SYNCHRONIZED, NATIVE, ABSTRACT, STRICT)
        @return A new MethodGen object for the method
        @exception IllegalArgumentException if illegal flags are specified
        @see MethodGen
        @see CGConst
    */
    public final MethodGen addMethod(String name, Type ret, Type[] args, int flags) {
        return addMethod(getType().method(name, ret, args),flags);
    }
    public final MethodGen addMethod(Type.Class.Method method,int flags) {
        MethodGen mg = new MethodGen(method, flags);
        methods.addElement(mg);
        return mg;
    }
    
    /** Adds a new field to this class 
        @param name The name of the filed (not the signature, just the name)
        @param type The type of the field
        @param flags The flags for the field
        (PUBLIC, PRIVATE, PROTECTED, STATIC, FINAL, VOLATILE, TRANSIENT)
        @return A new FieldGen object for the method
        @exception IllegalArgumentException if illegal flags are specified
        @see FieldGen
        @see CGConst
        */  
    public final FieldGen addField(String name, Type type, int flags) {
        return addField(getType().field(name,type),flags);
    }
    public final FieldGen addField(Type.Class.Field field, int flags) {
        FieldGen fg = new FieldGen(field , flags);
        fields.addElement(fg);
        return fg;
    }
    
    /** Sets the source value of the SourceFile attribute of this class 
        @param sourceFile The string to be uses as the SourceFile of this class
    */
    public void setSourceFile(String sourceFile) { attrs.put("SourceFile", new ConstantPool.Utf8Key(sourceFile)); }
    
    /** Writes the classfile data to the file specifed
        @see ClassFile#dump(OutputStream)
    */
    public void dump(String file) throws IOException { dump(new File(file)); }
    
    /** Writes the classfile data to the file specified
        If <i>f</i> is a directory directory components under it are created for the package the class is in (like javac's -d option)
        @see ClassFile#dump(OutputStream)
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
        ConstantPool cp = new ConstantPool();
        cp.add(thisType);
        cp.add(superType);
        if(interfaces != null) for(int i=0;i<interfaces.length;i++) cp.add(interfaces[i]);
        for(int i=0;i<methods.size();i++) ((MethodGen)methods.elementAt(i)).finish(cp);
        for(int i=0;i<fields.size();i++) ((FieldGen)fields.elementAt(i)).finish(cp);
        attrs.finish(cp);
        
        cp.optimize();
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
        for(int i=0;i<fields.size();i++) ((FieldGen)fields.elementAt(i)).dump(o,cp); // fields

        o.writeShort(methods.size()); // methods_count
        for(int i=0;i<methods.size();i++) ((MethodGen)methods.elementAt(i)).dump(o,cp); // methods
        
        attrs.dump(o,cp); // attributes        
    }
    
    public static ClassFile read(String s) throws IOException { return read(new File(s)); }
    public static ClassFile read(File f) throws ClassReadExn, IOException {
        InputStream is = new FileInputStream(f);
        ClassFile ret = read(is);
        is.close();
        return ret;
    }
    
    public static ClassFile read(InputStream is) throws ClassReadExn, IOException {
        try {
            return new ClassFile(new DataInputStream(new BufferedInputStream(is)));
        } catch(RuntimeException e) {
            e.printStackTrace();
            throw new ClassReadExn("invalid constant pool entry");
        }
    }

    public ClassFile(DataInput i) throws IOException { this(i, false); }
    public ClassFile(DataInput i, boolean ssa) throws IOException {
        this(i.readInt(), i.readShort(), i.readShort(), new ConstantPool(i), i.readShort(), i, ssa);
    }
    private ClassFile(int magic, short minor, short major, ConstantPool cp,
                      short flags, DataInput i, boolean ssa) throws IOException {
        this(magic, minor, major, cp, flags, (Type.Class)cp.getKeyByIndex(i.readShort()), i, ssa);
    }
    private ClassFile(int magic, short minor, short major, ConstantPool cp, short flags,
                      Type.Class thisType, DataInput i, boolean ssa) throws IOException {
        thisType.super(flags, null);
        if (magic != 0xcafebabe) throw new ClassReadExn("invalid magic: " + Long.toString(0xffffffffL & magic, 16));
        this.minor = minor;
        this.major = major;
        this.thisType = thisType;
        superType = (Type.Class) cp.getKeyByIndex(i.readShort());
        interfaces = new Type.Class[i.readShort()];
        for(int j=0; j<interfaces.length; j++) interfaces[j] = (Type.Class) cp.getKeyByIndex(i.readShort());
        int numFields = i.readShort();
        for(int j=0; j<numFields; j++) fields.addElement(new FieldGen(this.getType(), i, cp));
        int numMethods = i.readShort();
        for(int j=0; j<numMethods; j++) methods.addElement(ssa 
                                                           ? new JSSA(this.getType(), i, cp) 
                                                           : new MethodGen(this.getType(), i, cp));
        readAttributes(i, cp);
        
        // FEATURE: Support these
        // NOTE: Until we can support them properly we HAVE to delete them,
        //       they'll be incorrect after we rewrite the constant pool, etc
        attrs.remove("InnerClasses");
    }
    
    /** Thrown when class generation fails for a reason not under the control of the user
        (IllegalStateExceptions are thrown in those cases */
    // FEATURE: This should probably be a checked exception
    public static class Exn extends RuntimeException {
        public Exn(String s) { super(s); }
    }
    
    public static class ClassReadExn extends IOException {
        public ClassReadExn(String s) { super(s); }
    }
    
    static class AttrGen {
        private final Hashtable ht = new Hashtable();
        
        AttrGen() { }
        AttrGen(DataInput in, ConstantPool cp) throws IOException {
            int size = in.readShort();
            for(int i=0; i<size; i++) {
                String name = cp.getUtf8KeyByIndex(in.readUnsignedShort());
                int length = in.readInt();
                if ((name.equals("SourceFile")||name.equals("ConstantValue")) && length == 2) {
                    ht.put(name, cp.getKeyByIndex(in.readUnsignedShort()));
                } else {
                    byte[] buf = new byte[length];
                    in.readFully(buf);
                    ht.put(name, buf);
                }
            }
        }

        public Object get(String s) { return ht.get(s); }
        public void put(String s, Object data) { ht.put(s, data); }
        public boolean contains(String s) { return ht.get(s) != null; }
        public void remove(String s) { ht.remove(s); }
        public int size() { return ht.size(); }
        
        void finish(ConstantPool cp) {
            for(Enumeration e = ht.keys(); e.hasMoreElements();) {
                String name = (String) e.nextElement();
                Object val = ht.get(name);
                cp.addUtf8(name);
                if(!(val instanceof byte[])) cp.add(val);
            }
        }
        
        void dump(DataOutput o, ConstantPool cp) throws IOException {
            o.writeShort(size());
            for(Enumeration e = ht.keys(); e.hasMoreElements();) {
                String name = (String) e.nextElement();
                Object val = ht.get(name);
                o.writeShort(cp.getUtf8Index(name));
                if(val instanceof byte[]) {
                    byte[] buf = (byte[]) val;
                    o.writeInt(buf.length);
                    o.write(buf);
                } else {
                    o.writeInt(2);
                    o.writeShort(cp.getIndex(val));
                }
            }
        }
    }
    
    public static void main(String[] args) throws Exception {
        if(args.length >= 2 && args[0].equals("copyto")) {
            File dest = new File(args[1]);
            dest.mkdirs();
            for(int i=2;i<args.length;i++) {
                System.err.println("Copying " + args[i]);
                read(args[i]).dump(dest);
            }
        }
        else if (args.length==1) {
            if (args[0].endsWith(".class")) {
                System.out.println(new ClassFile(new DataInputStream(new FileInputStream(args[0]))).toString());
            } else {
                InputStream is = Class.forName(args[0]).getClassLoader().getResourceAsStream(args[0].replace('.', '/')+".class");
                System.out.println(new ClassFile(new DataInputStream(is)).toString());
            }
        } else {
            /*
            Type.Class me = new Type.Class("Test");
            ClassFile cg = new ClassFile("Test", "java.lang.Object", PUBLIC|SUPER|FINAL);
            FieldGen fg = cg.addField("foo", Type.INT, PUBLIC|STATIC);
        
            MethodGen mg = cg.addMethod("main", Type.VOID, new Type[]{Type.arrayType(Type.STRING)}, STATIC|PUBLIC);
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
