package uk.ac.gre.jml4sec.gen;

import uk.ac.gre.jml4sec.gen.impl.ActionBehaviorCodeGenerator;
import uk.ac.gre.jml4sec.gen.impl.CompileTimeEscVerificationCodeGenerator;
import uk.ac.gre.jml4sec.gen.impl.CompromisedBehaviorCodeGenerator;
import uk.ac.gre.jml4sec.gen.impl.SMTVerificationCodeGenerator;
import uk.ac.gre.jml4sec.gen.utils.GenUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;


public final class CodeGeneratorFactory {

    private static final Map<String, uk.ac.gre.jml4sec.gen.MethodCodeGenerator> GENERATORS = new HashMap<>();

    private CodeGeneratorFactory() {
    }

    static {
        GENERATORS.put(CompromisedBehaviorCodeGenerator.instance.generatorKey(),
               CompromisedBehaviorCodeGenerator.instance);
        GENERATORS.put(ActionBehaviorCodeGenerator.instance.generatorKey(),
                ActionBehaviorCodeGenerator.instance);
        GENERATORS.put(GenUtils.SMT_VERIFY_CODE_GEN_FACTORY_KEY,
                SMTVerificationCodeGenerator.instance);
        GENERATORS.put(GenUtils.COMPILETIME_ESC_VERIFY_CODE_GEN_FACTORY_KEY,
                CompileTimeEscVerificationCodeGenerator.instance);
    }

    public static uk.ac.gre.jml4sec.gen.MethodCodeGenerator methodCodeGenerator(String generatorKey) {
        return GENERATORS.get(generatorKey);
    }

    public static Set<String> generatableClauses() {
        return GENERATORS.keySet();
    }

}
