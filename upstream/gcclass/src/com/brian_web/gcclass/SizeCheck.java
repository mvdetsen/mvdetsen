// Copyright (C) 2004 Brian Alliet

package com.brian_web.gcclass;

import java.util.*;
import java.util.zip.*;
import java.io.*;
import org.apache.bcel.util.*;
import org.apache.bcel.classfile.*;

// This code is hideous... it is just a quick hack

public class SizeCheck {
    public static void main(String[] args) throws Exception {
        if(args.length < 3) {
            System.err.println("Usage SizeCheck classpath class1 ... [class n]");
            System.exit(1);
        }
        String classpath = ClassPath.SYSTEM_CLASS_PATH + File.pathSeparator + args[0];
        Repository repo = SyntheticRepository.getInstance(new ClassPath(classpath));
        
        List all= new ArrayList();
        int alltotal=0;
        for(int j=1;j<args.length;j++) {
            String s = args[j];
            if(s.endsWith(".class")) s = s.substring(0,s.length()-6).replace('/','.');
            while(s.startsWith(".")) s = s.substring(1);
            JavaClass c = repo.loadClass(s);
            List stuff = new ArrayList();
            ByteArrayOutputStream baos;
            GZIPOutputStream gzos;
            DataOutputStream dos;
            int total = 0;
            baos = new ByteArrayOutputStream();
            gzos = new GZIPOutputStream(baos);
            dos = new DataOutputStream(gzos);
            c.getConstantPool().dump(dos);
            gzos.finish();
            total += baos.size();
            stuff.add(new Stuff(baos.size(),"Constant Pool"));
            Method[] methods = c.getMethods();
            for(int i=0;i<methods.length;i++) {
                baos = new ByteArrayOutputStream();
                gzos = new GZIPOutputStream(baos);
                dos = new DataOutputStream(gzos);
                methods[i].dump(dos);
                gzos.finish();
                stuff.add(new Stuff(baos.size(),methods[i].getName()));
                total += baos.size();
            }
            stuff.add(new Stuff(total,"Total"));
            StringBuffer sb = new StringBuffer(s + ":\n");
            Collections.sort(stuff);
            for(int i=0;i<stuff.size();i++) {
                Stuff st = (Stuff) stuff.get(i);
                sb.append("\t" + st.size + (st.size < 1000 ? "\t\t" : "\t") + st.desc + "\n");
            }
            all.add(new Stuff(total,sb.toString()));
            alltotal += total;
        }
        Collections.sort(all);
        for(int i=0;i<all.size();i++) {
            Stuff st = (Stuff) all.get(i);
            System.out.println(st.size + ": " + st.desc);
        }
        System.out.println("Total: " + alltotal);
    }
    
    public static class Stuff implements Comparable {
        int size;
        String desc;
        public int compareTo(Object o_) {
            Stuff o = (Stuff)o_;
            return desc.equals("Total") ? 1 : o.desc.equals("Total") ? -1 : -(size - o.size); }
        public Stuff(int size, String desc) { this.size = size; this.desc = desc; }
    }
}