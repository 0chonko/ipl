/* $Id$ */

package ibis.frontend;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

import org.apache.bcel.Repository;
import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.JavaClass;

/**
 * BCEL implementation of the <code>ByteCodeWrapper</code> interface.
 */
public class BCELWrapper implements ByteCodeWrapper {

    private HashMap<String, BCELClassInfo> javaClasses
            = new HashMap<String, BCELClassInfo>();

    public ClassInfo getInfo(Object o) {
        JavaClass cl = (JavaClass) o;
        String name = cl.getClassName();
        BCELClassInfo e = javaClasses.get(name);
        if (e == null) {
            e = new BCELClassInfo(cl);
            javaClasses.put(name, e);
        }
        e.setClassObject(cl);
        return e;
    }

    public ClassInfo parseClassFile(String fileName) throws IOException {
        JavaClass cl = new ClassParser(fileName).parse();
        Repository.addClass(cl);
        String name = cl.getClassName();
        BCELClassInfo e = javaClasses.get(name);
        if (e == null) {
            e = new BCELClassInfo(cl);
            javaClasses.put(name, e);
        }
        e.setClassObject(cl);
        Repository.addClass(cl);
        return e;
    }

    public ClassInfo parseInputStream(InputStream in, String fileName)
            throws IOException {
        JavaClass cl = new ClassParser(in, fileName).parse();
        Repository.addClass(cl);
        String name = cl.getClassName();
        BCELClassInfo e = javaClasses.get(name);
        if (e == null) {
            e = new BCELClassInfo(cl);
            javaClasses.put(name, e);
        }
        e.setClassObject(cl);
        Repository.addClass(cl);
        return e;
    }
}
