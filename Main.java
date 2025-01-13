
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


class CompilationManager extends ForwardingJavaFileManager {
    
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
    private CompilationManager manager;
    private StringWriter compilerOutput;
    private ByteArrayOutputStream programOutput;
    private ByteArrayOutputStream programInput; 
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

    public boolean execute(InputStream input, String fullname, String code) {
        boolean failed = false;
        compile(fullname, code);

        programOutput = new ByteArrayOutputStream(32768);
        programErrors = new ByteArrayOutputStream();

        PrintStream originalOutput = System.out;
        PrintStream originalError = System.err; // Save original System.err
        Class<?> klass;
        try {
            System.setOut(new PrintStream(programOutput));
            System.setErr(new PrintStream(originalError));
            System.setIn(input);

            String[] args = new String[]{};
            klass = manager.getClassLoader(null).loadClass(fullname);
            klass.getDeclaredMethod("main", String[].class)
                .invoke(null, (Object)args);
        } catch (Exception e) {
            failed = true;
        }
        finally {
            System.setOut(originalOutput);
            System.setErr(originalError);
            System.setIn(System.in);
        }
        return failed;            
    }


    public CodeExecution() {
       compiler = ToolProvider.getSystemJavaCompiler();
       manager = new CompilationManager(compiler.getStandardFileManager(null, null, null));
    }
}


public class Main {
    public static void main(String[] args) {
        //System.out.println("Hello, from Visual Studio Code!");
        String sourceCode = """
            public lass HelloWorld {
                public static void main(String[] args) {
                    System.out.println("Hello, World!");
                    System.err.println("This is an error message.");
                }

                public void cock() {}
            }
        """;
        CodeExecution executor = new CodeExecution();
        executor.execute(null, "HelloWorld", sourceCode);
    }
}