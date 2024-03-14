package uk.ac.gre.jml4sec.gen.impl;

import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Names;
import org.jmlspecs.openjml.JmlTree.JmlMethodSpecs;
import org.jmlspecs.openjml.JmlTree.JmlSpecificationCase;
import uk.ac.gre.jml4sec.gen.MethodCodeGenerator;

import java.util.ArrayList;
import java.util.Collections;


public class ActionBehaviorCodeGenerator implements MethodCodeGenerator {

    public static final ActionBehaviorCodeGenerator instance = new ActionBehaviorCodeGenerator();

    private TreeMaker maker;

    private Names symbolsTable;

    private ActionBehaviorCodeGenerator() {
    }

    @Override
    public void init(Context context) {
        this.maker = TreeMaker.instance(context);
        this.symbolsTable = Names.instance(context);
    }

    @Override
    public void generate(JmlMethodSpecs tree,
                         java.util.List<JmlSpecificationCase> cases) {
        JCTree.JCBlock methodBody = ((JCTree.JCMethodDecl) tree.decl).body;

        java.util.List<JCTree.JCIf> ifStatements = translateSpecsToIfStatements(
                cases);

        Collections.reverse(ifStatements);

        ifStatements.forEach(stmt -> {
            methodBody.stats = methodBody.stats.prepend(stmt);
        });
    }

    @Override
    public String generatorKey() {
        return "NotYetImplemented";
    }

    private java.util.List<JCTree.JCIf> translateSpecsToIfStatements(
            java.util.List<JmlSpecificationCase> genCandidateCases) {

        java.util.List<JCTree.JCIf> ifStatements = new ArrayList<>();

//        for (JmlSpecificationCase specCase : genCandidateCases) {
//
//            JCTree.JCExpression selector = maker
//                    .Ident(symbolsTable.fromString("_currentAttack"));
//
//            JCTree.JCExpression conditionExpression = null;
//            java.util.List<JCTree.JCCase> cases = new ArrayList<>();
//            for (Object clause : specCase.clauses) {
//                String clauseName = ((JmlMethodClause) clause).clauseKind.keyword;
//                if (clauseName.equalsIgnoreCase(
//                        MethodExprClauseExtensions.requiresID)) {
//                    if (conditionExpression == null) {
//                        conditionExpression = ((JmlMethodClauseExpr) clause).expression;
//                    } else {
//                        conditionExpression = maker.Binary(JCTree.Tag.AND,
//                                conditionExpression,
//                                ((JmlMethodClauseExpr) clause).expression);
//                    }
//                } else if (clauseName
//                        .equalsIgnoreCase(ActionClauseExtension.actionID)) {
//                    JCExpression casePattern = ((JmlMethodClauseAction) clause).actionType;
//                    JCStatement caseStatement = maker
//                            .Exec(((JmlMethodClauseAction) clause).expression);
//                    cases.add(maker.Case(casePattern,
//                            List.of(caseStatement, maker.Break(null))));
//                }
//            }
//
//            JCCase defaultCase = maker.Case(
//                    maker.Ident(symbolsTable.fromString("NONE")),
//                    List.of(maker.Break(null)));
//            JCTree.JCSwitch switchCaseStatement = maker.Switch(selector,
//                    List.from(cases).append(defaultCase));
//
//            ifStatements.add(maker.If(conditionExpression,
//                    maker.Block(0L, List.of(switchCaseStatement)), null));
//
//        }

        return ifStatements;
    }

}
