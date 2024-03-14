package uk.ac.gre.jml4sec.gen.impl;

import org.jmlspecs.openjml.JmlTree.JmlMethodSpecs;
import org.jmlspecs.openjml.JmlTree.JmlSpecificationCase;

import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCIf;
import com.sun.tools.javac.tree.JCTree.JCMethodInvocation;
import com.sun.tools.javac.tree.JCTree.JCThrow;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Names;
import uk.ac.gre.jml4sec.gen.MethodCodeGenerator;
import uk.ac.gre.jml4sec.gen.utils.GenUtils;
import uk.ac.gre.jml4sec.gen.utils.SMTSolver;

public class SMTVerificationCodeGenerator implements MethodCodeGenerator {

    public static final SMTVerificationCodeGenerator instance = new SMTVerificationCodeGenerator();

    private SMTVerificationCodeGenerator() {
    }

    private TreeMaker maker;

    private Names symbolsTable;

    public void init(Context context) {
        this.maker = TreeMaker.instance(context);
        this.symbolsTable = Names.instance(context);
    }

    @Override
    public void generate(JmlMethodSpecs tree,
                         java.util.List<JmlSpecificationCase> cases) {
        JCTree.JCBlock methodBody = tree.decl.body;

        JCTree.JCIf ifStatement = translateSpecsToIfStatements(tree);

        methodBody.stats = methodBody.stats.prepend(ifStatement);

    }

    @Override
    public String generatorKey() {
        return GenUtils.SMT_VERIFY_CODE_GEN_FACTORY_KEY;
    }

    private JCTree.JCIf translateSpecsToIfStatements(JmlMethodSpecs tree) {
        JCExpression verifyMethodAccess = maker.Select(
                maker.Ident(symbolsTable.fromString(SMTSolver.class.getName())),
                symbolsTable.fromString("verify"));

        JCExpression expression = maker.Literal(TypeTag.CLASS,
                GenUtils.FILE_DIR + GenUtils.getFileName(tree.decl));

        JCMethodInvocation methodInvocation = maker
                .Apply(null, verifyMethodAccess, List.of(expression))
                .setType(maker.Literal(TypeTag.BOOLEAN, true).type);

        JCThrow throwable = maker.Throw(maker.NewClass(null, null,
                maker.Ident(
                        symbolsTable.fromString("java.lang.RuntimeException")),
                List.nil(), null));

        return maker.If(
                maker.Unary(JCTree.Tag.NOT, methodInvocation), throwable, null);
    }
}
