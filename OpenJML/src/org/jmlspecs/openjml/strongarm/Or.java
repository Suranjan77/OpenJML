package org.jmlspecs.openjml.strongarm;

import org.jmlspecs.openjml.JmlTreeUtils;

import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCExpression;

public class Or<T extends JCExpression> extends Prop<T> {

    public Prop<T> p1;
    public Prop<T> p2;
    
    public Or(Prop<T> p1, Prop<T> p2){
        this.p1 = p1;
        this.p2 = p2;
    }
  
    public static <E extends JCExpression> Or<E> of(Prop<E> p1, Prop<E> p2){
        return new Or<E>(p1, p2);
    }
     
    public JCExpression toTree(JmlTreeUtils treeutils){
        return treeutils.makeBinary(0, JCTree.OR, p1.toTree(treeutils), p2.toTree(treeutils));
    }
    
    
}
