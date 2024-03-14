package uk.ac.gre.jml4sec.gen.visitor;

import java.io.FileWriter;
import java.io.PrintWriter;

import org.jmlspecs.openjml.JmlOption;
import org.jmlspecs.openjml.JmlSpecs;
import org.jmlspecs.openjml.JmlTree.JmlClassDecl;
import org.jmlspecs.openjml.JmlTree.JmlMethodDecl;
import org.jmlspecs.openjml.JmlTree.JmlMethodSpecs;
import org.jmlspecs.openjml.Utils;
import org.jmlspecs.openjml.esc.BasicBlocker2;
import org.jmlspecs.openjml.esc.BasicProgram;
import org.jmlspecs.openjml.esc.JmlEsc;
import org.jmlspecs.openjml.esc.SMTTranslator;
import org.jmlspecs.openjml.esc.Translations;
import org.jmlspecs.openjml.visitors.JmlTreeScanner;
import org.smtlib.ICommand;
import org.smtlib.SMT;

import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCBlock;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import uk.ac.gre.jml4sec.gen.MethodCodeGenerator;
import uk.ac.gre.jml4sec.gen.utils.GenUtils;
import uk.ac.gre.jml4sec.gen.CodeGeneratorFactory;


/**
 * Scans the {@link org.jmlspecs.openjml.JmlTree.JmlCompilationUnit} and does
 * the following things: i. Translates each method to SMT and writes to a file.
 * ii. Instruments the AST with assertions for verification of SMT for each
 * method. Currently, this class is capable of doing things mentioned above for
 * action_behavior and compromised_behavior clauses.
 * <p>
 * Example: JmlEsc esc = JmlEsc.instance(context()); esc.check(compilationUnit);
 * SMTVerificationInstrumenter generator = new
 * SMTVerificationInstrumenter(context(), esc);
 * compilationUnit.accept(generator);
 *
 * @author Suranjan Poudel
 */
public class SMTVerificationInstrumenter extends JmlTreeScanner {

    private final Context context;

    private final JmlEsc esc;

    private final Utils utils;

    public SMTVerificationInstrumenter(Context context, JmlEsc esc) {
        super();
        this.context = context;
        this.utils = Utils.instance(context);
        this.esc = esc;
    }

    @Override
    public void visitJmlMethodSpecs(JmlMethodSpecs tree) {

        try {
            JmlMethodDecl methodDecl = tree.decl;
            writeSMT(methodDecl, GenUtils.getFileName(tree.decl));

            JmlClassDecl ownerClass = utils.getOwner(methodDecl);

            List<JCTree> classMembers = ownerClass.defs;
            String testClassName = "test_" + methodDecl.name.toString();
            for (JCTree member : classMembers) {
                if (JmlMethodDecl.class.isAssignableFrom(member.getClass())) {
                    JmlMethodDecl testMethodDecl = (JmlMethodDecl) member;
                    if (testMethodDecl.name.toString().equals(testClassName)) {
                        writeSMT(testMethodDecl,
                                GenUtils.getFileName(testMethodDecl));
                        break;
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        MethodCodeGenerator codeGenerator = CodeGeneratorFactory
                .methodCodeGenerator(
                        GenUtils.COMPILETIME_ESC_VERIFY_CODE_GEN_FACTORY_KEY);
        codeGenerator.init(context);
        codeGenerator.generate(tree, null);

        super.visitJmlMethodSpecs(tree);
    }

    private void writeSMT(JmlMethodDecl methodDecl, String smtFileName)
            throws Exception {
        JmlOption.putOption(context, JmlOption.SHOW, "smt");

        esc.assertionAdder.visitJmlMethodDecl(methodDecl);

        Translations translations = esc.assertionAdder.methodBiMap
                .getf(methodDecl);

        JmlClassDecl currentClassDecl = utils.getOwner(methodDecl);

        for (String splitkey : translations.keys()) {
            JmlMethodDecl translatedMethod = translations
                    .getTranslation(splitkey);
            esc.assertionAdder.setSplits(translations, splitkey);

            // newBlock is the translated version of the method body, for a
            // given split
            JCBlock newblock = translatedMethod.getBody();
            // JCBlock newblock = methodDecl.getBody();
            // System.out.println(translatedMethod);

            // now convert to basic block form
            BasicBlocker2 basicBlocker = new BasicBlocker2(context);
            BasicProgram program = basicBlocker.convertMethodBody(newblock,
                    methodDecl, currentClassDecl,
                    esc.assertionAdder);
            // System.out.println(program);

            SMT smt = new SMT();
            SMTTranslator smtTranslator = new SMTTranslator(context,
                    methodDecl.sym.toString());

            // convert the basic block form to SMT
            ICommand.IScript script;
            try {
                script = smtTranslator.convert(program, smt,
                        methodDecl.usedBitVectors);
            } catch (Exception e) {
                throw e;
            }

            try (PrintWriter writer = new PrintWriter(
                    new FileWriter(GenUtils.FILE_DIR + smtFileName + ".smt"))) {
                writer.println("(");
                script.commands().forEach(writer::println);
                writer.println(")");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
