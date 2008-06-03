package org.ibex.classgen;
import java.util.*;

/**
 *  a Context is like a ClassLoader in that it maps from class names
 *  to bytecode-implementations of classes, except that it doesn't
 *  actually load the resolved class -- it simply creates a (cached)
 *  ClassFile for it.
 */
public class Context {

    private Hashtable cache = new Hashtable();

    public Context() { }

    public void add(ClassFile cf) { cache.put(cf.getType().getName(), cf); }
    public Collection enumerateClassFiles() { return cache.values(); }
    public ClassFile resolve(String classname) { return (ClassFile)cache.get(classname); }

}
