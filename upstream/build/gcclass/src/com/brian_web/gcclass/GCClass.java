// Copyright (C) 2004 Brian Alliet

// Based on NanoGoat by Adam Megac

// Copyright (C) 2004 Adam Megacz <adam@ibex.org> all rights reserved.
//
// You may modify, copy, and redistribute this code under the terms of
// the GNU Library Public License version 2.1, with the exception of
// the portion of clause 6a after the semicolon (aka the "obnoxious
// relink clause")

package com.brian_web.gcclass;

import java.util.*;
import java.io.*;

import org.apache.bcel.Constants;
import org.apache.bcel.util.*;
import org.apache.bcel.generic.*;
import org.apache.bcel.classfile.*;

// FEATURE: Rebuild each method with a new constant pool to eliminate extra constant pool entries
// FEATURE: Optimize away INSTANCEOF if the class can never be instansiated

public class GCClass {
    private static final String[] PRE_REF = {
        "java.lang.Thread.run",
        "java.security.PrivilegedAction.run"
    };
    
    // NOTE: This doesn't mean these classes are ignored alltogether
    //       failures to resolve them are just ignored
    private static final String[] IGNORED_METHODS = {
        "java.net.SocketImpl.setOption(ILjava/lang/Object;)V",
        "java.net.SocketImpl.getOption(I)Ljava/lang/Object;",
        "java.awt.geom.*",
        "apple.awt.*",
        "java.security.*"
    };
    
    private static final String[] NO_OUTPUT = { "java", "javax", "sun", "com.sun", "apple", "com.apple" };
    
    
    public static void main(String[] args) throws Exception {
        if(args.length < 3) {
            System.err.println("Usage GCClass  classpath outdir entrypoint1 ... [ entrypoint n]");
            System.exit(1);
        }
        GCClass gc = new GCClass(args[0]);
        for(int i=2;i<args.length;i++) gc.referenceMethod(args[i]);
        gc.go();
        gc.dump(new File(args[1]));
    }
    
    private final Repository repo;
    private final Vector work = new Vector();
    private final Hashtable completed = new Hashtable();
    private final Hashtable references = new Hashtable();
    
    public GCClass(String classpath) throws ClassNotFoundException {
        System.err.println(ClassPath.SYSTEM_CLASS_PATH + File.pathSeparator + classpath);
        repo = SyntheticRepository.getInstance(new ClassPath(ClassPath.SYSTEM_CLASS_PATH + File.pathSeparator + classpath));
        for(int i=0;i<PRE_REF.length;i++) referenceMethod(PRE_REF[i]);
    }
    
    private Hashtable classRefHash(JavaClass c) { return classRefHash(new ObjectType(c.getClassName())); }
    private Hashtable classRefHash(ObjectType c) {
        Hashtable h = (Hashtable) references.get(c);
        if(h == null) references.put(c,h=new Hashtable());
        return h;
    }
    
    public final ObjectType referenceClass(JavaClass c) { return referenceClass(c.getClassName()); }
    public final ObjectType referenceClass(String s) { return referenceClass(new ObjectType(s)); }
    public final ObjectType referenceClass(Type t) {
        if(t instanceof ObjectType) return referenceClass((ObjectType)t);
        return null;
    }
    
    public final ObjectType referenceClass(ObjectType t) {
        classRefHash(t);
        return t;
    }
    
    public final void referenceMethod(String s) throws ClassNotFoundException {
        int p = s.lastIndexOf('.');
        if(p == -1) throw new IllegalArgumentException("invalid class/method string");
        String cs = s.substring(0,p);
        String ms = s.substring(p+1);
        
        JavaClass c = repoGet(cs);
        Method[] methods = c.getMethods();
        for(int i=0;i<methods.length;i++)
            if(methods[i].getName().equals(ms))
                referenceMethod(new MethodRef(c,methods[i]));
    }
        
    public final void referenceMethod(MethodRef m) {
        if(completed.get(m) != null) return;
        
        if(m.c.getClassName().startsWith("[")) {
            completed.put(m,Boolean.TRUE);
            return;
        }
        
        Hashtable h = classRefHash(m.c);
        h.put(m,Boolean.TRUE);
        work.add(m);
        
        referenceClass(m.ret);
        for(int i=0;i<m.args.length;i++) referenceClass(m.args[i]);
    }
    
    public final void referenceField(FieldRef f) {
        Hashtable h = classRefHash(f.c);
        h.put(f,Boolean.TRUE);
        referenceClass(f.ftype);
    }
    
    private Hashtable repoCache = new Hashtable();
    public JavaClass repoGet(ObjectType t) throws ClassNotFoundException { return repoGet(t.getClassName()); }
    public JavaClass repoGet(String s) throws ClassNotFoundException {
        Object o = repoCache.get(s);
        if(o == null) repoCache.put(s,o = repo.loadClass(s));
        return (JavaClass) o;
    }
    
    public void go() throws Exn, ClassNotFoundException {
        while(work.size() != 0) {
            while(work.size() != 0) process((MethodRef) work.remove(work.size()-1));
            fixup();
        }
    }
    
    private void fixup() throws ClassNotFoundException {
        for(Enumeration e = references.keys(); e.hasMoreElements(); ) {
            ObjectType t = (ObjectType) e.nextElement();
            JavaClass c = repoGet(t);
            if(c == null) continue;
            Hashtable refs = (Hashtable) references.get(t);
            if(refs.size() != 0) {
                MethodRef clinit = new MethodRef(t,"<clinit>",Type.VOID,Type.NO_ARGS);
                if(findMethod(c,clinit) != null) referenceMethod(clinit);
            }
            Method[] methods = c.getMethods();
            JavaClass[] supers = c.getSuperClasses();
            JavaClass[] interfaces = c.getInterfaces();
            //System.err.println("Fixing up " + t);
            for(int i=0;i<supers.length;i++) referenceClass(supers[i]);
            for(int i=0;methods != null && i<methods.length;i++) {
                MethodRef mr = new MethodRef(c,methods[i]);
                if(refs.get(mr) != null) continue;
                for(int j=0;j<supers.length;j++) {
                    MethodRef smr = new MethodRef(supers[j],methods[i]);
                    Hashtable srefs = classRefHash(supers[j]);
                    if(srefs.get(smr) != null) referenceMethod(mr);
                    
                    JavaClass[] interfaces2 = supers[j].getInterfaces();
                    for(int k=0;interfaces2 != null && k<interfaces2.length;k++) {
                        MethodRef imr = new MethodRef(interfaces2[k],methods[i]);
                        Hashtable irefs = classRefHash(interfaces2[k]);
                        if(irefs.get(imr) != null) referenceMethod(mr);
                    }
                }
                for(int j=0;interfaces != null && j<interfaces.length;j++) {
                    MethodRef imr = new MethodRef(interfaces[j],methods[i]);
                    Hashtable irefs = classRefHash(interfaces[j]);
                    if(irefs.get(imr) != null) referenceMethod(mr);
                }
            }                                             
        }
    }
    
    private Hashtable cpgCache = new Hashtable();
    private void process(MethodRef mr) throws Exn, ClassNotFoundException {
        if(completed.get(mr) != null) return;
        completed.put(mr,Boolean.TRUE);
        
        //System.err.println("Processing " + mr + "...");

        JavaClass c = repoGet(mr.c.toString());
        
        if(!c.isClass() && !mr.name.equals("<clinit>")) return;
        
        Method m = findMethod(c,mr);
        if(m == null) {
            JavaClass supers[] = c.getSuperClasses();
            for(int i=0;i<supers.length;i++) {
                m = findMethod(supers[i],mr);
                if(m != null) { referenceMethod(new MethodRef(supers[i],m)); return; }
            }
            String sig = mr.toString();
            for(int i=0;i<IGNORED_METHODS.length;i++) {
                String pat = IGNORED_METHODS[i];
                if(pat.endsWith("*") ? sig.startsWith(pat.substring(0,pat.length()-1)) : sig.equals(pat)) return;
            }
            throw new Exn("Couldn't find " + sig);
        }
        
        Code code = m.getCode();
        if(code == null) return;
        
        ConstantPoolGen cpg = (ConstantPoolGen) cpgCache.get(c);
        if(cpg == null) cpgCache.put(c,cpg=new ConstantPoolGen(c.getConstantPool()));
        
        InstructionList insnList = new InstructionList(code.getCode());
        Instruction[] insns = insnList.getInstructions();
        
        for(int n=0;n<insns.length;n++) {
            Instruction i = insns[n];
            if(i instanceof ANEWARRAY || i instanceof CHECKCAST || i instanceof INSTANCEOF || i instanceof MULTIANEWARRAY || i instanceof NEW)
                referenceClass(((CPInstruction)i).getType(cpg));
            else if(i instanceof FieldInstruction) // GETFIED, GETSTATIC, PUTFIELD, PUTSTATIC
                referenceField(new FieldRef((FieldInstruction)i,cpg));
            else if(i instanceof InvokeInstruction) // INVOKESTATIC, INVOKEVIRTUAL, INVOKESPECIAL
                referenceMethod(new MethodRef((InvokeInstruction)i,cpg));
        }
    }
    
    private static Method findMethod(JavaClass c, MethodRef mr) {
        Method[] ms = c.getMethods();
        for(int i=0;i<ms.length;i++) {
            Method m = ms[i];
            if(m.getName().equals(mr.name) && m.getReturnType().equals(mr.ret) && Arrays.equals(m.getArgumentTypes(),mr.args))
               return m;
        }
        return null;
    }
    
    public void dump(File outdir) throws IOException, ClassNotFoundException {
        if(!outdir.isDirectory()) throw new IOException("" + outdir + " is not a directory");
        OUTER: for(Enumeration e = references.keys(); e.hasMoreElements(); ) {
            ObjectType t = (ObjectType) e.nextElement();
            String name =  t.getClassName();
            for(int i=0;i<NO_OUTPUT.length;i++) if(name.startsWith(NO_OUTPUT[i])) continue OUTER;
            Hashtable refs = (Hashtable) references.get(t);
            JavaClass c = repoGet(t.getClassName());
            if(c == null) continue;
            File cf = new File(outdir,t.getClassName().replace('.',File.separatorChar) + ".class");
            cf.getParentFile().mkdirs();
            dumpClass(c,refs,cf);
        }
    }
    
    private void dumpClass(JavaClass c, Hashtable refs, File file) throws IOException {
        ClassGen cg = new ClassGen(c);
        Method[] methods= c.getMethods();
        for(int i=0;i<methods.length;i++) {
            Method m = methods[i];
            MethodRef mr = new MethodRef(c,m);
            if(refs.get(mr) == null) {
                System.err.println("Removing method " + mr);
                if(false) {
                    cg.removeMethod(m);
                } else {
                    InstructionFactory fac = new InstructionFactory(cg,cg.getConstantPool());
                    InstructionList il = new InstructionList();
                    MethodGen mg = new MethodGen(m.getAccessFlags(),m.getReturnType(),m.getArgumentTypes(),null,m.getName(),c.getClassName(),il,cg.getConstantPool());
                    il.append(fac.createNew("java.lang.UnsatisfiedLinkError"));
                    il.append(InstructionConstants.DUP);
                    il.append(new PUSH(cg.getConstantPool(),"" + mr + " has been pruned"));
                    il.append(fac.createInvoke("java.lang.UnsatisfiedLinkError","<init>",Type.VOID, new Type[]{Type.STRING},Constants.INVOKESPECIAL));
                    il.append(InstructionConstants.ATHROW);
                    mg.setMaxStack();
                    mg.setMaxLocals();
                    cg.replaceMethod(m,mg.getMethod());
                }
            } else {
                //System.err.println("Keeping method " + mr);
            }
        }
        
        Field[] fields = c.getFields();
        for(int i=0;i<fields.length;i++) {
            Field f = fields[i];
            FieldRef fr = new FieldRef(c,f);
            if(refs.get(fr) == null) {
                System.err.println("Removing field " + fr);
                cg.removeField(f);
            } else {
                //System.err.println("Keeping field " + fr);
            }
        }
        
        JavaClass n = cg.getJavaClass();
        n.dump(file);
    }
    
    public static class Exn extends Exception { public Exn(String s) { super(s); } }
        
        
    private static class MethodRef {
        ObjectType c;
        String name;
        Type ret;
        Type[] args;
        
        public MethodRef(JavaClass c, Method m) {
            this(new ObjectType(c.getClassName()),m.getName(),m.getReturnType(),m.getArgumentTypes());
        }
        
        public MethodRef(InvokeInstruction i, ConstantPoolGen cp) {
            this(i.getClassType(cp),i.getMethodName(cp),i.getReturnType(cp),i.getArgumentTypes(cp));
        }
        
        public MethodRef(ObjectType c, String name, Type ret, Type[] args) { this.c = c; this.name = name; this.ret = ret; this.args = args; }
        
        public boolean equals(Object o_) {
            if(!(o_ instanceof MethodRef)) return false;
            MethodRef o = (MethodRef)o_;
            boolean r = name.equals(o.name) && c.equals(o.c) && ret.equals(o.ret) && Arrays.equals(args,o.args);
            return r;
        }
        // FIXME: ArrayType.java in BCEL doesn't properly implement hashCode()
        public int hashCode() {
            int hc = name.hashCode()  ^ c.hashCode(); //^ ret.hashCode();
            //for(int i=0;i<args.length;i++) hc ^= args[i].hashCode();
            return hc;
        }
        public String toString() { return c.toString() + "." + name + Type.getMethodSignature(ret,args); }
    }
    
    public static class FieldRef {
        ObjectType c;
        String name;
        Type ftype;
        
        public FieldRef(JavaClass c, Field f) {
            this(new ObjectType(c.getClassName()),f.getName(),f.getType());
        }
        
        public FieldRef(FieldInstruction i, ConstantPoolGen cp) {
            this(i.getClassType(cp),i.getFieldName(cp),i.getFieldType(cp));
        }
        public FieldRef(ObjectType c, String name, Type ftype) { this.c = c; this.name = name; this.ftype = ftype; }
        
        public boolean equals(Object o_) {
            if(!(o_ instanceof FieldRef)) return false;
            FieldRef o = (FieldRef)o_;
            return name.equals(o.name) && c.equals(o.c);
        }
        public int hashCode() { return name.hashCode() ^ c.hashCode(); }
        public String toString() { return c.toString() + "." + name; }
    }
}
