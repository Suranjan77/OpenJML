package uk.ac.gre.jml4sec.gen.utils;

import org.jmlspecs.openjml.JmlTree.JmlMethodDecl;

import java.util.StringJoiner;

public class GenUtils {
    
    public static final String FILE_DIR = "/Users/suranjanpoudel/Documents/git/ug/out/";
    
    public static final String SMT_VERIFY_CODE_GEN_FACTORY_KEY = "smt_verify_code_generator";
    public static final String COMPILETIME_ESC_VERIFY_CODE_GEN_FACTORY_KEY = "compiletime_esc_verify_code_generator";

    public static String getFileName(JmlMethodDecl decl) {
        StringJoiner joiner = new StringJoiner("_");
        String className = decl.sym.owner.toString().replaceAll("\\.", "_");
        joiner.add("verify");
        joiner.add(className);
        joiner.add(decl.name.toString());
        return joiner.toString();
    }

}

