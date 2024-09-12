package uk.ac.gre.jml4sec.gen.impl;

import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Names;
import org.jmlspecs.openjml.JmlTree.JmlMethodSpecs;
import org.jmlspecs.openjml.JmlTree.JmlSpecificationCase;
import org.jmlspecs.openjml.Utils;
import uk.ac.gre.jml4sec.gen.MethodCodeGenerator;
import uk.ac.gre.jml4sec.gen.utils.EscVerify;
import uk.ac.gre.jml4sec.gen.utils.GenUtils;

import java.util.ArrayList;
import java.util.stream.Collectors;


/**
 * This method is to be used at compile time.
 * <p>
 * Scans the {@link org.jmlspecs.openjml.JmlTree.JmlCompilationUnit} and adds if
 * condition in each method with JML specification. The condition calls
 * {@link uk.ac.gre.jml4sec.gen.utils.EscVerify#verify} method
 *
 * @author Suranjan Poudel
 */
public class CompileTimeEscVerificationCodeGenerator
        implements MethodCodeGenerator {

    public static final CompileTimeEscVerificationCodeGenerator instance = new CompileTimeEscVerificationCodeGenerator();

    private CompileTimeEscVerificationCodeGenerator() {
    }

    private TreeMaker maker;

    private Names symbolsTable;

    private Utils utils;

    public void init(Context context) {
        this.maker = TreeMaker.instance(context);
        this.symbolsTable = Names.instance(context);
        this.utils = Utils.instance(context);
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
        return GenUtils.COMPILETIME_ESC_VERIFY_CODE_GEN_FACTORY_KEY;
    }

    private JCTree.JCIf translateSpecsToIfStatements(JmlMethodSpecs tree) {
        JCExpression verifyMethodAccess = maker.Select(
                maker.Ident(symbolsTable.fromString(EscVerify.class.getName())),
                symbolsTable.fromString("verify"));

        java.util.List<JCExpression> arguments = new ArrayList<>();
        arguments.add(maker.Literal(utils.getOwner(tree.decl).sym.toString()));
        arguments.add(maker.Literal(tree.decl.name.toString()));

        List<JCExpression> realMethodParameters = List
                .from(tree.decl.params.stream().map(param -> maker.Ident(param))
                        .collect(Collectors.toList()));

        JCNewArray paramArray = maker.NewArray(
                maker.Ident(symbolsTable.fromString("java.lang.Object")),
                List.nil(), realMethodParameters);

        arguments.add(paramArray);

        JCMethodInvocation methodInvocation = maker.Apply(List.nil(),
                verifyMethodAccess, List.from(arguments));

        JCIf ifStatement = null;

//        for (JmlSpecificationCase specCase : tree.cases) {
//
//            JCTree.JCExpression selector = maker
//                    .Ident(symbolsTable.fromString("_currentAttack"));
//
//            java.util.List<JCTree.JCCase> cases = new ArrayList<>();
//            for (Object clause : specCase.clauses) {
//                String clauseName = ((JmlMethodClause) clause).clauseKind.keyword;
//                if (clauseName
//                        .equalsIgnoreCase(AlarmsClauseExtension.alarmsID)) {
//                    JCExpression casePattern = ((JmlMethodClauseAlarms) clause).attackType;
//                    JCStatement caseStatement = maker
//                            .Exec(((JmlMethodClauseAlarms) clause).expression);
//                    cases.add(maker.Case(casePattern,
//                            List.of(caseStatement, maker.Break(null))));
//                }
//            }
//
//            if (!cases.isEmpty()) {
//                JCCase defaultCase = maker.Case(
//                        maker.Ident(symbolsTable.fromString("NONE")),
//                        List.of(maker.Break(null)));
//                JCTree.JCSwitch switchCaseStatement = maker.Switch(selector,
//                        List.from(cases).append(defaultCase));
//
//                ifStatement = maker.If(
//                        maker.Unary(JCTree.Tag.NOT, methodInvocation),
//                        maker.Block(0, List.of(switchCaseStatement)), null);
//            }
//
//        }

        if (ifStatement == null) {

            JCThrow throwable = maker.Throw(maker.NewClass(null, null,
                    maker.Ident(symbolsTable
                            .fromString("java.lang.RuntimeException")),
                    List.nil(), null));

            ifStatement = maker.If(
                    maker.Unary(JCTree.Tag.NOT, methodInvocation), throwable,
                    null);
        }

        return ifStatement;
    }
}
