package uk.ac.gre.jml4sec.gen.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jmlspecs.openjml.API;
import org.jmlspecs.openjml.Factory;
import org.jmlspecs.openjml.IAPI;
import org.jmlspecs.openjml.JmlTree.JmlClassDecl;
import org.jmlspecs.openjml.JmlTree.JmlCompilationUnit;
import org.jmlspecs.openjml.JmlTree.JmlMethodSpecs;

import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCMethodInvocation;
import com.sun.tools.javac.tree.JCTree.JCStatement;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Names;
import org.jmlspecs.openjml.visitors.JmlTreeScanner;

public class EscVerify {
    private static final String SOURCE_FOLDER = "/Users/suranjanpoudel/Documents/git/ug/OpenJML/OpenJML/src/";

    public static boolean verify(String className, String methodName,
            Object[] params) {

        String classLocation = className.replaceAll("\\.", "/");

        Path sourceFilePath = null;

        try {
            IAPI api = Factory.makeAPI();
            
            JmlCompilationUnit unit = api
                    .parseSingleFile(SOURCE_FOLDER + classLocation + ".java");
            
            CodeGenerator gen = new CodeGenerator(api.context(), methodName,
                    params);
            unit.accept(gen);

            SourceWriter sourceWriter = new SourceWriter(className, unit);
            sourceFilePath = sourceWriter.writeSource();

            java.util.List<String> output = EscRunner
                    .runEsc(sourceFilePath.toString(), "test_" + methodName);

            output.stream().forEach(System.out::println);
        } catch (Exception th) {
            th.printStackTrace();
        } finally {
            if (sourceFilePath != null) {
                try {
                    Files.deleteIfExists(sourceFilePath);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return true;
    }

    private static class CodeGenerator extends JmlTreeScanner {

        private final Context  context;

        private final String   methodName;

        private final Object[] params;

        private JmlClassDecl   owner;

        public CodeGenerator(Context context, String methodName,
                Object[] params) {
            this.context = context;
            this.methodName = methodName;
            this.params = params;
        }

        @Override
        public void visitJmlClassDecl(JmlClassDecl that) {
            owner = that;
            super.visitJmlClassDecl(that);
        }

        @Override
        public void visitJmlMethodSpecs(JmlMethodSpecs tree) {
            if (!tree.decl.name.toString().equals(methodName)) {
                super.visitJmlMethodSpecs(tree);
                return;
            }

            TreeMaker maker = TreeMaker.instance(context);
            Names symbolTable = Names.instance(context);

            java.util.List<JCExpression> arguments = new ArrayList<>();
            for (Object param : params) {
                arguments.add(maker.Literal(param));
            }

            JCMethodInvocation methodInvocation = maker.Apply(List.nil(),
                    maker.Ident(symbolTable.fromString(methodName)),
                    List.from(arguments));

            JCStatement methodCall = maker.Exec(methodInvocation);

            JCMethodDecl testMethodDefinition = maker.MethodDef(
                    maker.Modifiers(Flags.PRIVATE),
                    symbolTable.fromString("test_" + methodName),
                    maker.TypeIdent(TypeTag.VOID), List.nil(), null, List.nil(),
                    List.nil(), maker.Block(0, List.of(methodCall)), null);

            owner.defs = owner.defs.append(testMethodDefinition);

            super.visitJmlMethodSpecs(tree);
        }
    }

    private static class SourceWriter {

        private static final Pattern     SPEC_COMMENT_PATTERN = Pattern
                .compile("/*@(.*?)\\*/", Pattern.DOTALL);

        private final String             originalClassName;

        private final JCTree.JCCompilationUnit unit;

        public SourceWriter(String originalClassName, JCTree.JCCompilationUnit unit) {
            this.originalClassName = originalClassName.substring(
                    originalClassName.lastIndexOf(".") + 1,
                    originalClassName.length());
            this.unit = unit;
        }

        public Path writeSource() throws Exception {

            Path tempFile = Files.createTempFile("Test_", ".java");

            String code = replaceSpecificationNewLines(unit.toString());

            code = code.replaceAll(originalClassName, tempFile.getFileName().toString().replace(".java", ""));

            System.out.println(code);

            Files.write(tempFile, code.getBytes(), StandardOpenOption.WRITE);

            return tempFile;
        }

        public static String replaceSpecificationNewLines(String text) {
            Matcher matcher = SPEC_COMMENT_PATTERN.matcher(text);
            if (matcher.find()) {
                String matchedText = matcher.group(1);
                String replacedText = matchedText.replace("\n", "\n@");
                replacedText.replaceAll("\\s+", " ");
                replacedText.replaceAll(matchedText, replacedText);
                replacedText = replacedText.trim();
                if(replacedText.lastIndexOf("@") == replacedText.length()-1) {
                    replacedText = replacedText.substring(0, replacedText.lastIndexOf('@'));
                }
                replacedText = replacedText + "*/";
                text = matcher.replaceFirst(replacedText);
            }
            return text;
        }

    }

}
