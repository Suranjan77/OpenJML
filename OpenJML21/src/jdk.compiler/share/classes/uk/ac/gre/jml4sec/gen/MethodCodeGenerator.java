package uk.ac.gre.jml4sec.gen;

import java.util.List;

import org.jmlspecs.openjml.IJmlClauseKind;
import org.jmlspecs.openjml.JmlTree.JmlMethodSpecs;
import org.jmlspecs.openjml.JmlTree.JmlSpecificationCase;

import com.sun.tools.javac.util.Context;

public interface MethodCodeGenerator {
    /**
     * Initialises tree maker and symbols table from the context
     * 
     * @param context
     *            current compilation context
     */
    void init(Context context);

    /**
     * Generates code from the specification cases on the AST.
     * 
     * @param tree
     *            AST representing the JML method and specification
     * @param cases
     *            JML specification cases for which code needs to be generated
     */
    void generate(JmlMethodSpecs tree, List<JmlSpecificationCase> cases);

    /**
     * Key to identify the generator.
     * It can be kind of the clause for which this generator is designed for.
     */
    String generatorKey();
}
