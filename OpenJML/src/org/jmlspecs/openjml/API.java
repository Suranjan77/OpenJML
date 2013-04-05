/*
 * This file is part of the OpenJML project. 
 * Author: David R. Cok
 */
package org.jmlspecs.openjml;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;

import javax.tools.DiagnosticListener;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;

import org.jmlspecs.annotation.NonNull;
import org.jmlspecs.annotation.Nullable;
import org.jmlspecs.annotation.Pure;
import org.jmlspecs.openjml.JmlSpecs.FieldSpecs;
import org.jmlspecs.openjml.JmlSpecs.TypeSpecs;
import org.jmlspecs.openjml.JmlTree.JmlClassDecl;
import org.jmlspecs.openjml.JmlTree.JmlCompilationUnit;
import org.jmlspecs.openjml.JmlTree.JmlMethodDecl;
import org.jmlspecs.openjml.JmlTree.JmlMethodInvocation;
import org.jmlspecs.openjml.JmlTree.JmlMethodSpecs;
import org.jmlspecs.openjml.JmlTree.JmlVariableDecl;
import org.jmlspecs.openjml.Main.JmlCanceledException;
import org.jmlspecs.openjml.esc.BasicBlocker;
import org.jmlspecs.openjml.esc.BasicProgram;
import org.jmlspecs.openjml.esc.JmlEsc;
import org.jmlspecs.openjml.proverinterface.IProver;
import org.jmlspecs.openjml.proverinterface.IProverResult;
import org.jmlspecs.openjml.proverinterface.IProverResult.ICounterexample;
import org.smtlib.IExpr;

import com.sun.tools.javac.code.Scope;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Symbol.PackageSymbol;
import com.sun.tools.javac.code.Symbol.TypeSymbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.comp.Enter;
import com.sun.tools.javac.comp.JmlAttr;
import com.sun.tools.javac.comp.JmlEnter;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.main.JavaCompiler.CompileState;
import com.sun.tools.javac.parser.JmlParser;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCStatement;
import com.sun.tools.javac.tree.Pretty;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;
import com.sun.tools.javac.util.Options;
import com.sun.tools.javac.util.Position;

// FIXME - rewrite using interfaces wherever possible?
// FIXME - include parsing of expressions and methods
// FIXME - include information about walking trees

/** This class is a wrapper and publicly published API for the OpenJML tool 
 * functionality.  In principle, any external programmatic interaction with
 * openjml would go through methods in this class.  [In practice some internal
 * classes are exposed as well.]
 * <P>
 * The class is used as follows.  The user creates a new API object and makes
 * calls to it; each distinct API object encapsulates a completely separate
 * compilation context.  What would ordinarily be command-line options are
 * specified on creation of the API object; for the most part they are not
 * changeable after the compilation context has been created (where change is
 * allowed, a method call is provided).
 * <P>
 * There are also static methods, named execute, that are public entry points to the
 * various tools (jml checker, jmldoc, ...) that openjml provides.  It performs 
 * a one-time processing of files, without making the classes and ASTs available,
 * just like a command-line execution would do.
 * <P>
 * The public API consists of the methods with public visibility in this 
 * compilation unit.
 *
 * @author David Cok
 */
public class API implements IAPI {
    
    /** The encapsulated org.jmlspecs.openjml.Main object */
    public Main main = null; // FIXME - change this back to protected
    //@ initially main != null;
    
    protected DiagnosticListener<? extends JavaFileObject> diagListener = null;
    
//    /** The encapsulated compilation context; this can change when various
//     * actions are execu5ted, so do not cache it elsewhere */
//    // protected invariant main != null ==> main.context == context;
//    protected Context context = null;
    
    /** Creates a new compilation context, initialized with given command-line options.
     * Output is sent to System.out; no diagnostic listener is used (so errors
     * and warnings are sent to System.out). 
     * @param args the command-line options and initial set of files with which
     * to load the compilation environment
     */
    //@ ensures isOpen;
    public API(@NonNull String ... args) throws IOException {
        this(null,null,null,args);
    }
    
    /** Creates an API that will send informational output to the
     * given PrintWriter and diagnostic output to the given listener.
     * @param writer destination of non-diagnostic output (null means System.out)
     * @param listener
     */
    //@ ensures isOpen;
    public API(@Nullable PrintWriter writer, 
            @Nullable DiagnosticListener<? extends JavaFileObject> listener, 
            @Nullable Options options,
            @NonNull String... args) throws java.io.IOException {
        if (writer == null) {
            writer = new PrintWriter(System.out);
        }
        main = new Main(Strings.applicationName,writer,listener,options,args);
        this.diagListener = listener;
        Log.instance(main.context).multipleErrors = true;
    }
    
    /* (non-Javadoc)
     * @see org.jmlspecs.openjml.IAPI#context()
     */
    @Override
    //@ ensures \result == context;
    @Pure
    public @Nullable Context context() {
        return main == null ? null : main.context;
    }
    
    /* (non-Javadoc)
     * @see org.jmlspecs.openjml.IAPI#setProgressReporter(org.jmlspecs.openjml.Main.IProgressReporter)
     */
    @Override
    public void setProgressListener(@Nullable Main.IProgressListener p) {
        if (main.progressDelegate != null) {
            p.setContext(main.context);
            main.progressDelegate.setDelegate(p);
        }
    }
    
    /** Returns the string describing the version of OpenJML that is this
     * set of classes.
     * @return the version of this instance of OpenJML
     */
    @Override
    public @NonNull String version() {
        return JavaCompiler.version();
    }
    
    /** Adds options to the current context
     */
    // FIXME _ get error return? 
    @Override
    public void addOptions(@NonNull Options options, @NonNull String ... args) {
        main.initializeOptions(options == null && context() != null ? Options.instance(context()) : options, args);
    }
    
    /** Executes the command-line version of OpenJML, returning the exit code.
     * @param args the command-line arguments
     * @return the exit code (0 is success; other values are various kinds of errors)
     */
    @Override
    public int execute(@NonNull Options options, @NonNull String ... args) {
        int ret = main.executeNS(main.out(), diagListener, options, args);
        return ret;
    }
    
    /** Executes the command-line version of OpenJML, returning the exit code.
     * @param writer the PrintWriter to receive general output
     * @param diagListener a listener to receive reports of diagnostics (e.g. parse or typechecking errors and warnings)
     * @param args the command-line arguments
     * @return the exit code (0 is success; other values are various kinds of errors)
     */
    @Override
    public int execute(@NonNull PrintWriter writer, @Nullable DiagnosticListener<JavaFileObject> diagListener, @Nullable Options options, @NonNull String ... args) {
        int ret = main.executeNS(writer,diagListener,options,args);
        return ret;
    }
    
    /** Executes the jmldoc tool on the given command-line arguments. */
    public int jmldoc(@NonNull String... args) {
        return org.jmlspecs.openjml.jmldoc.Main.execute(args);
    }
    
    /* (non-Javadoc)
     * @see org.jmlspecs.openjml.IAPI#enterAndCheck(org.jmlspecs.openjml.JmlTree.JmlCompilationUnit)
     */
    @Override
    public int typecheck(@NonNull JmlCompilationUnit... trees) throws IOException {
        if (trees == null) {
            throw new IllegalArgumentException("Argument 'trees' of API.enterAndCheck is null");
        }
        ListBuffer<JCCompilationUnit> list = new ListBuffer<JCCompilationUnit>();
        list.appendArray(trees);
        return typecheck(list.toList());
    }
    
    /* (non-Javadoc)
     * @see org.jmlspecs.openjml.IAPI#enterAndCheck(java.util.Collection)
     */
    //@ requires isOpen;
    //@ ensures isOpen;
    @Override
    public int typecheck(@NonNull Collection<? extends JmlCompilationUnit> trees) throws java.io.IOException {
        if (main.context == null) {
            throw new NullPointerException("There is no valid compilation context");
        }
        if (trees == null) {
            throw new IllegalArgumentException("Argument 'trees' of API.enterAndCheck is null");
        }
        ListBuffer<JCCompilationUnit> list = new ListBuffer<JCCompilationUnit>();
        list.addAll(trees);
        return typecheck(list.toList());
    }
    
    /** Enters and typechecks the provided compilation unit ASTs.  The elements
     * of the list should all be JmlCompilationUnit nodes.
     * @param list a list of the ASTs to be checked
     * @return the number of errors encountered
     * @throws IOException
     */
    @Override
    public int typecheck(@NonNull List<JCCompilationUnit> list) throws IOException {
        JmlCompiler jcomp = (JmlCompiler)JmlCompiler.instance(main.context);
        JmlTree.Maker maker = JmlTree.Maker.instance(main.context);
        for (JCCompilationUnit jcu: list) {
            for (JCTree t: jcu.defs) {
                if (t instanceof JmlClassDecl && ((JmlClassDecl)t).typeSpecs == null) JmlParser.filterTypeBodyDeclarations((JmlClassDecl)t,main.context,maker);
            }
            for (JmlClassDecl t: ((JmlCompilationUnit)jcu).parsedTopLevelModelTypes) {
                if (t.typeSpecs == null) JmlParser.filterTypeBodyDeclarations(t,main.context, maker);
            }
        }

        JavaCompiler dc =
            jcomp.processAnnotations(
                    jcomp.enterTrees(jcomp.stopIfError(CompileState.PARSE, list)),
                com.sun.tools.javac.util.List.<String>nil());
        dc.flow(dc.attribute(dc.todo));
        int errs = Log.instance(main.context).nerrors;
        Log.instance(main.context).nerrors = 0;
        return errs;
    }

    /* (non-Javadoc)
     * @see org.jmlspecs.openjml.IAPI#parseFiles(java.io.File)
     */
    //@ requires \nonnullelements(files);
    //@ requires isOpen;
    //@ ensures isOpen;
    //@ ensures files.length == \result.size();
    //@ ensures (* output elements are non-null *);
    @Override
    public @NonNull java.util.List<JmlCompilationUnit> parseFiles(@NonNull File... files) {
        JmlCompiler c = (JmlCompiler)JmlCompiler.instance(main.context);
        c.inSequence = false;
        Iterable<? extends JavaFileObject> fobjects = ((JavacFileManager)main.context.get(JavaFileManager.class)).getJavaFileObjects(files);
        ArrayList<JmlCompilationUnit> trees = new ArrayList<JmlCompilationUnit>();
        for (JavaFileObject fileObject : fobjects)
            trees.add((JmlCompilationUnit)c.parse(fileObject));
        return trees;
    }
    
    /* (non-Javadoc)
     * @see org.jmlspecs.openjml.IAPI#parseFiles(String)
     */
    //@ requires \nonnullelements(files);
    //@ requires isOpen;
    //@ ensures isOpen;
    //@ ensures files.length == \result.size();
    //@ ensures (* output elements are non-null *);
    @Override
    public @NonNull java.util.List<JmlCompilationUnit> parseFiles(@NonNull String... filenames) {
        File[] files = new File[filenames.length];
        for (int i=0; i<filenames.length; i++) {
            files[i] = new File(filenames[i]);
        }
        return parseFiles(files);
    }
    
    /* (non-Javadoc)
     * @see org.jmlspecs.openjml.IAPI#parseFiles(javax.tools.JavaFileObject)
     */
    //@ requires \nonnullelements(inputs);
    //@ requires isOpen;
    //@ ensures isOpen;
    //@ ensures inputs.length == \result.size();
    //@ ensures (* output elements are non-null *);
    @Override
    public @NonNull java.util.List<JmlCompilationUnit> parseFiles(@NonNull JavaFileObject... inputs) {
        JmlCompiler c = (JmlCompiler)JmlCompiler.instance(main.context);
        c.inSequence = false;
        ArrayList<JmlCompilationUnit> trees = new ArrayList<JmlCompilationUnit>();
        for (JavaFileObject fileObject : inputs)
            trees.add((JmlCompilationUnit)c.parse(fileObject));
        return trees;
    }
    
    /* (non-Javadoc)
     * @see org.jmlspecs.openjml.IAPI#parseFiles(java.util.Collection)
     */
    //@ requires \nonnullelements(inputs);
    //@ requires isOpen;
    //@ ensures isOpen;
    //@ ensures inputs.length == \result.size();
    //@ ensures (* output elements are non-null *);
    @Override
    public @NonNull java.util.List<JmlCompilationUnit> parseFiles(@NonNull Collection<? extends JavaFileObject> inputs) {
        JmlCompiler c = (JmlCompiler)JmlCompiler.instance(main.context);
        c.inSequence = false;
        ArrayList<JmlCompilationUnit> trees = new ArrayList<JmlCompilationUnit>();
        for (JavaFileObject fileObject : inputs)
            trees.add((JmlCompilationUnit)c.parse(fileObject));
        return trees;
    }
    
    /* (non-Javadoc)
     * @see org.jmlspecs.openjml.IAPI#parseSingleFile(java.io.File)
     */
    //@ requires isOpen;
    //@ ensures isOpen;
    @Override
    public @NonNull JmlCompilationUnit parseSingleFile(@NonNull String filename) {
        return parseSingleFile(makeJFOfromFilename(filename));
    }
    
    
    /* (non-Javadoc)
     * @see org.jmlspecs.openjml.IAPI#parseSingleFile(java.io.File)
     */
    //@ requires isOpen;
    //@ ensures isOpen;
    @Override
    public @NonNull JmlCompilationUnit parseSingleFile(@NonNull JavaFileObject jfo) {
        JmlCompiler c = (JmlCompiler)JmlCompiler.instance(main.context);
        c.inSequence = true; // Don't look for specs
        JmlCompilationUnit specscu = (JmlCompilationUnit)c.parse(jfo);
        c.inSequence = false;
        return specscu;
    }

    @Override public @Nullable
    JavaFileObject findSpecs(JmlCompilationUnit jmlcu) {
        return ((JmlCompiler)JmlCompiler.instance(main.context)).findSpecs(jmlcu,true);
    }


    @Override
    public void attachSpecs(JmlCompilationUnit javaSource, @Nullable JmlCompilationUnit specsSource) {
        javaSource.specsCompilationUnit = specsSource;
    }
    
    /* (non-Javadoc)
     * @see org.jmlspecs.openjml.IAPI#parseString(java.lang.String, java.lang.String)
     */
    // FIXME - resolve whether the package name must be present
    // TODO: Would like to automatically set the filename, but can't since the
    // JavaFileObject has to be created before parsing and it is immutable
    //@ requires name.length() > 0;
    //@ requires isOpen;
    //@ ensures isOpen;
    @Override
    public @NonNull JmlCompilationUnit parseString(@NonNull String name, @NonNull String content) throws Exception {
        if (name == null || name.length() == 0) throw new IllegalArgumentException();
        JmlCompiler c = (JmlCompiler)JmlCompiler.instance(main.context);
        JavaFileObject file = makeJFOfromString(name,content);
        c.inSequence = true;  // true so that no searching for spec files happens
        Iterable<? extends JavaFileObject> fobjects = List.<JavaFileObject>of(file);
        JmlCompilationUnit jcu = ((JmlCompilationUnit)c.parse(fobjects.iterator().next()));
        return jcu;
    }
    
    /* (non-Javadoc)
     * @see org.jmlspecs.openjml.IAPI#parseExpression(java.lang.CharSequence, boolean)
     */
    @Override
    public JCExpression parseExpression(CharSequence text,boolean isJML) {
        JmlCompiler.instance(main.context);
        JmlParser p = ((com.sun.tools.javac.parser.JmlFactory)com.sun.tools.javac.parser.JmlFactory.instance(main.context)).newParser(text,true,true,true,isJML);
        return p.parseExpression();
    }
    
    /* (non-Javadoc)
     * @see org.jmlspecs.openjml.IAPI#parseStatement(java.lang.CharSequence, boolean)
     */
    @Override
    public JCStatement parseStatement(CharSequence text,boolean isJML) {
        JmlCompiler.instance(main.context);
        JmlParser p = ((com.sun.tools.javac.parser.JmlFactory)com.sun.tools.javac.parser.JmlFactory.instance(main.context)).newParser(text,true,true,true,isJML);
        return p.parseStatement();
    }
    
    /* (non-Javadoc)
     * @see org.jmlspecs.openjml.IAPI#parseAndCheck(java.io.File)
     */
    //@ requires isOpen;
    //@ ensures isOpen;
    @Override
    public void parseAndCheck(File... files) throws java.io.IOException {
        JmlCompiler c = (JmlCompiler)JmlCompiler.instance(main.context);
        if (!main.setupOptions()) throw new JmlCanceledException("");
        c.inSequence = false;
        Iterable<? extends JavaFileObject> sourceFileObjects = ((JavacFileManager)main.context.get(JavaFileManager.class)).getJavaFileObjects(files);
        ListBuffer<JavaFileObject> list = ListBuffer.<JavaFileObject>lb();
        for (JavaFileObject jfo : sourceFileObjects) list.append(jfo);
        c.processAnnotations(c.enterTrees(c.stopIfError(CompileState.PARSE,c.parseFiles(list.toList()))),
                main.classnames.toList());
        c.flow(c.attribute(c.todo));
    }
    
    // TODO: need an easier way to find out if there are errors from parseAndCheck or enterAndCheck
    // TODO: do we need parseAndCheck for JavaFileObject arguments; for Collection arguments?
    
    /* (non-Javadoc)
     * @see org.jmlspecs.openjml.IAPI#getPackageSymbol(java.lang.String)
     */
    //@ requires isOpen;
    //@ ensures isOpen;
    @Override
    public @Nullable PackageSymbol getPackageSymbol(@NonNull String qualifiedName) {
        Name n = Names.instance(main.context).fromString(qualifiedName);
        return Symtab.instance(main.context).packages.get(n);
    }

    /* (non-Javadoc)
     * @see org.jmlspecs.openjml.IAPI#getClassSymbol(java.lang.String)
     */
    //@ requires isOpen;
    //@ ensures isOpen;
    @Override
    public @Nullable ClassSymbol getClassSymbol(@NonNull String qualifiedName) {
        Name n = Names.instance(main.context).fromString(qualifiedName);
        return Symtab.instance(main.context).classes.get(n);
    }
    
    /* (non-Javadoc)
     * @see org.jmlspecs.openjml.IAPI#getClassSymbol(com.sun.tools.javac.code.Symbol.ClassSymbol, java.lang.String)
     */
    @Override
    public @Nullable ClassSymbol getClassSymbol(@NonNull ClassSymbol csym, @NonNull String name) {
        Scope.Entry e = csym.members().lookup(Names.instance(main.context).fromString(name));
        if (e == null || e.sym == null) return null;
        while (e.sym != null && e.sym.owner == csym) {
            if (e.sym instanceof ClassSymbol) return (ClassSymbol)e.sym;
            e = e.next();
        }
        return null;
    }
    
    /* (non-Javadoc)
     * @see org.jmlspecs.openjml.IAPI#getMethodSymbol(com.sun.tools.javac.code.Symbol.ClassSymbol, java.lang.String)
     */
    //@ requires isOpen;
    //@ ensures isOpen;
    @Override
    public @Nullable MethodSymbol getMethodSymbol(@NonNull ClassSymbol csym, @NonNull String name) {
        Scope.Entry e = csym.members().lookup(Names.instance(main.context).fromString(name));
        if (e == null || e.sym == null) return null;
        while (e.sym != null && e.sym.owner == csym) {
            if (e.sym instanceof MethodSymbol) return (MethodSymbol)e.sym;
            e = e.next();
        }
        return null;
    } // FIXME - need a way to handle multiple methods with the same name
    
    /* (non-Javadoc)
     * @see org.jmlspecs.openjml.IAPI#getVarSymbol(com.sun.tools.javac.code.Symbol.ClassSymbol, java.lang.String)
     */
    @Override
    public @Nullable VarSymbol getVarSymbol(@NonNull ClassSymbol csym, @NonNull String name) {
        Scope.Entry e = csym.members().lookup(Names.instance(main.context).fromString(name));
        if (e == null || e.sym == null) return null;
        while (e.sym != null && e.sym.owner == csym) {
            if (e.sym instanceof VarSymbol) return (VarSymbol)e.sym;
            e = e.next();
        }
        return null;
    }
    
    /* (non-Javadoc)
     * @see org.jmlspecs.openjml.IAPI#getSymbol(org.jmlspecs.openjml.JmlTree.JmlClassDecl)
     */
    @Override
    public @Nullable ClassSymbol getSymbol(@NonNull JmlClassDecl decl) {
        return decl.sym;
    }
    
    /* (non-Javadoc)
     * @see org.jmlspecs.openjml.IAPI#getSymbol(org.jmlspecs.openjml.JmlTree.JmlMethodDecl)
     */
    @Override
    public @Nullable MethodSymbol getSymbol(@NonNull JmlMethodDecl decl) {
        return decl.sym;
    }
    
    /* (non-Javadoc)
     * @see org.jmlspecs.openjml.IAPI#getSymbol(org.jmlspecs.openjml.JmlTree.JmlVariableDecl)
     */
    @Override
    public @Nullable VarSymbol getSymbol(@NonNull JmlVariableDecl decl) {
        return decl.sym;
    }
    
    /* (non-Javadoc)
     * @see org.jmlspecs.openjml.IAPI#getClassDecl(java.lang.String)
     */
    //@ requires isOpen;
    //@ ensures isOpen;
    @Override
    public @NonNull JmlClassDecl getClassDecl(@NonNull String qualifiedName) {
        return getJavaDecl(getClassSymbol(qualifiedName));
    }
        
    /* (non-Javadoc)
     * @see org.jmlspecs.openjml.IAPI#getJavaDecl(com.sun.tools.javac.code.Symbol.ClassSymbol)
     */
    //@ requires isOpen;
    //@ ensures isOpen;
    @Override
    public JmlClassDecl getJavaDecl(ClassSymbol csym) {
        JCTree tree = JmlEnter.instance(main.context).getClassEnv(csym).tree;
        return (JmlClassDecl)tree;
    }
    
    /* (non-Javadoc)
     * @see org.jmlspecs.openjml.IAPI#getJavaDecl(com.sun.tools.javac.code.Symbol.MethodSymbol)
     */
    //@ requires isOpen;
    //@ ensures isOpen;
    @Override
    public @Nullable JmlMethodDecl getJavaDecl(MethodSymbol msym) {
        JmlClassDecl cdecl = getJavaDecl((ClassSymbol)msym.owner);
        for (JCTree t: cdecl.defs) {
            if (t instanceof JmlMethodDecl && ((JmlMethodDecl)t).sym == msym) {
                return (JmlMethodDecl)t;
            }
        }
        return null;
    }
    
    /* (non-Javadoc)
     * @see org.jmlspecs.openjml.IAPI#getJavaDecl(com.sun.tools.javac.code.Symbol.VarSymbol)
     */
    //@ requires isOpen;
    //@ ensures isOpen;
    @Override
    public @Nullable JmlVariableDecl getJavaDecl(VarSymbol vsym) {
        JmlClassDecl cdecl = getJavaDecl((ClassSymbol)vsym.owner);
        for (JCTree t: cdecl.defs) {
            if (t instanceof JmlVariableDecl && ((JmlVariableDecl)t).sym == vsym) {
                return (JmlVariableDecl)t;
            }
        }
        return null;
    }
    
    /* (non-Javadoc)
     * @see org.jmlspecs.openjml.IAPI#setOption(java.lang.String, java.lang.String)
     */
    //@ requires isOpen;
    //@ ensures isOpen;
    @Override
    public boolean setOption(String name, String value) {
        Options.instance(main.context).put(name,value);
        return main.setupOptions();
    }

    /* (non-Javadoc)
     * @see org.jmlspecs.openjml.IAPI#setOption(java.lang.String)
     */
    //@ requires isOpen;
    //@ ensures isOpen;
    @Override
    public boolean setOption(String name) {
        Options.instance(main.context).put(name,"");
        return main.setupOptions();
    }
    
    // FIXME - not sure about remove; do these option handling routines work for Java and JML options?
    
    /* (non-Javadoc)
     * @see org.jmlspecs.openjml.IAPI#removeOption(java.lang.String)
     */
    //@ requires isOpen;
    //@ ensures isOpen;
    @Override
    public void removeOption(String name) {
        Options.instance(main.context).remove(name);
    }
    
    /* (non-Javadoc)
     * @see org.jmlspecs.openjml.IAPI#getOption(java.lang.String)
     */
    //@ requires isOpen;
    //@ ensures isOpen;
    @Override
    public @Nullable String getOption(String name) {
        return Options.instance(main.context).get(name);
    }
    
    
    /** A cached object to search parse trees (not thread safe since it is static) */
    static protected Finder finder = new Finder();
    
    /** A class that searches parse trees for nodes with a given position range */
    protected static class Finder extends JmlTreeScanner {
        /** Find the node within the given tree that encompasses the given
         * start and end position.
         * @param tree the root of the tree
         * @param startpos the starting char position (from begining of file)
         * @param endpos the ending position
         * @return the best matching node
         */
        public JCTree find(JmlCompilationUnit tree, int startpos, int endpos) {
            this.startpos = startpos;
            this.endpos = endpos;
            this.tree = tree;
            this.scanMode = AST_JML_MODE;
            scan(tree);
            return found;
        }
        
        int startpos;
        int endpos;
        JmlCompilationUnit tree;
        JCTree found = null;
        JmlMethodDecl parentMethod = null;
        
        public void scan(JCTree node) {
            if (node == null) return;
            int sp = node.getStartPosition();
            if (sp == Position.NOPOS && node instanceof JmlMethodInvocation) sp = ((JmlMethodInvocation)node).pos;
            int ep = node.getEndPosition(tree.endPositions);
            // Do this specially because the range of a MethodDecl node does not include the specs
            if (node instanceof JmlMethodDecl) {
                JCTree ftree = found;
                super.scan(node);
                if (found != ftree) parentMethod = (JmlMethodDecl)node;
            } else if (node instanceof JmlVariableDecl) {
                JCTree ftree = found;
                super.scan(node);
                if (found == ftree && sp <= startpos && endpos <= ep) {
                    found = node;
                }
            } else if (sp <= startpos && endpos <= ep) {
                found = node;
                //System.out.println(startpos + " " + endpos + " " + sp + " " + ep + " " + node.getClass());
                super.scan(node);  // Call this to scan child nodes
            } // If the desired range is not within the node's range, we
              // don't even process the children
        }
    }

    /** Finds a node in the AST that minimally includes the given character position range
     * @param tree the compilation unit to search (must have endPositions set)
     * @param startpos the starting character position
     * @param endpos the ending character position
     * @return the node identified
     */
    protected JCTree findNode(JmlCompilationUnit tree, int startpos, int endpos) {
        return finder.find(tree,startpos,endpos);
    }
    
    /** The method on which ESC was run most recently */
    protected MethodSymbol mostRecentProofMethod = null; // FIXME - does this need to be static?
    
    protected BasicProgram mostRecentProgram = null; // FIXME - document
    
    protected IProver mostRecentProver = null; // FIXME
    
    // TODO: document
    /* (non-Javadoc)
     * @see org.jmlspecs.openjml.IAPI#getCEValue(int, int, java.lang.String, java.lang.String)
     */
    @Override
    public String getCEValue(int pos, int end, String text, String fileLocation) {
        //String msg = "Seeking character range " + pos + " to " + end + " in " + fileLocation.toString()
        //    + "\n";
        fileLocation = fileLocation.replace('\\','/');
        if (mostRecentProofMethod == null) {
            return "No proof in which to evaluate the selection";
        }
        JmlCompilationUnit tree = (JmlCompilationUnit)Enter.instance(main.context).getEnv((TypeSymbol)mostRecentProofMethod.owner).toplevel;
        if (!tree.sourcefile.getName().replace('\\','/').equals(fileLocation)) {
            //System.out.println("Did not match " + tree.sourcefile.toString());
            boolean found = false;
            {
                JmlCompilationUnit stree = tree.specsCompilationUnit;
                if (stree.sourcefile.getName().replace('\\','/').equals(fileLocation)) {
                    tree = stree;
                    found = true;
                }
                //System.out.println("Did not match " + stree.sourcefile.toString());
            }
            if (!found) {
                // TODO _ make a proper error to the right destination
                System.out.println("No Match for " + tree.specsCompilationUnit.sourcefile.getName());
            }
        }
        JCTree node = findNode(tree,pos,end);
        JmlMethodDecl parentMethod = finder.parentMethod;
        if (parentMethod.sym != mostRecentProofMethod) {
            return "Selected text is not within the method of the most recent proof (which is " + mostRecentProofMethod + ")";
        }
        String out;
        if (node instanceof JmlVariableDecl) {
            // This happens when we have selected a method parameter or the variable within a declaration
            // continue
            out = "Found declaration: " + ((JmlVariableDecl)node).name.toString() + "\n";
        } else if (!(node instanceof JCTree.JCExpression)) {
            return "Selected text is not an expression (" + node.getClass() + "): " + text;
        } else {
            out = "Found expression node: " + node.toString() + "\n";
        }
        
        if (!JmlOption.isOption(context(), JmlOption.CUSTOM)) {
            if (JmlEsc.mostRecentCEMap != null) {
                String value = JmlEsc.mostRecentCEMap.get(node);
                out = out + "Value " + node.type + " : " + value;
                return out;
            }
            return "No counterexample information available";
        }
        
        // OLD style
        
        ICounterexample ce = getProofResult(mostRecentProofMethod).counterexample();
        if (ce == null) {
        	out = "There is no counterexample information";
        } else {
        	JCTree logical = mostRecentProgram.toLogicalForm.get(node);
        	if (logical == null) {
        		out = out + "No corresponding logical form";
        	} else {
        		//out = out + "Logical form: " + logical.toString() + "\n";
        		String value = ce.get(logical);
        		if (value == null) value = ce.get(logical.toString());
        		if (value == null) {
        			out = out + "No value found";
        		} else {
        			out = out + "Value: " + value;
        		}
        	}
        }
        return out;
    }
    
    /* (non-Javadoc)
     * @see org.jmlspecs.openjml.IAPI#doESC(com.sun.tools.javac.code.Symbol.MethodSymbol)
     */
    //@ requires isOpen;
    //@ ensures isOpen;
    @Override
    public IProverResult doESC(MethodSymbol msym) {
        JmlMethodDecl decl = getJavaDecl(msym);
        JmlEsc esc = JmlEsc.instance(main.context);
        esc.check(decl);
        mostRecentProofMethod = msym;
        mostRecentProgram = esc.mostRecentProgram;
        mostRecentProver = esc.mostRecentProver;
        
        return getProofResult(msym);
    }
    
    /* (non-Javadoc)
     * @see org.jmlspecs.openjml.IAPI#doESC(com.sun.tools.javac.code.Symbol.ClassSymbol)
     */
    //@ requires isOpen;
    //@ ensures isOpen;
    @Override
    public void doESC(ClassSymbol csym) {
        mostRecentProofMethod = null;
        JmlClassDecl decl = getJavaDecl(csym);
        JmlEsc.instance(main.context).check(decl);
    }
    
    /* (non-Javadoc)
     * @see org.jmlspecs.openjml.IAPI#getProofResult(com.sun.tools.javac.code.Symbol.MethodSymbol)
     */
    //@ requires isOpen;
    //@ ensures isOpen;
    @Override
    public @Nullable IProverResult getProofResult(MethodSymbol msym) {
        return JmlEsc.instance(main.context).proverResults.get(msym);
    }
    
    // TODO - is this really to be public?
    /** Returns the basic block program for the given method
     * @param msym the method in question
     * @return the basic block program, (somewhat) pretty printed
     */
    //@ requires isOpen;
    //@ ensures isOpen;
    public String getBasicBlockProgram(MethodSymbol msym) {
        JmlMethodDecl tree = getJavaDecl(msym);
        JmlClassDecl cdecl = getJavaDecl((ClassSymbol)msym.owner);
        BasicProgram program = BasicBlocker.convertToBasicBlocks(main.context, tree, JmlSpecs.instance(main.context).getSpecs(msym).cases.deSugared, cdecl);
        return "BASIC BLOCK PROGRAM FOR " + msym.owner.getQualifiedName() + "." + msym.toString() + "\n\n" + program.toString();
    }
    
    // TODO - why is this public?
    /** Adds the given class and all its supertypes (recursively) to the given list.
     * @param csym the class or interface in question
     * @param list the list to add to
     */
    //@ requires isOpen;
    //@ ensures isOpen;
    public void collectSuperTypes(@NonNull ClassSymbol csym, java.util.List<ClassSymbol> list) {
        Type tt = csym.getSuperclass();
        if (tt != null && tt != Type.noType) {
            ClassSymbol s = (ClassSymbol)tt.tsym; // FIXME - when is a TypeSymbol not a class Symbol - parameterization?
            collectSuperTypes(s,list);
        }
        for (Type t: csym.getInterfaces()) {
            ClassSymbol c = (ClassSymbol)t.tsym;
            if (!list.contains(c)) {
                collectSuperTypes(c,list);  // c and any super interfaces are added here
            }
        }
        list.add(csym);
    }
    
    // TODO - why is this public?
    /** Adds the method and all the methods it overrides (in classes or interfaces)
     * to the given list
     * @param msym the method in question
     * @param list the list to add the methods to
     */
    //@ requires isOpen;
    //@ ensures isOpen;
    public void collectSuperMethods(@NonNull MethodSymbol msym, java.util.List<MethodSymbol> list) {
        java.util.List<ClassSymbol> clist = new ArrayList<ClassSymbol>();
        collectSuperTypes(msym.enclClass(),clist);
        for (ClassSymbol c: clist) {
            // find a method matching msym in c
            Scope.Entry e = c.members().lookup(msym.getSimpleName());
            while (e != null) {
                Symbol sym = e.sym;
                e = e.sibling;
                if (!(sym instanceof MethodSymbol)) continue;
                MethodSymbol mmsym = (MethodSymbol)sym;
                if (!msym.overrides(mmsym,msym.enclClass(),Types.instance(main.context),false)) continue;
                list.add(mmsym);
                break;
            }
        }
    }
    
    /* (non-Javadoc)
     * @see org.jmlspecs.openjml.IAPI#getSpecs(com.sun.tools.javac.code.Symbol.ClassSymbol)
     */
    //@ requires isOpen;
    //@ ensures isOpen;
    @Override
    public @NonNull TypeSpecs getSpecs(@NonNull ClassSymbol sym) {
        return JmlSpecs.instance(main.context).get(sym);
    }
    
    /* (non-Javadoc)
     * @see org.jmlspecs.openjml.IAPI#getAllSpecs(com.sun.tools.javac.code.Symbol.ClassSymbol)
     */
    //@ requires isOpen;
    //@ ensures isOpen;
    @Override
    public java.util.List<TypeSpecs> getAllSpecs(@NonNull ClassSymbol sym) {
        java.util.List<ClassSymbol> list = new ArrayList<ClassSymbol>();
        collectSuperTypes(sym,list);
        JmlSpecs specs = JmlSpecs.instance(main.context);
        java.util.List<TypeSpecs> tslist = new ArrayList<TypeSpecs>();
        for (ClassSymbol c: list) tslist.add(specs.get(c));
        return tslist;
    }

    /* (non-Javadoc)
     * @see org.jmlspecs.openjml.IAPI#getSpecs(com.sun.tools.javac.code.Symbol.MethodSymbol)
     */
    //@ requires isOpen;
    //@ ensures isOpen;
    @Override
    public @NonNull JmlSpecs.MethodSpecs getSpecs(@NonNull MethodSymbol sym) {
        return JmlSpecs.instance(main.context).getSpecs(sym);
    }
    
    /* (non-Javadoc)
     * @see org.jmlspecs.openjml.IAPI#getAllSpecs(com.sun.tools.javac.code.Symbol.MethodSymbol)
     */
    //@ requires isOpen;
    //@ ensures isOpen;
    @Override
    public java.util.List<JmlSpecs.MethodSpecs> getAllSpecs(@NonNull MethodSymbol msym) {
        java.util.List<JmlSpecs.MethodSpecs> tslist = new ArrayList<JmlSpecs.MethodSpecs>();
        if (msym.isStatic() || msym.isConstructor()) {
            tslist.add(getSpecs(msym));
            return tslist;
        }
        java.util.List<MethodSymbol> list = new ArrayList<MethodSymbol>();
        collectSuperMethods(msym,list);
        JmlSpecs specs = JmlSpecs.instance(main.context);
        for (MethodSymbol c: list) tslist.add(specs.getSpecs(c));
        return tslist;
    }
    
    // FIXME - should this be inherited specs; what about parameter name renaming?
    // FIXME - is this public?
    /* (non-Javadoc)
     * @see org.jmlspecs.openjml.IAPI#getDenestedSpecs(com.sun.tools.javac.code.Symbol.MethodSymbol)
     */
    //@ requires isOpen;
    //@ ensures isOpen;
    @Override
    public @NonNull JmlMethodSpecs getDenestedSpecs(@NonNull MethodSymbol sym) {
        return JmlSpecs.instance(main.context).getDenestedSpecs(sym);
    }
    
    /* (non-Javadoc)
     * @see org.jmlspecs.openjml.IAPI#getSpecs(com.sun.tools.javac.code.Symbol.VarSymbol)
     */
    //@ requires isOpen;
    //@ ensures isOpen;
    @Override
    public @NonNull FieldSpecs getSpecs(@NonNull VarSymbol sym) {
        return JmlSpecs.instance(main.context).getSpecs(sym);
    }
    
    /* (non-Javadoc)
     * @see org.jmlspecs.openjml.IAPI#nodeFactory()
     */
    //@ requires isOpen;
    //@ ensures isOpen;
    @Override
    public @NonNull JmlTree.Maker nodeFactory() {
        JmlAttr.instance(main.context);  // Avoids circular tool registration problems
        return JmlTree.Maker.instance(main.context);
    }
    
    
    /* (non-Javadoc)
     * @see org.jmlspecs.openjml.IAPI#prettyPrint(com.sun.tools.javac.tree.JCTree, boolean)
     */ // FIXME - allow the option of showing composite specs?
    //@ requires isOpen;
    //@ ensures isOpen;
    @Override
    public @NonNull String prettyPrint(@NonNull JCTree ast) throws java.io.IOException {
        StringWriter s = new StringWriter();
        Pretty p = JmlPretty.instance(s,true);
        if (ast instanceof JCTree.JCExpressionStatement) p.printStat(ast);
        else ast.accept(p);
        return s.toString();
    }
    
    @Override
    public @NonNull String prettyPrintJML(@NonNull JCTree ast) throws java.io.IOException {
        StringWriter s = new StringWriter();
        Pretty p = JmlPretty.instance(s,false);
        if (ast instanceof JCTree.JCExpressionStatement) p.printStat(ast);
        else ast.accept(p);
        return s.toString();
    }
    
    /* (non-Javadoc)
     * @see org.jmlspecs.openjml.IAPI#prettyPrint(java.util.List, boolean, java.lang.String)
     */
    //@ requires isOpen;
    //@ ensures isOpen;
    @Override
    public @NonNull String prettyPrint(@NonNull java.util.List<? extends JCTree> astlist, @NonNull String sep) throws java.io.IOException {
        StringWriter s = new StringWriter();
        boolean isFirst = true;
        for (JCTree ast: astlist) {
            if (!isFirst) { s.append(sep); isFirst = false; }
            JmlPretty.instance(s,true).print(ast);
        }
        return s.toString();
    }
    
    /* (non-Javadoc)
     * @see org.jmlspecs.openjml.IAPI#close()
     */
    //@ requires isOpen;
    //@ assignable isOpen;
    //@ ensures !isOpen;
    @Override
    public void close() {
        JmlCompiler.instance(main.context).close();
        main.context = null;
        main = null;
    }
    
    //@ public model boolean isOpen; private represents isOpen = main != null;

    /** Creates a JavaFileObject instance from a pseudo filename and given content
     * @param name the name to give the 'file'
     * @param content the content to give the file
     * @return the resulting JavaFileObject
     */ // FIXME - comment on whether the package path is needed
    @Override
    public JavaFileObject makeJFOfromString(String name, String content) throws Exception {
        return new StringJavaFileObject(name,content);
    }
    
    /** Creates a JavaFileObject instance from a real file, by name
     * @param filepath the path to the file, either absolute or relative to the current working directory
     * @return the resulting JavaFileObject
     */
    @Override
    public JavaFileObject makeJFOfromFilename(String filepath) {
        JavacFileManager dfm = (JavacFileManager)main.context.get(JavaFileManager.class);
        return dfm.getFileForInput(filepath);
    }
    
    /** Creates a JavaFileObject instance from a File object
     * @param file the file to wrap
     * @return the resulting JavaFileObject
     */
    @Override
    public JavaFileObject makeJFOfromFile(File file) {
        JavacFileManager dfm = (JavacFileManager)main.context.get(JavaFileManager.class);
        return dfm.getRegularFile(file);
    }
    
    /** This class encapsulates a String as a JavaFileObject, making it a pseudo-file
     */
    protected static class StringJavaFileObject extends SimpleJavaFileObject {
        
        /** The content of the mock file */
        //@ non_null
        protected String content;
        
        /** A fake file name, used when the user does not want to be bothered
         * supplying one.  We have to make and cache this because it is a pain to
         * deal with exceptions in constructors.
         */
        //@ non_null
        static final protected URI uritest = makeURI();
        
        /** A utility method to make the URI, so it can handle the exceptions. 
         * We don't try to recover gracefully if the exception occurs - this is
         * just used in testing anyway. */
        private static URI makeURI() {
            try {
                return new URI("file:///TEST.java");
            } catch (Exception e) {
                System.err.println("CATASTROPHIC EXIT - FAILED TO CONSTRUCT A MOCK URI");
                System.exit(3);
                return null;
            }
        }


        /** Constructs a new JavaFileObject of kind SOURCE or OTHER depending on the
         * filename extension
         * @param filename the filename to use (no leading slash) (null indicates to
         *          use the internal fabricated filename)
         * @param content the content of the pseudo file
         * @throws Exception if a URI cannot be created
         */ // FIXME - sort out the package part of the path
        public StringJavaFileObject(/*@ nullable */String filename, /*@ non_null */String content) throws Exception {
            // This takes three slashes because the filename is supposed to be absolute.
            // In our case this is not a real file anyway, so we pretend it is absolute.
            super(filename == null ? uritest : new URI("file:///" + filename),
                    filename == null || filename.endsWith(".java") ? Kind.SOURCE : Kind.OTHER);
            this.content = content;
        }

        /** Overrides the parent to provide the content directly from the String
         * supplied at construction, rather than reading the file.  This is called
         * by the system.
         */
        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return content;
        }
        
        /** Overrides the parent method to allow name compatibility between 
         * pseudo files of different kinds.
         */
        // Don't worry about whether the kinds match, just the file extension
        @Override
        public boolean isNameCompatible(String simpleName, Kind kind) {
            String s = uri.getPath();
            if (kind == Kind.OTHER) {
                int i = s.lastIndexOf('/');
                s = s.substring(i+1);
                return s.startsWith(simpleName);
            } else {
                String baseName = simpleName + kind.extension;
                return s.endsWith("/" + baseName);
            }
        }
        
        /** Returns true if the receiver and argument are the same object */
        public boolean equals(Object o) {
            return o == this;
        }
        
        /** A definition of hashCode, since we have a definition of equals */
        public int hashCode() {
            return super.hashCode();
        }
    }
}
