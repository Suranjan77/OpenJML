package uk.ac.gre.jml4sec.gen.visitor;

import com.sun.tools.javac.util.Context;
import org.jmlspecs.openjml.JmlTree.JmlMethodSpecs;
import org.jmlspecs.openjml.visitors.JmlTreeScanner;
import uk.ac.gre.jml4sec.gen.CodeGeneratorFactory;
import uk.ac.gre.jml4sec.gen.MethodCodeGenerator;
import uk.ac.gre.jml4sec.gen.utils.GenUtils;

public class EscVerificationInstrumentor extends JmlTreeScanner {

    private final Context context;

    public EscVerificationInstrumentor(Context context) {
        super();
        this.context = context;
    }

    @Override
    public void visitJmlMethodSpecs(JmlMethodSpecs tree) {

        MethodCodeGenerator gen = CodeGeneratorFactory.methodCodeGenerator(
                GenUtils.COMPILETIME_ESC_VERIFY_CODE_GEN_FACTORY_KEY);

        gen.init(context);
        gen.generate(tree, null);
    }
}
