/*
 * This file is part of the OpenJML project. 
 * Author: David R. Cok
 */
package com.sun.tools.javac.comp;

import static com.sun.tools.javac.code.Kinds.MTH;
import static com.sun.tools.javac.code.Kinds.TYP;
import static com.sun.tools.javac.code.Kinds.kindName;
import static com.sun.tools.javac.code.TypeTag.FORALL;

import org.jmlspecs.annotation.NonNull;
import org.jmlspecs.openjml.JmlTokenKind;
import org.jmlspecs.openjml.JmlTree.JmlVariableDecl;
import org.jmlspecs.openjml.Utils;

import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Scope;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Type.ForAll;
import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;
import com.sun.tools.javac.util.Warner;

/** The Check class is specialized for JML in order to avoid unchecked-cast warnings
 * for uses of casts in JML expressions.  JML checks these logically. Also adjusts
 * warnings on use of old in method specifications.
 * <p>
 * [TODO: Give examples for clarity]
 * @author David R. Cok
 *
 */
public class JmlCheck extends Check {

    /** Creates a new instance - but use instance(), not this constructor, in order to
     * get the unique instance for the current compilation context.
     * @param context the compilation context this instance is for
     */
    protected JmlCheck(@NonNull Context context) {
        super(context);
        this.context = context;
    }
    
    /** Registers a singleton factory for JmlCheck against the checkKey, so that there is
     * just one instance per context.
     * @param context the context in which to register the instance
     */
    public static void preRegister(final Context context) {
        context.put(Check.checkKey, new Context.Factory<Check>() {
            public Check make(Context context) {
                return new JmlCheck(context); // Registers itself on construction
            }
        });
    }
    
    /** Returns the instance for the given context
     * 
     * @param context the context in which we are working
     * @return the non-null instance of JmlCheck for this context
     */
    public static JmlCheck instance(Context context) {
        Check instance = context.get(checkKey); 
        if (instance == null)
            instance = new JmlCheck(context); // Registers itself in the super constructor
        return (JmlCheck)instance; // If the registered instance is only a Check, something is catastrophically wrong
    }
    
    /** Set externally in order to control errors about old variables needing to be static. */
    public boolean staticOldEnv = false;
    
    /** Set by setInJml in order to avoid errors about generic casts.*/
    protected boolean isInJml = false;
    
    /** public method to control the inJml flag */
    public boolean setInJml(Boolean inJml) {
        boolean b = isInJml;
        isInJml = inJml;
        return b;
    }
    
    /** A warning object that issues no warnings.*/
    public static class NoWarningsAtAll extends Warner {
        public void warnUnchecked() {
        }
        public void silentUnchecked() {
        }
    }

    /** Overridden to avoid generic cast warnings in JML.
     */
    @Override
    protected Type checkCastable(DiagnosticPosition pos, Type found, Type req) {
        if (!isInJml) return super.checkCastable(pos,found,req);
        if (types.isCastable(found, req, castWarner(pos, found, req))) {
            return req;
        } else {
            basicHandler.report(pos, diags.fragment("inconvertible.types", found, req));
            return types.createErrorType(found);
        }
    }
    
    /** Overridden to avoid errors about static-ness of old variables in 
     * method specifications.
     */
    @Override
    long checkFlags(DiagnosticPosition pos, long flags, Symbol sym, JCTree tree) {
        JCTree.JCVariableDecl d = (tree instanceof JCTree.JCVariableDecl) ? (JCTree.JCVariableDecl) tree : null;
        if (staticOldEnv) flags &= ~Flags.STATIC;
        long k = super.checkFlags(pos,flags,sym,tree);
        if (staticOldEnv) { k |= Flags.STATIC; }
        if (d != null) {
            boolean isInstance = JmlAttr.instance(context).findMod(d.mods,JmlTokenKind.INSTANCE) != null;
            if (isInstance) k &= ~Flags.STATIC;
        }
        return k;
    }
    
    @Override
    public Type checkType(DiagnosticPosition pos, Type found, Type req) {
        if (found != null && found.getTag() == TypeTag.ARRAY && req.getTag() == TypeTag.ARRAY &&
                found.toString().equals("org.jmlspecs.utils.IJMLTYPE[]") &&
                req.toString().equals("\\TYPE[]")) {
            // FIXME - can we do the above without converting to String
            // We need this for the implementation of JML.typeargs, but
            // does it cause problems elsewhere?
            return req;
        }
        return super.checkType(pos, found, req);
    }
    
    boolean noDuplicateWarn = false;
    DiagnosticPosition duplicateErrorPosition = null;
    void duplicateError(DiagnosticPosition pos, Symbol sym) {
        if (noDuplicateWarn) return;
        super.duplicateError(pos, sym);
    }
    
    void varargsDuplicateError(DiagnosticPosition pos, Symbol sym1, Symbol sym2) {
        if (!noDuplicateWarn) super.varargsDuplicateError(pos, sym1, sym2);
    }
    
    Symbol findClassName(DiagnosticPosition pos, Name name, Scope s) {
        for (Scope.Entry e = s.lookup(name); e.scope == s; e = e.next()) {
            if (e.sym.kind == TYP && e.sym.name != names.error) {
                return e.sym;
            }
        }
        for (Symbol sym = s.owner; sym != null; sym = sym.owner) {
            if (sym.kind == TYP && sym.name == name && sym.name != names.error) {
                return sym;
            }
        }
        return null;
    }


}
