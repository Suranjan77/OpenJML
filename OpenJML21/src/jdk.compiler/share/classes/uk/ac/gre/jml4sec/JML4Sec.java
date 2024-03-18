package uk.ac.gre.jml4sec;

import org.jmlspecs.openjml.Factory;
import org.jmlspecs.openjml.IAPI;
import org.jmlspecs.openjml.JmlTree.JmlCompilationUnit;

import com.sun.tools.javac.util.Context;
public class JML4Sec {

    public void typeCheck(JmlCompilationUnit compilationUnit, Context context) {
        int errors = 0;

        try {
            IAPI api = Factory.makeAPIWithContext(context);

            errors = api.typecheck(compilationUnit);

            api.translate(compilationUnit);

            System.out.println("Source code after translation");
            System.out.println(compilationUnit);

        } catch (Throwable th) {
            th.printStackTrace();
        } finally {
            System.out.println(errors > 0 ? "Type checking failed."
                    : "Type check successfully.");
        }
    }
}
