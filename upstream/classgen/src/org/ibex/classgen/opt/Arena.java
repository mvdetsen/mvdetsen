package org.ibex.classgen.opt;
import java.io.*;
import java.util.*;
import org.ibex.classgen.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

public class Arena implements CGConst {

    public static interface Gladiator { }

    Context cx = new Context();

    public static final int initialSize = 1000;


    // Initializers //////////////////////////////////////////////////////////////////////////////

    public static Type.Class        System_class     = Type.Class.instance("java.lang.System");
    public static Type.Class.Method System_arraycopy = System_class.method("arraycopy","(Ljava/lang/Object;ILjava/lang/Object;II)V");
    public static Type.Class        Gladiator_class  = Type.Class.instance("org.ibex.classgen.opt.Arena$Gladiator");

    boolean          implementsGladiator(Type t)          { return t instanceof Type.Class && implementsGladiator((Type.Class)t);}
    boolean          implementsGladiator(Type.Class c)    { return c.extendsOrImplements(Gladiator_class, cx); }

    String           getArenaName(Type.Class c)           { return c.getName().substring(0, c.getName().lastIndexOf('$')); }
    String           getGladiatorName(Type.Class c)       { return c.getName().substring(c.getName().lastIndexOf('$')+1); }
    Type.Class       getArenaForGladiator(Type.Class c)   { return Type.Class.instance(getArenaName(c)); }

    Type             getSliceElementType(Type t)          { return implementsGladiator(t) ? Type.INT : t; }
    Type.Class.Field getSliceForField(Type.Class.Field f) {
        Type.Class c = f.getDeclaringClass();
        return getArenaForGladiator(c).field(getGladiatorName(c)+"$$"+f.getName(),
                                             getSliceElementType(f.getType()).makeArray());
    }


    // Operations performed on the Gladiator class //////////////////////////////////////////////////////////////////////////

    public Type.Class.Method.Body getSoleConstructor(Type.Class c) {
        Type.Class.Method.Body ret = null;
        Type.Class.Method.Body[] bodies = c.getBody(cx).methods();
        for(int i=0; i<bodies.length; i++) {
            if (!bodies[i].getMethod().isConstructor()) continue;
            if (ret != null) throw new Error("class " + c.getName() + " has two constructors");
            ret = bodies[i];
        }
        return ret;
    }

    public void processGladiatorClass(Type.Class c) {

        System.out.println("**** " + c.getName() + " is a gladiator!");

        Type.Class             arena         = getArenaForGladiator(c);
        Type.Class.Body        arenaBody     = arena.getBody(cx);
        Type.Class.Method.Body arenaInitBody = getSoleConstructor(c);
        Type.Class.Method      arenaInit     = arenaInitBody.getMethod();

        Type.Class.Field            maxField = arena.field(getGladiatorName(c) + "$$max", Type.INT);
        /*arenaBody.addField(maxField, PRIVATE);*/
        /*
        assign(arenaInitBody, newIFR(arenaInitBody, maxField.makeRef()), IntConstant.v(initialSize),
               arenaInitBody.getFirstNonIdentityStmt());
        */

        Type.Class.Field sizeField = arena.field(getGladiatorName(c) + "$$size", Type.INT);
        /*arenaBody.addField(sizeField, PRIVATE);*/
        /*
        assign(arenaInitBody, newIFR(arenaInitBody, sfr.makeRef()), IntConstant.v(0),
               arenaInitBody.getFirstNonIdentityStmt());
        */
        /*
        Type.Class.Method      incMethod = c.method(getGladiatorName(c) + "$$inc()I");
        Type.Class.Method.Body incBody   = incMethod.getBody(cx);


        // Now build the $$inc method

        Local l  =  newLocal(incBody, IntType.v());
        Local l2 =  newLocal(incBody, IntType.v());
        Local l3 =  newLocal(incBody, IntType.v());
        Local l4 =  newLocal(incBody, IntType.v());
       
        assign(incBody, l,                                 newIFR(incBody, sfr.makeRef()));
        assign(incBody, l2,                                Jimple.v().newAddExpr(l, IntConstant.v(1)));
        assign(incBody, newIFR(incBody, sfr.makeRef()),       l2);
        assign(incBody, l3,                                newIFR(incBody, maxField.makeRef()));

        Stmt returnStmt = Jimple.v().newReturnStmt(l2);
        incBody.getUnits().add(Jimple.v().newIfStmt(Jimple.v().newLtExpr(l2, l3), returnStmt));

        assign(incBody,  l4,                               Jimple.v().newShlExpr(l3, IntConstant.v(1)));
        assign(incBody,  newIFR(incBody, maxField.makeRef()), l4);


        // Finally, iterate over the Gladiator's fields, updating the $$inc method and Arena's zero-arg constructor as we go

        for(Iterator it = sc.getFields().iterator(); it.hasNext();) {
            Type.Class.Field f = (Type.Class.Field)it.next();
            Type t      = getSliceElementType(f.getType());
            f = arena.field(getGladiatorName(sc) + "$$" + f.getName(), t.makeArray());
            arena.addField(f);

            Expr newArr = Jimple.v().newNewArrayExpr(t, IntConstant.v(initialSize));
            Local newArrLocal = newLocal(arenaInitBody, f.getType());
            arenaInitBody.getUnits().addFirst(Jimple.v().newAssignStmt(newIFR(arenaInitBody, f.makeRef()), newArrLocal));
            arenaInitBody.getUnits().addFirst(Jimple.v().newAssignStmt(newArrLocal, newArr));

            Local ll0 = newLocal(incBody, f.getType());
            Local ll = newLocal(incBody, f.getType());
            assign(incBody, ll0, newIFR(incBody,  f.makeRef()));
            assign(incBody, ll,  Jimple.v().newNewArrayExpr(t, l4));

            List args = new LinkedList();
            args.add(ll0);
            args.add(IntConstant.v(0));
            args.add(ll);
            args.add(IntConstant.v(0));
            args.add(l3);
            incBody.getUnits().add(Jimple.v().newInvokeStmt(Jimple.v().newStaticInvokeExpr(System_arraycopy, args)));
            assign(incBody, newIFR(incBody,  f.makeRef()), ll);
        }

        for(Iterator it = c.getBody(cx).getMethods().iterator(); it.hasNext();) {
            Type.Class.Method m = (Type.Class.Method)it.next();
            if (!m.isConcrete()) continue;
            boolean doremove = true;
            Type.Class.Method.Body mincBody = m.getBody(cx);
            if (implementsGladiator(m.getDeclaringClass()) && m.isConstructor()) {
                System.out.println("processing ctor " + c.getName() + "." + m.getSignature());
                doremove = false;
                Type.Class c = m.getDeclaringClass();
                String name = "$init";
                Type[] at = m.getArgTypes();
                c.removeMethod(m);
                Type.Class.Method nm = c.method(name, implementsGladiator(m.getReturnType()) ? Type.INT : m.getReturnType(), at);
                Type.Class.Method.Body bod = nm.getBody(cx);
                bod.importBodyContentsFrom(m.getActiveBody());
                m = nm;
                mincBody = bod;

                for(Iterator it2 = mincBody.getUnits().snapshotIterator(); it2.hasNext(); ) {
                    Unit u = (Unit)it2.next();
                    if (u instanceof DefinitionStmt) {
                        DefinitionStmt ds = (DefinitionStmt)u;
                        if (ds.getLeftOp() instanceof ThisRef)
                            mincBody.getUnits().remove(u);
                        else if (ds.getLeftOp() instanceof FieldRef) {
                            if (((FieldRef)ds.getLeftOp()).getFieldRef().name().endsWith("this$0"))
                                mincBody.getUnits().remove(u);
                        }
                    } else if (u instanceof InvokeStmt) {
                        InvokeExpr ie = ((InvokeStmt)u).getInvokeExpr();
                        Type.Class.MethodRef meth = ie.getMethodRef();
                        if (meth.getDeclaringClass().getName().equals("java.lang.Object") && meth.name().equals("<init>"))
                            mincBody.getUnits().remove(u);
                    }
                }
            }
            if (m.isStatic()) continue;

            String name = c.getShortName().substring(c.getShortName().lastIndexOf('$')+1) + "$$" + m.getName();
            Type[] list = new Type[m.getNumArgs() + 1];
            for(int i=0; i<m.getNumArgs(); i++) list[i] = m.getArgType(i);
            list[list.length-1] = Type.INT;
            Type.Class.Method m2 = c.method(name, m.getReturnType(), list);
            getArenaForGladiator(c).addMethod(m2);
            Type.Class.Method.Body ab = m2.getBody(cx);
            ab.importBodyContentsFrom(mincBody);

            Local loc = Jimple.v().newLocal("tmpRef" + (tfr++), getArenaForGladiator(sc).getType());
            ab.getLocals().add(loc);
            // FIXME: insert assignment to this
            for(Iterator z = ab.getLocals().iterator(); z.hasNext();) {
                Local loc2 = (Local)z.next();
                if (implementsGladiator(loc2.getType())) {
                    loc2.setType(IntType.v());
                }
            }
            Chain units = ab.getUnits();
            boolean touched = false;
            Local loc0 = Jimple.v().newLocal("tmpRef" + (tfr++), getArenaForGladiator(sc).getType());
            ab.getLocals().add(loc0);
            for(Iterator stmtIt = units.snapshotIterator(); stmtIt.hasNext();) {
                Stmt s = (Stmt) stmtIt.next();
                if (s instanceof IdentityStmt) {
                    IdentityStmt is = (IdentityStmt)s;
                    Local left = (Local)is.getLeftOp();
                    if (is.getRightOp() instanceof ThisRef) {
                        left.setType(IntType.v());
                        is.getRightOpBox().setValue(Jimple.v().newParameterRef(IntType.v(), m.getParameterCount()));
                        if (!touched) {
                            units.addFirst(Jimple.v().newIdentityStmt(loc0, Jimple.v().newThisRef(getArenaForGladiator(sc).getType())));
                            touched = true;
                        }
                    }
                }

                for(Iterator i = s.getUseAndDefBoxes().iterator(); i.hasNext();) {
                    Object o = i.next();
                    if (o instanceof ValueBox) {
                        ValueBox vb = (ValueBox)o;
                        o = vb.getValue();
                        //if (o instanceof Local && implementsGladiator(((Local)o).getType())) {
                        //System.out.println("thunking");
                        //vb.setValue(loc0);
                        //}
                        if (vb.getValue() instanceof ThisRef) {
                            vb.setValue(loc);
                        }
                    }
                }

            }
            
            if (doremove) sc.removeMethod(m);

        }
        incBody.getUnits().add(returnStmt);
        */
    }

    // Operations performed on all classes ////////////////////////////////////////////////////////////////////////////

    public Type processType(Type t) {
        if (t instanceof Type.Array) return processType(((Type.Array)t).getElementType()).makeArray();
        if (implementsGladiator(t)) return Type.INT;
        return t;
    }

    public void processField(Type.Class.Field.Body fb) {
        /*
        f.setType(processType(f.getType()));
        */
    }

    public void processMethod(Type.Class.Method.Body mb) {
        /*
        if (m.getName().endsWith("$$inc")) continue;
        //if (m.isConcrete()) b = processBody(m.getBody(cx), c, m);
        m.setReturnType(processType(m.getReturnType()));
        Type[] argTypes = m.getArgTypes();
        for(int i=0; i<argTypes.length; i++) argTypes[i] = processType(argTypes[i]);
        m.setArgTypes(argTypes);
        
        if (implementsGladiator(t)) {
            t = IntType.v();
            if (m.hasActiveBody()) {
                Body bod = m.getActiveBody();
                for(Iterator stmtIt = bod.getUnits().snapshotIterator(); stmtIt.hasNext();) {
                    Stmt s = (Stmt) stmtIt.next();
                    if (s instanceof ReturnStmt) {
                        if (((ReturnStmt)s).getOp().getType() instanceof NullType) {
                            ((ReturnStmt)s).getOpBox().setValue(IntConstant.v(-1));
                        }
                    }
                }
            }
        }
        c.removeMethod(m);
        c.addMethod(meth);
        */
    }

    public void processClassFile(ClassFile cf) {
        boolean verdict = implementsGladiator(cf.getType());
        //System.out.println("checking " + cf.getType().getName() + " => " + verdict);
        if (verdict) processGladiatorClass(cf.getType());
        Type.Class.Field.Body[] fields = cf.fields();
        for(int i=0; i<fields.length; i++) processField(fields[i]);
        Type.Class.Method.Body[] methods = cf.methods();
        for(int i=0; i<methods.length; i++) processMethod(methods[i]);
    }

    /*
    protected Body processBody(Body body, Type.Class ownerClass, Type.Class.Method smeth) {
        Chain units = body.getUnits();
        for(Iterator it = body.getLocals().snapshotIterator(); it.hasNext();) {
            Local l = (Local)it.next();
            if (implementsGladiator(l.getType())) l.setType(IntType.v());
        }
        if (!smeth.isStatic())
            body.getThisLocal().setType(ownerClass.getType());
        for(int qq=0; qq<2; qq++) for(Iterator stmtIt = units.snapshotIterator(); stmtIt.hasNext();) {
            Stmt s = (Stmt) stmtIt.next();
            if (s instanceof DefinitionStmt) {
                DefinitionStmt ds = (DefinitionStmt)s;
                if (ds.getLeftOp().getType() instanceof PrimType && ds.getRightOp().getType() instanceof NullType)
                    ds.getRightOpBox().setValue(IntConstant.v(-1));
            }
            if (implementsGladiator(smeth.getReturnType()) && s instanceof ReturnStmt)
                if (((ReturnStmt)s).getOp().getType() instanceof NullType)
                    ((ReturnStmt)s).getOpBox().setValue(IntConstant.v(-1));
            List l = s.getUseAndDefBoxes();
            List l2l = new LinkedList();
            l2l.addAll(l);
            for(Iterator it = l2l.iterator(); it.hasNext();) {
                Object o = it.next();
                if (o instanceof ValueBox) {
                    ValueBox vb = (ValueBox)o;
                    Value v = vb.getValue();
                    
                    if (v instanceof BinopExpr) {
                        BinopExpr boe = (BinopExpr)v;
                        Type t1 = boe.getOp1().getType();
                        Type t2 = boe.getOp2().getType();
                        if (t1 instanceof PrimType && t2 instanceof NullType) boe.setOp2(IntConstant.v(-1));
                        if (t2 instanceof PrimType && t1 instanceof NullType) boe.setOp1(IntConstant.v(-1));
                    }

                    if (v instanceof NewExpr) {
                        NewExpr ne = (NewExpr)v;
                        if (implementsGladiator(ne.getBaseType())) {
                            Type.Class sc = ((Type.Ref)ne.getBaseType()).Type.Class.instance();
                            Type.Class arena = getArenaForGladiator(sc);
                            String incFuncName = sc.getShortName().substring(sc.getShortName().lastIndexOf('$')+1) + "$$inc";
                            Type.Class.MethodRef smr = Scene.v().makeMethodRef(arena, incFuncName, new LinkedList(), IntType.v(), false);
                            Expr invokeExpr = Jimple.v().newSpecialInvokeExpr(body.getThisLocal(), smr);
                            Local ll = viaLocal(invokeExpr, body, s);
                            vb.setValue(ll);
                            v = ll;
                            qq = 0;
                            break;
                        } 
                    } else

                    if (v instanceof InvokeExpr) {
                        InvokeExpr ie = (InvokeExpr)v;
                        Type.Class.MethodRef mr = ie.getMethodRef();
                        String name = mr.name();
                        if (v instanceof InstanceInvokeExpr && implementsGladiator(mr.getDeclaringClass())) {
                            InstanceInvokeExpr iie = (InstanceInvokeExpr)v;
                            List li = new LinkedList();
                            li.addAll(iie.getArgs());
                            LinkedList pl = new LinkedList();
                            for(Iterator it2 = mr.parameterTypes().iterator(); it2.hasNext();) {
                                Type t = (Type)it2.next();
                                pl.add(implementsGladiator(t) ? IntType.v() : t);
                            }
                            if (mr.name().equals("<init>") && implementsGladiator(mr.getDeclaringClass())) {
                                name = "$init";
                                //li.remove(0);
                                //pl.remove(0);
                            }
                            pl.add(IntType.v());
                            //li.add(iie.getBase());
                            Type.Class sc = mr.getDeclaringClass();
                            name = sc.getShortName().substring(sc.getShortName().lastIndexOf('$')+1) + "$$" + name;
                            mr = Scene.v().makeMethodRef(getArenaForGladiator(sc),
                                                         name,
                                                         pl,
                                                         implementsGladiator(mr.returnType()) ? IntType.v() : mr.returnType(),
                                                         false);
                            ie = Jimple.v().newVirtualInvokeExpr(body.getThisLocal(), mr, li);
                            vb.setValue(v = ie);

                        } else if (!(v instanceof StaticInvokeExpr)) {
                            List l0 = mr.parameterTypes();
                            List l2 = new LinkedList();
                            for(Iterator it2 = l0.iterator(); it2.hasNext();) {
                                Type t = (Type)it2.next();
                                l2.add(implementsGladiator(t) ? IntType.v() : t);
                            }
                            mr = Scene.v().makeMethodRef(mr.getDeclaringClass(),
                                                         mr.name(),
                                                         l2,
                                                         implementsGladiator(mr.returnType()) ? IntType.v() : mr.returnType(),
                                                         mr.isStatic());
                            ie.setMethodRef(mr);
                            vb.setValue(v = ie);                            
                        }

                        for(int i=0; i<ie.getArgCount(); i++) {
                            ValueBox b = ie.getArgBox(i);
                            Value val = b.getValue();
                            if (mr.parameterType(i) instanceof Type.Ref && val.getType() instanceof PrimType) {
                                Type.Class intClass = Scene.v().Type.Class.instance("java.lang.Integer");
                                List typelist = new LinkedList();
                                typelist.add(IntType.v());
                                Type.Class.Method intMethod = intClass.getMethod("<init>", typelist);
                                Local loc = viaLocal(Jimple.v().newNewExpr(Type.Ref.v(intClass)), body, s);
                                List list = new LinkedList();
                                list.add(val);
                                units.insertBefore(Jimple.v().newInvokeStmt(Jimple.v().newSpecialInvokeExpr(loc,
                                                                                                            intMethod.makeRef(),
                                                                                                            list)),
                                                   s);
                                b.setValue(loc);
                            }
                            if (val != null && val.getType() instanceof NullType && mr.parameterType(i) instanceof IntType) {
                                b.setValue(IntConstant.v(-1));
                            }
                        }


                    } else if (v instanceof CastExpr) {
                        CastExpr ce = (CastExpr)v;
                        if (implementsGladiator(ce.getCastType())) {
                            Type.Class arena = getArenaForGladiator(((Type.Ref)ce.getCastType()).Type.Class.instance());
                            Type.Class ic = Scene.v().Type.Class.instance("java.lang.Integer");
                            ce.setCastType(ic.getType());

                            Local l1 = Jimple.v().newLocal("tmpRef" + (tfr++), ic.getType()); body.getLocals().add(l1);
                            Local l2 = Jimple.v().newLocal("tmpRef" + (tfr++), IntType.v()); body.getLocals().add(l2);

                            Stmt s2 = Jimple.v().newAssignStmt(l1, Jimple.v().newCastExpr(ce.getOp(), ic.getType()));
                            body.getUnits().insertBefore(s2, s);

                            Stmt isNull = Jimple.v().newAssignStmt(l2, IntConstant.v(-1));
                            body.getUnits().insertAfter(isNull, s2);

                            Stmt ifStmt = Jimple.v().newIfStmt(Jimple.v().newEqExpr(l1, NullConstant.v()), s);
                            body.getUnits().insertAfter(ifStmt, isNull);

                            Type.Class.MethodRef mr = Scene.v().makeMethodRef(ic, "intValue", new LinkedList(), IntType.v(), false);
                            Stmt isNotNull =
                                Jimple.v().newAssignStmt(l2, Jimple.v().newVirtualInvokeExpr(l1, mr, new LinkedList()));
                            body.getUnits().insertAfter(isNotNull, ifStmt);

                            vb.setValue(l2);
                            qq = 0;  // ???
                            break;
                        }

                    } else if (v instanceof FieldRef) {
                        FieldRef ifr = (FieldRef)v;
                        Type.Class.Field fr = ifr.getFieldRef();
                        Type t = fr.type();
                        if (implementsGladiator(fr.getDeclaringClass()) && fr.name().equals("this$0")) {
                            vb.setValue(body.getThisLocal());
                        } else if (implementsGladiator(fr.getDeclaringClass())) {
                            Type.Class arena = getArenaForGladiator(fr.getDeclaringClass());
                            if (fr.isStatic()) {
                                vb.setValue(newIFR(body, getSliceForField(fr)));
                            } else {
                                InstanceFieldRef sfr = newIFR(body, getSliceForField(fr));
                                vb.setValue(Jimple.v().newArrayRef(viaLocal(sfr, body, s), ((InstanceFieldRef)ifr).getBase()));
                            }
                        }
                        if ((t instanceof Type.Ref) && implementsGladiator(((Type.Ref)t).Type.Class.instance())) {
                            Type.Class tc = ((Type.Ref)t).Type.Class.instance();
                            Type.Class arena = getArenaForGladiator(tc);
                            ifr.setFieldRef(Scene.v().makeFieldRef(arena, fr.name(), IntType.v(), fr.isStatic()));
                        } else if (t instanceof Type.Array) {
                            Type.Array at = (Type.Array)t;
                            Type et = at.getElementType();
                            if (et instanceof Type.Ref && implementsGladiator(((Type.Ref)et).Type.Class.instance()))
                                ifr.setFieldRef(Scene.v().makeFieldRef(fr.getDeclaringClass(),
                                                                       fr.name(),
                                                                       IntType.v().makeType.Array(),
                                                                       fr.isStatic()));
                        }
                    }

                }
            }
        }
        return body;
    }
*/

    public static void main(String[] s) throws Exception { new Arena().process(s); }
    public void process(String[] s) throws Exception {
        File outf = new File(s[0] + "-");
        File inf = new File(s[0]);
        ZipOutputStream out = new ZipOutputStream(new FileOutputStream(outf));
        ZipInputStream zis = new ZipInputStream(new FileInputStream(inf));
        for(;;) {
            ZipEntry ze = zis.getNextEntry();
            if (ze==null) break;
            String name = ze.getName();
            if (!name.endsWith(".class")) {
                out.putNextEntry(ze);
                byte b[] = new byte[1024];
                for(;;) {
                    int numread = zis.read(b, 0, b.length);
                    if (numread==-1) break;
                    out.write(b, 0, numread);
                }
                continue;
            }
            System.out.println("updating " + name.substring(0, name.length()-6).replace('$','.').replace('/','.'));
            ClassFile cf = new ClassFile(new DataInputStream(zis));
            cx.add(cf);
        }
        for(Iterator it = cx.enumerateClassFiles().iterator(); it.hasNext();) {
            ClassFile cf = (ClassFile)it.next();
            processClassFile(cf);
        }
        for(Iterator it = cx.enumerateClassFiles().iterator(); it.hasNext();) {
            ClassFile cf = (ClassFile)it.next();
            out.putNextEntry(new ZipEntry(cf.getType().getName().replace('.', '/') + ".class"));
            cf.dump(out);
        }
        out.close();
        outf.renameTo(inf);
    }

}
