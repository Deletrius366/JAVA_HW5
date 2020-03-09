package ru.ifmo.rain.levashov.implementor;

import info.kgeorgiy.java.advanced.implementor.Impler;
import info.kgeorgiy.java.advanced.implementor.ImplerException;
import info.kgeorgiy.java.advanced.implementor.JarImpler;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.CodeSource;
import java.util.Arrays;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;

/**
 * Class for java classes and interfaces implementations.
 * Implements {@link Impler} and {@link JarImpler} interfaces and
 * provides public methods to create implementations and <code>.jar</code> files
 * for given classes and interfaces
 * @see #implement(Class, Path)
 * @see #implementJar(Class, Path)
 *
 * @author Georgiy Levashov
 * @version 1.0
 *
 */
public class Implementor implements Impler, JarImpler {

    /**
     * Space indention
     */
    private static final String SPACE = " ";

    /**
     * Double empty line indention specified by {@link System#lineSeparator()}
     */
    private static final String DOUBLE_EMPTY_LINE = System.lineSeparator() + System.lineSeparator();

    /**
     * Class to recursively directory deletions. Extends {@link SimpleFileVisitor}
     */
    private class Deleter extends SimpleFileVisitor<Path> {

        /**
         * Default constructor to create new instance of {@link Deleter}. Uses super constructor
         */
        Deleter() {
            super();
        }

        /**
         * File visitor, which deletes visited files
         * @param file {@link Path} file to visit
         * @param attrs {@link BasicFileAttributes} {@code file} attributes
         * @return {@link FileVisitResult#CONTINUE}
         * @throws IOException if deletion fails for some reason
         */
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            Files.delete(file);
            return FileVisitResult.CONTINUE;
        }

        /**
         * Directory visitor, which deletes directory after visiting all files in it
         * @param dir {@link Path} directory to visit
         * @param exc {@link IOException} instance if error occured during directory visiting
         * @return {@link FileVisitResult#CONTINUE}
         * @throws IOException if deletion fails for some reason
         */
        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            Files.delete(dir);
            return FileVisitResult.CONTINUE;
        }
    }

    /**
     * Class to generate unique names
     */
    private class Namer {

        /**
         * integer value, which used for name generation
         */
        private int index;

        /**
         * Default constructor to create new instance of {@link Namer}
         */
        Namer() {
            index = 0;
        }

        /**
         * @return new unique name generated using {@link Namer#index}
         */
        private String getName() {
            return "_" + index++;
        }
    }

    /**
     * Main function which implements console interface for {@link Implementor}
     * Runs in two mods:
     * <ul>
     *     <li> 2 arguments: <tt>className</tt> and <tt>outputPath</tt>, runs {@link #implement(Class, Path)} for it</li>
     *     <li> 3 arguments: <tt>-jar, className</tt> and <tt>outputPath</tt>, runs {@link #implementJar(Class, Path)} for it</li>
     * </ul>
     * If error occurs during function processing, function stops and notices about error.
     * @param args console arguments
     */
    public static void main(String[] args) {
        if (args == null || (args.length != 2 && args.length != 3)) {
            System.err.println("Expected 2 or 3 arguments");
            return;
        }
        for (String arg : args) {
            if (arg == null) {
                System.err.println("Expected not-null arguments");
                return;
            }
        }
        if (args.length == 3 && !args[0].equals("-jar")) {
            System.err.println(String.format("Expected -jar, found %s", args[0]));
            return;
        }

        Implementor implementor = new Implementor();
        try {
            if (args.length == 2) {
                implementor.implement(Class.forName(args[0]), Path.of(args[1]));
            } else {
                implementor.implementJar(Class.forName(args[1]), Path.of(args[2]));
            }
        } catch (ClassNotFoundException e) {
            System.err.println("Failed to implement class given: " + e.getMessage());
        } catch (InvalidPathException e) {
            System.err.println("Failed to create path for output file: " + e.getMessage());
        } catch (ImplerException e) {
            System.err.println("Failed to generate implementation code for class given: " + e.getMessage());
        }
    }

    /**
     * Returns package info declaration for {@code token} class implementation
     * or empty {@link String} if no declaration needed
     *
     * @param token class to get package info
     * @return {@link String} containing package info declaration
     */
    private String getPackageInfo(Class<?> token) {
        return token.getPackageName() == null ? "" : ("package " + token.getPackageName() + ";");
    }

    /**
     * Creates all upper directories for {@link Path} given
     * @param path {@link Path} to create directories for
     * @throws IOException if directories creation fails for some reason
     */
    private static void createPath(Path path) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    /**
     * Returns {@code token} class modifiers representation
     * @param token class to get modifiers for
     * @return {@link String} contating modifiers for given {@code token} class
     */
    private String getClassModifiers(Class<?> token) {
        return Modifier.toString(token.getModifiers() & ~Modifier.INTERFACE & ~Modifier.ABSTRACT & ~Modifier.STATIC & ~Modifier.PROTECTED);
    }

    /**
     * Returns extension representation based on {@code token} class
     * @param token class using for extension
     * @return {@link String} containing extension representation based on {@code token}
     */
    private String getExtension(Class<?> token) {
        return (token.isInterface() ? "implements " : "extends ") + token.getCanonicalName();
    }

    /**
     * Returns {@code strings} concatenation using {@code separator} as delimiter.
     * Empty strings are ignored
     * @param strings array of {@link String} to concatenate
     * @param separator delimiter using in concatenation
     * @return {@link String} containing concatenation
     */
    private String combine(String[] strings, String separator) {
        return Arrays.stream(strings).filter(s -> !"".equals(s)).collect(Collectors.joining(separator));
    }

    /**
     * Concatenate {@code strings} using {@link #SPACE} as separator
     * @param strings array of {@link String} to concatenate
     * @return {@link String} containing concatenation
     */
    private String combineWithSpaces(String... strings) {
        return combine(strings, SPACE);
    }

    /**
     * Concatenate {@code strings} using {@link #DOUBLE_EMPTY_LINE} as separator
     * @param strings array of {@link String} to concatenate
     * @return {@link String} containing concatenation
     */
    private String combineBlocks(String... strings) {
        return combine(strings, DOUBLE_EMPTY_LINE);
    }

    /**
     * Concatenate {@code first} string and {@code second} string if {@code string} is not empty
     * and empty string otherwise
     * @param first first {@link String} to concatenate
     * @param second second {@link String} to concatenate
     * @return {@link String} containing concatenation if {@code second} is not empty and empty string otherwise
     */
    private String combineIfNotEmpty(String first, String second) {
        return (!"".equals(second)) ? combineWithSpaces(first, second) : "";
    }

    /**
     * Returns {@code token} declaration line containing class modifiers, name and extensions
     *
     * @param token class to get declaration for
     * @return {@link String} containing full declaration line for {@code token}
     */
    private String getClassDef(Class<?> token) {
        return combineWithSpaces(getClassModifiers(token), "class", getClassName(token), getExtension(token));
    }

    /**
     * Returns modifiers representation for given {@link Method} or {@link Constructor}
     * {@link Modifier#NATIVE}, {@link Modifier#TRANSIENT}, {@link Modifier#ABSTRACT} excluded
     * @param executable instance of {@link Executable} which contains a method or constructor
     * @return {@link String} containing modifiers representation
     */
    private String getExecutableModifiers(Executable executable) {
        return Modifier.toString(executable.getModifiers() & ~Modifier.NATIVE & ~Modifier.TRANSIENT & ~Modifier.ABSTRACT);
    }

    /**
     * Returns arguments representation for given {@link Method} or {@link Constructor}.
     * Argument names are generated by {@link Namer}
     * @param executable instance of {@link Executable} which contains a method or constructor
     * @return {@link String} containing arguments representation for {@code executable}
     */
    private String getExecutableArguments(Executable executable) {
        Namer namer = new Namer();
        return "(" + Arrays.stream(executable.getParameterTypes())
                .map(c -> combineWithSpaces(c.getCanonicalName(), namer.getName()))
                .collect(Collectors.joining(", ")) + ")";

    }

    /**
     * Returns arguments names representation for given {@link Method} or {@link Constructor}.
     * Argument names are generated by {@link Namer}
     * @param executable instance of {@link Executable} which contains a method or constructor
     * @return {@link String} containing arguments names representation for {@code executable}
     */
    private String getExecutableArgumentsNames(Executable executable) {
        Namer namer = new Namer();
        return "(" + Arrays.stream(executable.getParameterTypes())
                .map(c -> namer.getName())
                .collect(Collectors.joining(", ")) + ")";

    }

    /**
     * Returns exceptions representation for given {@link Method} or {@link Constructor}.
     * If {@code executable} do not throws exceptions, returns empty string
     * @see #combineIfNotEmpty
     * @param executable instance of {@link Executable} which contains a method or constructor
     * @return {@link String} containing exceptions representation for {@code executable}
     */
    private String getExecutableExceptions(Executable executable) {
        return combineIfNotEmpty("throws", Arrays.stream(executable.getExceptionTypes())
                .map(Class::getCanonicalName)
                .collect(Collectors.joining(", ")));
    }

    /** Returns default return value representation for {@link Method#getReturnType()} given
     * @param token class instance containing method return value type
     * @return {@link String} containing default return value for method given
     */
    private String getDefaultValue(Class<?> token) {
        if (!token.isPrimitive()) {
            return "null";
        } else if (token.equals(void.class)) {
            return "";
        } else if (token.equals(boolean.class)) {
            return "false";
        } else {
            return "0";
        }
    }

    /**
     * Returns method body implementation for {@code method}.
     * Method will return default value due to its return value type
     * @see #getDefaultValue(Class)
     * @param method to get body implementation for
     * @return {@link String} containing method body implementation
     */
    private String getMethodBody(Method method) {
        return "return " + getDefaultValue(method.getReturnType()) + ";";
    }

    /** Returns {@code method} implementation containing
     * declaration line with modifiers, name, arguments and exceptions, and
     * full method body
     * @param method method to get implementation for
     * @return {@link String} containing full method implementation
     */
    private String getMethod(Method method) {
        return combineWithSpaces(getExecutableModifiers(method), method.getReturnType().getCanonicalName(),
                method.getName() + getExecutableArguments(method), getExecutableExceptions(method), "{", getMethodBody(method), "}");
    }

    private String getMethodsFromClass(Class<?> token) {
        String ans = "";
        for (Method method : token.getMethods()) {
            if (Modifier.isAbstract(method.getModifiers())) {
                ans = combineBlocks(ans, getMethod(method));
            }
        }
        return ans;
    }

    /**
     * Returns all abstract methods implementations
     * specified by {@link #getMethod(Method)} for {@code token} class and its superclasses
     *
     * @param token class to get methods for
     * @return {@link String} containing methods implementations
     * @throws ImplerException if there is a private superclass for {@code token} class
     */
    private String getMethods(Class<?> token) throws ImplerException {
        String ans = getMethodsFromClass(token);
        for (; token != null; token = token.getSuperclass()) {
            if (Modifier.isPrivate(token.getModifiers())) {
                throw new ImplerException("Cannot extend class with private super interface");
            }
            for (Method method : token.getDeclaredMethods()) {
                if (Modifier.isAbstract(method.getModifiers()) && (Modifier.isPrivate(method.getModifiers()) || Modifier.isProtected(method.getModifiers()))) {
                    ans = combineBlocks(ans, getMethod(method));
                }
            }
        }
        return ans;
    }

    /**
     * Returns constructor body implementation for {@code constructor} using super constructor
     * @param constructor to get body implementation for
     * @return {@link String} containing constructor body implementation
     */
    private String getConstructorBody(Constructor constructor) {
        return "super" + getExecutableArgumentsNames(constructor) + ";";
    }


    /**
     * Returns {@code constructor} implementation containing
     * declaration line with modifiers, name, arguments and exceptions and,
     * full constructor body
     * @param constructor constructor to get implementation for
     * @return {@link String} containing full constructor implementation
     */
    private String getConstructor(Constructor constructor) {
        return combineWithSpaces(getExecutableModifiers(constructor),
                getClassName(constructor.getDeclaringClass()) + getExecutableArguments(constructor),
                getExecutableExceptions(constructor), "{", getConstructorBody(constructor), "}");
    }

    /**
     * Returns all non-private declared constructors implementations
     * specified by {@link #getConstructor(Constructor)} for {@code token} class
     *
     * @param token class to get constructors for
     * @return {@link String} containing constructors implementations
     * @throws ImplerException if there is no non-private constructor for {@code token} class
     */
    private String getConstructors(Class<?> token) throws ImplerException {
        if (token.isInterface()) {
            return "";
        }

        String ans = "";

        for (Constructor constructor : token.getDeclaredConstructors()) {
            if (!Modifier.isPrivate(constructor.getModifiers())) {
                ans = combineBlocks(ans, getConstructor(constructor));
            }
        }
        if (ans.equals("")) {
            throw new ImplerException("Class has only private constructors");
        }
        return ans;
    }

    /**
     * Returns full body implementation for {@code token} contatinig all methods and constructors
     *
     * @param token type token to create implementation for.
     * @return {@link String} containing full body of {@code token} implementation
     * @throws ImplerException if there is no non-private constructors for {@code token} or if {@code token} has private superclass
     */
    private String getClassImpl(Class<?> token) throws ImplerException {
        return combineBlocks(getClassDef(token) + " {", getConstructors(token), getMethods(token), "}");
    }

    /** Returns name of the class, which implements given {@code token} class
     * @param token class to implement for
     * @return {@link String} name of the new class
     */
    private String getClassName(Class<?> token) {
        return token.getSimpleName() + "Impl";
    }

    private String unicodeFilter(String s) {
        StringBuilder stringBuilder = new StringBuilder();
        for (char c : s.toCharArray()) {
            stringBuilder.append(c >= 128 ? String.format("\\u%04X", (int) c) : c);
        }
        return stringBuilder.toString();
    }

    /**
     * Creates new class, which implements given {@code token} class
     * and store it along {@code root} directory
     *
     * @param token type token to create implementation for.
     * @param root  root directory.
     * @throws ImplerException if implementation fails for some reason
     */
    @Override
    public void implement(Class<?> token, Path root) throws ImplerException {
        if (token.isPrimitive() || token.isArray() || token == Enum.class ||
                Modifier.isFinal(token.getModifiers()) || Modifier.isPrivate(token.getModifiers())) {

            throw new ImplerException("Unsupported class token given");
        }

        Path outfile;
        try {
            outfile = root.resolve(Path.of(token.getPackageName().replace('.', File.separatorChar),
                    getClassName(token) + ".java"));
            createPath(outfile);
        } catch (InvalidPathException | IOException e) {
            throw new ImplerException(e.getMessage());
        }
        try (BufferedWriter bw = Files.newBufferedWriter(outfile)) {
            bw.write(unicodeFilter(combineBlocks(getPackageInfo(token), getClassImpl(token))));
        } catch (IOException e) {
            throw new ImplerException(e.getMessage());
        }
    }

    /**
     * Compiles class, which implements given {@code token} class, and stores
     * the resulting <code>.class</code> along {@code path} directory
     * @param token type token to create implementation for
     * @param path directory to store compiled file
     * @throws ImplerException if compilation fails for some reason
     */
    private void compile(Class<?> token, Path path) throws ImplerException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new ImplerException("Can not find java compiler");
        }

        Path originPath;
        try {
            CodeSource codeSource = token.getProtectionDomain().getCodeSource();
            String uri = codeSource == null ? "" : codeSource.getLocation().getPath();
            if (uri.startsWith("/")) {
                uri = uri.substring(1);
            }
            originPath = Path.of(uri);
        } catch (InvalidPathException e) {
            throw new ImplerException(String.format("Can not find valid class path: %s", e));
        }
        String[] cmdArgs = new String[]{
                "-cp",
                path.toString() + File.pathSeparator + originPath.toString(),
                Path.of(path.toString(), token.getPackageName().replace('.', File.separatorChar), getClassName(token) + ".java").toString()
        };
        if (compiler.run(null, null, null, cmdArgs) != 0) {
            throw new ImplerException("Can not compile generated code");
        }
    }

    /**
     * Creates <code>.jar</code> file containing compiled by {@link #compile(Class, Path)}
     * implementation of {@code token}
     * @param token class that was implemented
     * @param jarPath directory to store <code>.jar</code> file
     * @param tmpPath directory containing compiled implementation of {@code token}
     * @throws ImplerException if error occurs during {@link JarOutputStream} work
     */
    private void jar(Class<?> token, Path jarPath, Path tmpPath) throws ImplerException {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");

        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
            String name = token.getName().replace('.', '/') + "Impl.class";
            jos.putNextEntry(new ZipEntry(name));
            Files.copy(Paths.get(tmpPath.toString(), name), jos);
        } catch (IOException e) {
            throw new ImplerException(e.getMessage());
        }
    }

    /**
     * Implements {@code token} class specified by {@link #implement(Class, Path)},
     * compiles implementation using {@link #compile(Class, Path)} and
     * creates <code>.jar</code> file containing compiled implementation using {@link #jar(Class, Path, Path)}
     * @param token   type token to create implementation for.
     * @param jarFile target <tt>.jar</tt> file.
     * @throws ImplerException if implementation fails for some reason
     */
    @Override
    public void implementJar(Class<?> token, Path jarFile) throws ImplerException {
        Path tmp;
        try {
            createPath(jarFile);
            tmp = Files.createTempDirectory(jarFile.toAbsolutePath().getParent(), "tmp");
        } catch (IOException e) {
            throw new ImplerException(e.getMessage());
        }
        try {
            implement(token, tmp);
            compile(token, tmp);
            jar(token, jarFile, tmp);
        } finally {
            try {
                Files.walkFileTree(tmp, new Deleter());
            } catch (IOException e) {
                System.err.println("Failed to delete temporary directory");
            }
        }
    }
}
