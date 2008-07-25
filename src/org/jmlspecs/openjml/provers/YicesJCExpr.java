package org.jmlspecs.openjml.provers;

import org.jmlspecs.openjml.JmlToken;
import org.jmlspecs.openjml.JmlTreeScanner;
import org.jmlspecs.openjml.JmlTree.JmlBBArrayAccess;
import org.jmlspecs.openjml.JmlTree.JmlBBArrayAssignment;
import org.jmlspecs.openjml.JmlTree.JmlBBFieldAccess;
import org.jmlspecs.openjml.JmlTree.JmlBBFieldAssignment;
import org.jmlspecs.openjml.JmlTree.JmlBinary;
import org.jmlspecs.openjml.JmlTree.JmlQuantifiedExpr;
import org.jmlspecs.openjml.esc.BasicBlocker;
import org.jmlspecs.openjml.proverinterface.ProverException;

import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.TypeTags;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.util.Name;

/** This class converts OpenJDK ASTs (i.e., JCTree) into Strings appropriate
 * to send to Yices; in the process, requests to define various variables may
 * be sent back to the invoking YicesProver.  It is implemented as a tree 
 * walker.
 * @author David Cok
 */
public class YicesJCExpr extends JmlTreeScanner {

    /** The tool used to report errors and warnings */
//    protected Log log;
    
    /** The prover that invoked this translator; we need this because we have to
     * tell it to define variables as they are encountered. */
    /*@ non_null */protected YicesProver p;
    
    /** Does the translation.  
     * 
     * @param t the tree to translate
     * @param p the prover invoking this translation
     * @return the translated string
     */
    static public String toYices(JCTree t, YicesProver p) 
            throws ProverException {
        try {
        YicesJCExpr tr = new YicesJCExpr(p);
        t.accept(tr);
        return tr.result.toString(); // FIXME - no type given?
        } catch (RuntimeException e) {
            if (e.getCause() instanceof ProverException) {
                throw (ProverException)e.getCause();
            } else {
                // Some unknown exception thrown - so convert it to a 
                // ProverException and copy the stack trace
                ProverException ee = new ProverException(e.toString());
                ee.setStackTrace(e.getStackTrace());
                throw ee;
            }
        }
    }
    
    /** The constructor of a new translator, connected to the given prover.
     * 
     * @param p the prover to connect with
     */
    protected YicesJCExpr(/*@ non_null */YicesProver p) {
        this.p = p;
    }

    /** The builder in which to create the resulting string */
    /*@ non_null */
    private StringBuilder result = new StringBuilder();

    public void visitIdent(JCIdent that) {
        // Make sure the id is defined.
        // Emit it simply as its string
        try {
            p.define(that.toString(),that.type);
        } catch (ProverException e) {
            throw new RuntimeException(e);
        }
        result.append(that.toString());
    }
        
    public void visitParens(JCParens that) {
        // Ignore parenthesized expression - all the output for Yices
        // is parenthesized in prefix form anyway
        that.expr.accept(this);
    }
    
    public void visitLiteral(JCLiteral that) {
        // Just use the string form.  Should only get basic types: boolean
        // and int.  // FIXME - characters, strings? others?
        result.append(that.toString());
    }
    
    public void visitApply(JCMethodInvocation that) {
        // FIXME - document
        if (that.meth != null) {
            // Should have only one argument (an \old or \pre can get here)
            //System.out.println("visit Apply: " + that.meth.getClass() + " " + that.meth);
            if (!(that.meth instanceof JCIdent)) 
                that.args.get(0).accept(this);
            else {
                // check for definition
                if (!p.checkAndDefine(that.meth.toString())) {
                    // Was not already defined
                    String s = "(define " + that.meth + "::(->";
                    for (JCExpression e: that.args) {
                        s = s + " " + p.convertType(e.type);
                    }
                    s = s + " " + p.convertType(that.meth.type) + "))\n";
                    try {
                        p.send(s);
                        p.eatPrompt();
                    } catch (ProverException e) {
                        throw new RuntimeException(e);
                    }
                } 
                // regular method application
                result.append("(");
                that.meth.accept(this);
                result.append(" ");
                for (JCExpression e: that.args) {
                    e.accept(this);
                    result.append(" ");
                }
                result.append(")");
                
            }
        } else if (that instanceof JmlBBArrayAssignment) {
            JCIdent newarrs = (JCIdent)that.args.get(0);
            JCIdent oldarrs = (JCIdent)that.args.get(1);
            JCExpression arr = that.args.get(2);
            JCExpression index = that.args.get(3);
            JCExpression rhs = that.args.get(4);
            Type t = rhs.type;
            String s = BasicBlocker.encodeType(t);
            String ty = "array$" + s;
            try {
                if (!p.checkAndDefine(ty)) {
                    p.send("(define-type " + ty + ")\n");
                    p.eatPrompt();
                }
                if (!p.checkAndDefine(newarrs.toString())) {
                    // Was not already defined
                    p.send("(define " + newarrs + "::(-> " + ty + "  int " + s + "))\n");
                    p.eatPrompt();
                } 
                if (!p.checkAndDefine(oldarrs.toString())) {
                    // Was not already defined
                    p.send("(define " + oldarrs + "::(-> " + ty + "  int " + s + "))\n");
                    p.eatPrompt();
                } 
                result.append("(= " + newarrs);
                result.append(" (update ");
                result.append(oldarrs);
                result.append(" (");
                arr.accept(this);
                result.append(" ");
                index.accept(this);
                result.append(") ");
                rhs.accept(this);
                result.append("))");
            } catch (ProverException e) {
                throw new RuntimeException(e);
            }
        } else if (that instanceof JmlBBFieldAssignment) {
            JCIdent newfield = (JCIdent)that.args.get(0);
            JCIdent oldfield = (JCIdent)that.args.get(1);
            JCExpression selected = that.args.get(2);
            JCExpression rhs = that.args.get(3);
            Type t = rhs.type;
            String s = BasicBlocker.encodeType(t);
            try {
                if (!p.checkAndDefine(newfield.toString())) {
                    // Was not already defined
                    p.send("(define " + newfield + "::(-> REF " + s + "))\n");
                    p.eatPrompt();
                } 
                if (!p.checkAndDefine(oldfield.toString())) {
                    // Was not already defined
                    p.send("(define " + oldfield + "::(-> REF " + s + "))\n");
                    p.eatPrompt();
                } 
                result.append("(= " + newfield);
                result.append(" (update ");
                result.append(oldfield);
                result.append(" (");
                selected.accept(this);
                result.append(") ");
                rhs.accept(this);
                result.append("))");
            } catch (ProverException e) {
                throw new RuntimeException(e);
            }
        } else {
            // FIXME - what might this be
        }
    }
    
    public void visitUnary(JCUnary that) {
        // boolean not (!) encoded as: (not arg)
        // arithmetic negation (-) as: (- 0 arg)   [there is no unary negation]
        switch (that.getTag()) {
            case JCTree.NOT:
                result.append("(not ");
                break;
            case JCTree.NEG:
                result.append("(- 0 ");
                break;
            case JCTree.POS:
                // Nothing special needed
                that.arg.accept(this);
                return;
            case JCTree.COMPL:
            default:
                throw new RuntimeException(new ProverException("Unary operator not implemented for Yices: " + that.getTag()));
        }
        that.arg.accept(this);
        result.append(")");
    }
    
    public void visitBinary(JCBinary that) {
        // encoded as: (op lhs rhs)
        result.append("(");
        switch (that.getTag()) {
            case JCTree.EQ:
                result.append("= ");
                break;
            case JCTree.AND:
                result.append("and ");
                break;
            case JCTree.OR:
                result.append("or ");
                break;
            case JCTree.PLUS:
                result.append("+ ");
                break;
            case JCTree.MINUS:
                result.append("- ");
                break;
            case JCTree.MUL:
                result.append("* ");
                break;
            case JCTree.DIV:
                int tag = that.type.tag;
                if (tag == TypeTags.INT || tag == TypeTags.SHORT || tag == TypeTags.LONG || tag == TypeTags.BYTE)
                    result.append("div ");
                else // float, double
                    result.append("/ ");
                break;
            case JCTree.MOD:
                result.append("mod ");
                break;
            case JCTree.NE:
                result.append("/= ");
                break;
            case JCTree.LE:
                result.append("<= ");
                break;
            case JCTree.LT:
                result.append("< ");
                break;
            case JCTree.GE:
                result.append(">= ");
                break;
            case JCTree.GT:
                result.append("> ");
                break;
            case JCTree.BITAND:
            case JCTree.BITXOR:
            case JCTree.BITOR:
            case JCTree.SL:
            case JCTree.SR:
            case JCTree.USR:
            default:  // FIXME - others: shift, mod, bit operations
                throw new RuntimeException(new ProverException("Binary operator not implemented for Yices: " + that.getTag()));
        }
        that.lhs.accept(this);
        result.append(" ");
        that.rhs.accept(this);
        result.append(")");
    }

    public void visitJmlBinary(JmlBinary that) {
        // encoded as: (op lhs rhs)
        result.append("(");
        if (that.op == JmlToken.IMPLIES) {
            result.append("=> ");
        } else if (that.op == JmlToken.EQUIVALENCE) {
            result.append("= ");
        } else if (that.op == JmlToken.INEQUIVALENCE) {
            result.append("/= ");
        } else if (that.op == JmlToken.REVERSE_IMPLIES) {
            result.append("=> ");
            that.rhs.accept(this);
            result.append(" ");
            that.lhs.accept(this);
            result.append(")");
            return;
        } else if (that.op == JmlToken.SUBTYPE_OF) {    // FIXME - need subtype
            throw new RuntimeException(new ProverException("Binary operator not implemented for Yices: " + that.getTag()));
        } else {
           throw new RuntimeException(new ProverException("Binary operator not implemented for Yices: " + that.getTag()));
        }
        that.lhs.accept(this);
        result.append(" ");
        that.rhs.accept(this);
        result.append(")");
    }
    
    public void visitConditional(JCConditional that) {
        // encoded as:  (ite cond lhs rhs)
        result.append("(ite ");
        that.cond.accept(this);
        result.append(" ");
        that.truepart.accept(this);
        result.append(" ");
        that.falsepart.accept(this);
        result.append(")");
    }
    
    public void visitIndexed(JCArrayAccess that) {
        if (!(that instanceof JmlBBArrayAccess)) {
            throw new RuntimeException(new ProverException("A BasicBlock AST should have JMLBBArrayAccess nodes for array access: " + that.getClass()));
        }
        // FIXME - document
        JCIdent arraysId = ((JmlBBArrayAccess)that).arraysId;
        Type t = that.type;
        String s = BasicBlocker.encodeType(t);
        String arr = arraysId.toString();
        String ty = "array$" + s;
        try {
            if (!p.checkAndDefine(ty)) {
                // Was not already defined
                p.send("(define-type " + ty + ")\n");
                p.eatPrompt();
            }
            if (!p.checkAndDefine(arr)) {
                // Was not already defined
                p.send("(define " + arr + "::(-> " + ty + "  int " + s + "))\n");
                p.eatPrompt();
            }
        } catch (ProverException e) {
            throw new RuntimeException(e);
        }

        result.append("(");
        result.append(arr);
        result.append(" ");
        that.indexed.accept(this);
        result.append(" ");
        that.index.accept(this);
        result.append(")");
    }
    
    public void visitSelect(JCFieldAccess that) {
        if (!(that instanceof JmlBBFieldAccess)) {
            throw new RuntimeException(new ProverException("A BasicBlock AST should have JmlBBFieldAccess nodes for field access: " + that.getClass()));
        }
        // FIXME - document
        JCIdent fieldId = ((JmlBBFieldAccess)that).fieldId;
        Type t = that.type;
        String s = BasicBlocker.encodeType(t);
        try {
            if (!p.checkAndDefine(fieldId.toString())) {
                // Was not already defined
                p.send("(define " + fieldId + "::(-> REF " + s + "))\n");
                p.eatPrompt();
            }
        } catch (ProverException e) {
            throw new RuntimeException(e);
        }

        result.append("(");
        result.append(fieldId);
        result.append(" ");
        that.selected.accept(this);
        result.append(")");
    }
    
    public void visitJmlQuantifiedExpr(JmlQuantifiedExpr that) {
        // Presuming all expression to this point are FORALL - FIXME
        // translates to (forall (name::type ... name::type) expr)
        result.append("(forall (");
        
        do {
            com.sun.tools.javac.util.List<JCExpression> localtypes = that.localtypes.toList();
            String ytype;

            for (Name n: that.names) {
                JCExpression localtype = localtypes.head;
                localtypes = localtypes.tail;
                ytype = p.convertType(localtype.type);

                result.append(n.toString());
                result.append("::");
                result.append(ytype);
                result.append(" ");
            }

            if (that.predicate instanceof JmlQuantifiedExpr) {
                that = (JmlQuantifiedExpr)that.predicate;
            } else break;
        } while (true);
            
        result.append(") ");
        that.predicate.accept(this);
        result.append(")");
    }
    
}
