import javax.tools.*;
import com.sun.source.util.JavacTask;
import java.io.*;
import java.util.*;
public class ParseCheck {
  public static void main(String[] args) throws Exception {
    JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    StandardJavaFileManager files=compiler.getStandardFileManager(diagnostics, Locale.ROOT, null);
    List<java.io.File> inputs=new ArrayList<>();
    for(String s:args) inputs.add(new java.io.File(s));
    JavacTask task=(JavacTask)compiler.getTask(null,files,diagnostics,Arrays.asList("-proc:none"),null,files.getJavaFileObjectsFromFiles(inputs));
    int n=0;for(Object tree:task.parse())n++;
    for (Diagnostic<? extends JavaFileObject> d:diagnostics.getDiagnostics()) {
      if(d.getKind()==Diagnostic.Kind.ERROR) throw new RuntimeException(d.getSource()+":"+d.getLineNumber()+" "+d.getMessage(Locale.ROOT));
    }
    System.out.println("Java syntax parser PASS: "+n+" source files");
  }
}
