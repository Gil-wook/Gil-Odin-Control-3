import com.sun.source.util.JavacTask;
import java.io.StringWriter;
import java.util.Arrays;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/** Syntax parser: does not pretend to resolve Android SDK symbols. */
public class ParseJava {
    public static void main(String[] paths) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> errors = new DiagnosticCollector<>();
        try (StandardJavaFileManager files = compiler.getStandardFileManager(errors,null,null)) {
            Iterable<? extends JavaFileObject> units = files.getJavaFileObjectsFromStrings(Arrays.asList(paths));
            JavacTask task = (JavacTask) compiler.getTask(new StringWriter(), files, errors,
                    Arrays.asList("-proc:none", "-source", "17"), null, units);
            task.parse();
            long failure = errors.getDiagnostics().stream()
                    .filter(d -> d.getKind() == javax.tools.Diagnostic.Kind.ERROR).count();
            if (failure > 0) {
                errors.getDiagnostics().forEach(System.err::println);
                throw new AssertionError("Java syntax errors: " + failure);
            }
            System.out.println("Java syntax parsed: " + paths.length + " files");
        }
    }
}
