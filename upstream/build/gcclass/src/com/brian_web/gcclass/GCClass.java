// Copyright (C) 2004 Brian Alliet

// Based on NanoGoat by Adam Megacz

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
    
    private static final String[] IGNORED_FIELDS = {
        "java.io.ObjectInputStream.SUBCLASS_IMPLEMENTATION_PERMISSION"
    };
    
    private static final String[] NO_OUTPUT = { "java", "javax", "sun", "com.sun", "apple", "com.apple" };
    
    
    public static void main(String[] args) throws Exception {
        if(args.length < 3) {
            System.err.println("Usage GCClass classpath outdir entrypoint1 ... [ entrypoint n]");
            System.exit(1);
        }
        GCClass gc = new GCClass(args[0]);
        for(int i=2;i<args.length;i++) {
            if(args[i].startsWith("hint:"))
                gc.parseHint(args[i].substring(5));
            else
                gc.referenceMethod(args[i]);
        }
        gc.go();
        gc.dump(new File(args[1]));
    }
    
    private final Repository repo;
    private final Vector work = new Vector();
    private final Hashtable completed = new Hashtable();
    private final Hashtable references = new Hashtable();
    private final Hashtable instansiated = new Hashtable();
    private final Hashtable hints = new Hashtable();
    
    public GCClass(String classpath) throws ClassNotFoundException {
        if(classpath.startsWith("="))
            classpath = classpath.substring(1);
        else
            classpath = ClassPath.SYSTEM_CLASS_PATH + File.pathSeparator + classpath;
        repo = SyntheticRepository.getInstance(new ClassPath(classpath));
        for(int i=0;i<PRE_REF.length;i++) {
            try {
                referenceMethod(PRE_REF[i]);
            } catch(ClassNotFoundException e) {
                System.err.println("WARNING: Couldn't preref: " + PRE_REF[i]);
            }
        }
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
    
    public void referenceMethod(String s) throws ClassNotFoundException {
        int p = s.lastIndexOf('.');
        if(p == -1) throw new IllegalArgumentException("invalid class/method string");
        String cs = s.substring(0,p);
        String ms = s.substring(p+1);
        boolean skip = false;
        
        if(cs.startsWith("-")) { cs = cs.substring(1); skip = true; }
        
        if(!skip && (ms.equals("*") || ms.equals("<init>")))
            instansiated.put(new ObjectType(cs),Boolean.TRUE);
        
        JavaClass c = repoGet(cs);
        Method[] methods = c.getMethods();
        for(int i=0;i<methods.length;i++) {
            if(ms.equals("*") || methods[i].getName().equals(ms)) {
                MethodRef mr = new MethodRef(c,methods[i]);
                if(skip) completed.put(mr,Boolean.TRUE);
                else referenceMethod(mr);
            }
        }
    }
    
    public void parseHint(String s) throws ClassNotFoundException {
        int p = s.indexOf(':');
        if(p == -1) throw new IllegalArgumentException("invalid  hint");
        String cms = s.substring(0,p);
        String hint = s.substring(p+1);
        p = cms.lastIndexOf('.');
        if(p == -1)  throw new IllegalArgumentException("invalid hint");
        String cs = cms.substring(0,p);
        String ms = cms.substring(p+1);
        
        JavaClass c = repoGet(cs);
        Method[] methods = c.getMethods();
        for(int i=0;i<methods.length;i++) {
            if(ms.equals("*") || methods[i].getName().equals(ms)) {
                MethodRef mr = new MethodRef(c,methods[i]);
                Vector v = (Vector) hints.get(mr);
                if(v == null) hints.put(mr,v=new Vector());
                v.add(hint);
            }
        }
    }
        
    private final void referenceMethod(MethodRef m) {
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
    
    private final void referenceField(FieldRef f) throws ClassNotFoundException  {
        if(completed.get(f) != null) return;
        
        Hashtable h = classRefHash(f.c);
        h.put(f,Boolean.TRUE);
        
        // process(FieldRef) doesn't create much work so we don't bother queuing it 
        process(f);
        
        referenceClass(f.ftype);
    }
    
    private Hashtable repoCache = new Hashtable();
    private JavaClass repoGet(ObjectType t) throws ClassNotFoundException { return repoGet(t.getClassName()); }
    private JavaClass repoGet(String s) throws ClassNotFoundException {
        Object o = repoCache.get(s);
        if(o == null) repoCache.put(s,o = repo.loadClass(s));
        return (JavaClass) o;
    }
    
    public void go() throws Exn {
        while(work.size() != 0) {
            while(work.size() != 0) {
                MethodRef mr = (MethodRef) work.remove(work.size()-1);
                try {
                    process(mr);
                } catch(ClassNotFoundException e) {
                    e.printStackTrace();
                    String refBy = mr.refBy == null ? "unknown" : mr.refBy.toString();
                    throw new Exn("ERROR: " + refBy + " references " + mr + " which cannot be found");
                }
            }
            try {
                fixup();
            } catch(ClassNotFoundException e) {
                e.printStackTrace();
                throw new Exn("ClassNotFoundException in fixup");
            }
        }
    }
        
    private void fixup() throws ClassNotFoundException {
        for(Enumeration e = references.keys(); e.hasMoreElements(); ) {
            ObjectType t = (ObjectType) e.nextElement();
            JavaClass c = repoGet(t);
            Hashtable refs = (Hashtable) references.get(t);
             
            if(c == null) continue;
           
            // add a ref to clinit if any fields/methods are referenced
            if(refs.size() != 0) {
                MethodRef clinit = new MethodRef(t,"<clinit>",Type.VOID,Type.NO_ARGS);
                if(findMethod(c,clinit) != null) referenceMethod(clinit);
            }
            
            Method[] methods = c.getMethods();
            JavaClass[] supers = c.getSuperClasses();
            JavaClass[] interfaces = c.getInterfaces();
            
            // If a subclass can be instansiated all its superclasses also can
            if(instansiated.get(t) != null) {
                for(int i=0;i<supers.length;i++) {
                    ObjectType st = new ObjectType(supers[i].getClassName());
                    if(instansiated.get(st) != null) break;
                    instansiated.put(st, Boolean.TRUE);
                }
            }
            
            // If a subclass is referenced all is superclasses also are
            for(int i=0;i<supers.length;i++) referenceClass(supers[i]);
            
            // Go though each method and look for method references a
            // superclass or interfaces version of the method, references them
            // result in references to us
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
        
        // interfaces can only have a clinit method - every other method has no definition
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
            throw new ClassNotFoundException("" + mr + " not found (but the class was)");
        }
        
        Code code = m.getCode();
        if(code == null) return;
        
        ConstantPoolGen cpg = (ConstantPoolGen) cpgCache.get(c);
        if(cpg == null) cpgCache.put(c,cpg=new ConstantPoolGen(c.getConstantPool()));
        
        InstructionList insnList = new InstructionList(code.getCode());
        Instruction[] insns = insnList.getInstructions();
        
        for(int n=0;n<insns.length;n++) {
            Instruction i = insns[n];
            if(i instanceof NEW)
                instansiated.put(((CPInstruction)i).getType(cpg),Boolean.TRUE);
            if(i instanceof ANEWARRAY || i instanceof CHECKCAST || i instanceof INSTANCEOF || i instanceof MULTIANEWARRAY || i instanceof NEW)
                referenceClass(((CPInstruction)i).getType(cpg));
            else if(i instanceof FieldInstruction) // GETFIED, GETSTATIC, PUTFIELD, PUTSTATIC
                referenceField(new FieldRef((FieldInstruction)i,cpg));
            else if(i instanceof InvokeInstruction) // INVOKESTATIC, INVOKEVIRTUAL, INVOKESPECIAL
                referenceMethod(new MethodRef((InvokeInstruction)i,cpg,mr));
        }
        
        if(hints.get(mr) != null) {
            Vector v = (Vector) hints.get(mr);
            for(int i=0;i<v.size();i++) referenceMethod((String) v.elementAt(i));
        }
    }
    
    private void process(FieldRef fr) throws ClassNotFoundException {
        if(completed.get(fr) != null) return;
        completed.put(fr,Boolean.TRUE);

        JavaClass c = repoGet(fr.c.toString());
        Field f = findField(c,fr);
        if(f == null) {
            JavaClass supers[] = c.getSuperClasses();
            for(int i=0;i<supers.length;i++) {
                f = findField(supers[i],fr);
                if(f != null) { referenceField(new FieldRef(supers[i],f)); return; }
            }
            String sig = fr.toString();
            for(int i=0;i<IGNORED_FIELDS.length;i++) {
                String pat = IGNORED_FIELDS[i];
                if(pat.endsWith("*") ? sig.startsWith(pat.substring(0,pat.length()-1)) : sig.equals(pat)) return;
            }
            throw new ClassNotFoundException("" + fr + " not found (but the class was)");            
        }
        /* nothing to do */
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
    
    private static Field findField(JavaClass c, FieldRef fr) {
        Field[] fs = c.getFields();
        for(int i=0;i<fs.length;i++) {
            Field f = fs[i];
            if(f.getName().equals(fr.name) && f.getType().equals(fr.ftype))
                return f;
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
            boolean staticOnly = c.isClass() && instansiated.get(t) == null;
            File cf = new File(outdir,t.getClassName().replace('.',File.separatorChar) + ".class");
            cf.getParentFile().mkdirs();
            dumpClass(c,refs,staticOnly,cf);
        }
    }
    
    private void dumpClass(JavaClass c, Hashtable refs, boolean staticOnly, File file) throws IOException {
        ClassGen oldCG = new ClassGen(c);
        ConstantPoolGen oldCP = oldCG.getConstantPool();
        
        ConstantPoolGen cp = new ConstantPoolGen();
        ClassGen cg = new ClassGen(c.getClassName(),c.getSuperclassName(),c.getSourceFileName(),c.getAccessFlags(),c.getInterfaceNames(),cp);
        
        Method[] methods= oldCG.getMethods();
        for(int i=0;i<methods.length;i++) {
            Method m = methods[i];
            MethodRef mr = new MethodRef(c,m);
            if((staticOnly && !m.isStatic()) || refs.get(mr) == null) {
                System.err.println("Removing method " + mr);
                if(true) {
                    InstructionFactory fac = new InstructionFactory(cg,cg.getConstantPool());
                    InstructionList il = new InstructionList();
                    MethodGen mg = new MethodGen(m.getAccessFlags(),m.getReturnType(),m.getArgumentTypes(),null,m.getName(),c.getClassName(),il,cp);
                    il.append(fac.createNew("java.lang.UnsatisfiedLinkError"));
                    il.append(InstructionConstants.DUP);
                    if(false) {
                        il.append(new PUSH(cg.getConstantPool(),"" + mr + " has been pruned"));
                        il.append(fac.createInvoke("java.lang.UnsatisfiedLinkError","<init>",Type.VOID, new Type[]{Type.STRING},Constants.INVOKESPECIAL));
                    } else {
                        il.append(fac.createInvoke("java.lang.UnsatisfiedLinkError","<init>",Type.VOID,Type.NO_ARGS,Constants.INVOKESPECIAL));
                    }
                    il.append(InstructionConstants.ATHROW);
                    mg.setMaxStack();
                    mg.setMaxLocals();
                    cg.addMethod(mg.getMethod());
                }
            } else {                
                MethodGen mg = new MethodGen(m,cg.getClassName(),oldCP);
                mg.setConstantPool(cp);
                if(mg.getInstructionList() != null) mg.getInstructionList().replaceConstantPool(oldCP, cp);
                
                Attribute[] attrs = m.getAttributes();
                for(int j=0;j<attrs.length;j++) {
                    Attribute a = attrs[j];
                    if(a instanceof Code || a instanceof ExceptionTable) continue;
                    mg.removeAttribute(a);
                    Constant con = oldCP.getConstant(a.getNameIndex());
                    a.setNameIndex(cp.addConstant(con,oldCP));
                    mg.addAttribute(a);                    
                }
                
                mg.removeLineNumbers();
                mg.removeLocalVariables();
                cg.addMethod(mg.getMethod());
            }
        }
        
        Field[] fields = c.getFields();
        for(int i=0;i<fields.length;i++) {
            Field f = fields[i];
            FieldRef fr = new FieldRef(c,f);
            if(refs.get(fr) == null) {
                System.err.println("Removing field " + fr);
            } else {
                //System.err.println("Keeping field " + fr);
                FieldGen fg = new FieldGen(f.getAccessFlags(),f.getType(),f.getName(),cp);
                Attribute[] attrs = f.getAttributes();
                for(int j=0;j<attrs.length;j++) {
                    if(attrs[j] instanceof ConstantValue) {
                        ConstantObject co = (ConstantObject) oldCP.getConstant(((ConstantValue)attrs[i]).getConstantValueIndex());
                        Object o = co.getConstantValue(oldCP.getConstantPool());
                        if(co instanceof ConstantLong) fg.setInitValue(((Number)o).longValue());
                        else if(co instanceof ConstantInteger) fg.setInitValue(((Number)o).intValue());
                        else if(co instanceof ConstantFloat) fg.setInitValue(((Number)o).floatValue());
                        else if(co instanceof ConstantDouble) fg.setInitValue(((Number)o).floatValue());
                        else if(co instanceof ConstantString) fg.setInitValue((String)o);
                        else throw new Error("should never happen");
                    } else {
                        Attribute a = attrs[j];
                        Constant con = oldCP.getConstant(a.getNameIndex());
                        a.setNameIndex(cp.addConstant(con,oldCP));
                        //System.err.println("Adding attribute: " + attrs[j]);
                        fg.addAttribute(a);
                    }
                }
                /*if(f.getConstantValue() != null) throw new Error("this might be broken");
                FieldGen fg = new FieldGen(f.getAccessFlags(),f.getType(),f.getName(),cp);*/
                cg.addField(fg.getField());
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
        MethodRef refBy;
                
        public MethodRef(JavaClass c, Method m) {
            this(new ObjectType(c.getClassName()),m.getName(),m.getReturnType(),m.getArgumentTypes());
        }
        
        public MethodRef(InvokeInstruction i, ConstantPoolGen cp, MethodRef refBy) {
            this(i.getClassType(cp),i.getMethodName(cp),i.getReturnType(cp),i.getArgumentTypes(cp));
            this.refBy = refBy;
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
    
    private static class FieldRef {
        ObjectType c;
        String name;
        Type ftype;
        MethodRef refBy;
        
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
