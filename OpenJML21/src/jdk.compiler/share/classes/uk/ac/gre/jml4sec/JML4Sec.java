package uk.ac.gre.jml4sec;

import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Names;
import org.jmlspecs.openjml.JmlTree;
import org.jmlspecs.openjml.esc.JmlAssertionAdder;
import org.jmlspecs.openjml.ext.MethodSimpleClauseExtensions;
import org.jmlspecs.openjml.visitors.JmlTreeScanner;
import uk.ac.gre.jml4sec.gen.utils.EscVerify;
import uk.ac.gre.jml4sec.gen.utils.GenUtils;
import uk.ac.gre.jml4sec.gen.utils.SMTSolver;

import java.util.HashSet;
import java.util.Set;

public class JML4Sec extends JmlTreeScanner {

    private final TreeMaker maker;
    private final Names symbolsTable;
    private final JmlAssertionAdder assertionAdder;

    private final Set<String> jml4SecClauseKeywords = new HashSet<>();

    protected static final Context.Key<JML4Sec> jml4seckey = new Context.Key<>();

    public static JML4Sec instance(Context context) {
        JML4Sec instance = context.get(jml4seckey);
        if (instance == null) {
            instance = new JML4Sec(context);
            context.put(jml4seckey, instance);
        }
        return instance;
    }

    public JML4Sec(Context context) {
        super(context);
        this.assertionAdder = new JmlAssertionAdder(context, true, false);
        this.maker = TreeMaker.instance(context);
        this.symbolsTable = Names.instance(context);
        this.jml4SecClauseKeywords.add(MethodSimpleClauseExtensions.compromisedBehaviorID);
    }

    public void instrument(JCTree tree) {
        assertionAdder.convert(tree);
        tree.accept(this);
        System.out.print("Instrumented source: ");
        System.out.println(tree);
    }

    @Override
    public void visitJmlMethodSpecs(JmlTree.JmlMethodSpecs tree) {
        JCTree.JCBlock methodBody = tree.decl.body;

        boolean hasJml4SecSpecs = false;
        for (JmlTree.JmlSpecificationCase specificationCase : tree.cases) {
            if (jml4SecClauseKeywords.contains(specificationCase.token.keyword)) {
                hasJml4SecSpecs = true;
                break;
            }
        }

        if (hasJml4SecSpecs) {
            JCTree.JCIf ifStatement = translateSpecsToIfStatements(tree);
            methodBody.stats = methodBody.stats.prepend(ifStatement);
            super.visitJmlMethodSpecs(tree);
        }
    }

    private JCTree.JCIf translateSpecsToIfStatements(JmlTree.JmlMethodSpecs tree) {
        JCTree.JCExpression smtVerifyMethodAccess = maker.Select(
                maker.Ident(symbolsTable.fromString(SMTSolver.class.getName())),
                symbolsTable.fromString("verify"));

        JCTree.JCExpression escVerifyMethodAccess = maker.Select(
                maker.Ident(symbolsTable.fromString(EscVerify.class.getName())),
                symbolsTable.fromString("verify"));

        JCTree.JCExpression expression = maker.Literal(TypeTag.CLASS,
                GenUtils.FILE_DIR + GenUtils.getFileName(tree.decl));

        JCTree.JCMethodInvocation escMethodInvocation = maker
                .Apply(null, escVerifyMethodAccess, List.of(expression))
                .setType(maker.Literal(TypeTag.BOOLEAN, true).type);

        JCTree.JCMethodInvocation smtMethodInvocation = maker
                .Apply(null, smtVerifyMethodAccess, List.of(expression))
                .setType(maker.Literal(TypeTag.BOOLEAN, true).type);

        JCTree.JCThrow throwable = maker.Throw(maker.NewClass(null, null,
                maker.Ident(
                        symbolsTable.fromString("java.lang.RuntimeException")),
                List.nil(), null));

        JCTree.JCIf escCheck = maker.If(maker.Unary(JCTree.Tag.NOT, escMethodInvocation), throwable, null);

        return maker.If(
                maker.Unary(JCTree.Tag.NOT, smtMethodInvocation), escCheck, null);

    }
}


