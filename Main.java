
import java.security.SecureClassLoader;

import java.io.*;
import java.util.*;

import javax.tools.*;
import javax.tools.JavaFileObject.Kind;

import java.net.URI;
import java.lang.ClassNotFoundException;
import java.lang.ClassLoader;


class CompilationCodeObject extends SimpleJavaFileObject {
    private CharSequence content;

    public CompilationCodeObject(String className, CharSequence content) {
        super(URI.create("string:///" + className.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
        this.content = content;
    }

    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
        return content;
    }
}

// After the bytecode is compiled, we want to send the bytecode to this class
class CompilationObject extends SimpleJavaFileObject {

    protected final ByteArrayOutputStream bytecode;

    CompilationObject(String name, Kind kind) {
        super(URI.create("string:///" + name.replace('.', '/')
                + kind.extension), kind);
        this.bytecode = new ByteArrayOutputStream();
    }

    public byte[] getBytes() {
        return bytecode.toByteArray();
    }

    @Override
    public OutputStream openOutputStream() throws IOException {
        return bytecode;
    }
}

class CompilationManager<T> extends ForwardingJavaFileManager<JavaFileManager> {
    
    private CompilationObject classObject;

    public CompilationManager(StandardJavaFileManager manager) {
        super(manager);
    }

    @Override 
    public ClassLoader getClassLoader(Location location) {
        return new SecureClassLoader() {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                byte[] bytecode = classObject.getBytes();
                return super.defineClass(name, classObject.getBytes(), 0, bytecode.length); // change privileges here
            }
        };
    }

    public CompilationObject getJavaFileForOutput(Location location, String className, Kind kind, FileObject sibling) throws IOException {
        classObject = new CompilationObject(className, kind);
        return classObject;
    }
 
}

class CodeExecution {
    private JavaCompiler compiler;
    private CompilationManager<JavaFileManager> manager;
    private StringWriter compilerOutput;
    private ByteArrayOutputStream programOutput;
    private InputStream programInput;
    private ByteArrayOutputStream programErrors;

    public void compile(String fullname, String code) {
        compilerOutput = new StringWriter(32768);
       
        if (compiler == null) {
            System.out.println("gay!\n");
            return;
        }
               
        // To create a new task our Java file objects have to be in a List<> 
        List<CompilationCodeObject> unit = new ArrayList<>();
        unit.add(new CompilationCodeObject(fullname, code));
        compiler.getTask(compilerOutput, manager, null, null, null, unit).call();
    }

    public boolean execute(String fullname, String code) {
        boolean failed = false;
        compile(fullname, code);

        programOutput = new ByteArrayOutputStream(32768);
        programErrors = new ByteArrayOutputStream();

        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        InputStream originalIn = System.in;


        Class<?> klass;
        try {
            System.setOut(new PrintStream(programOutput));
            System.setErr(new PrintStream(programErrors));

            String[] args = new String[]{};
            klass = manager.getClassLoader(null).loadClass(fullname);
            klass.getDeclaredMethod("main", String[].class)
                .invoke(null, (Object)args);
        } 
        catch (Exception e) {
            failed = true;
            e.printStackTrace(new PrintStream(programErrors));
        }
        finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
            System.setIn(originalIn);

            
        }
        return failed;            
    }

    public ByteArrayOutputStream getProgramOutput() {
        return programOutput;
    }

    public ByteArrayOutputStream getProgramErrors() {
        return programErrors;
    }

    public String getCompileErrors() {
        return compilerOutput.toString();
    }

    public boolean updateInput(CharSequence input) {
        try {
            programInput = new ByteArrayInputStream(input.toString().getBytes());
            System.setIn(programInput);
        }
        catch (Exception e) {
            System.setOut(System.out);
            System.setErr(System.err);
            System.err.println("Failed to update input");
            return false;
        }
        
        return true;
    }

    public CodeExecution() {
       compiler = ToolProvider.getSystemJavaCompiler();
       manager = new CompilationManager<>(compiler.getStandardFileManager(null, null, null));
    }
}


public class Main {
    public static void main(String[] args) {
        //System.out.println("Hello, from Visual Studio Code!");
        String sourceCode = """

            import java.io.*;
            import java.util.*;

            public class Dang {
                public static void main(String[] args) {
                    Scanner input = new Scanner(System.in);
                    System.out.println("Hello, World!");
                    System.out.println("This is an error message.");
                    System.out.println(input.nextLine());
                    System.out.println(input.nextLine());
                    System.out.println(input.nextInt() + input.nextInt());
                    System.out.println(input.nextInt());
                }
            }
        """;
        CodeExecution executor = new CodeExecution();
        executor.updateInput("cock\nball 3 2\n3 1\npenis");

        boolean failed = executor.execute("Dang", sourceCode);
        if (failed) {
            System.out.println("Compilation or execution error:");
            System.out.println("Compile Errors: " + executor.getCompileErrors());
            System.out.println("Runtime Errors: " + executor.getProgramErrors().toString());
        } else {
            System.out.println("Program Output:");
            System.out.println(executor.getProgramOutput().toString());
        }
        
    }
}