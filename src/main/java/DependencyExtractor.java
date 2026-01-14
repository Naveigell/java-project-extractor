import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;

import java.io.File;
import java.io.PrintWriter;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class DependencyExtractor {

    public static void main(String[] args) throws Exception {

        if (args.length < 1) {
            System.out.println("Usage: java DependencyExtractor <source-folder>");
            return;
        }

        String sourceFolder = args[0];

        // SymbolSolver
        CombinedTypeSolver solver = new CombinedTypeSolver();
        solver.add(new ReflectionTypeSolver());
        solver.add(new JavaParserTypeSolver(new File(sourceFolder)));

        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(solver);
        StaticJavaParser.getConfiguration().setSymbolResolver(symbolSolver);

        // Walk all Java files
        List<Path> javaFiles = Files.walk(Paths.get(sourceFolder))
                .filter(f -> f.toString().endsWith(".java"))
                .collect(Collectors.toList());

        // Output CSV
        PrintWriter writer = new PrintWriter(new File("dependencies_edges.csv"));
        writer.println("source,target");

        System.out.println("Processing " + javaFiles.size() + " Java files...");

        for (Path file : javaFiles) {
            try {
                CompilationUnit cu = StaticJavaParser.parse(file);

                // Loop setiap class di file itu
                for (ClassOrInterfaceDeclaration clazz : cu.findAll(ClassOrInterfaceDeclaration.class)) {

                    String className = clazz.getNameAsString();

                    // Temukan method call
                    for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
                        try {
                            // Resolve class dari method yg dipanggil
                            String targetClass = call.resolve().getClassName();

                            // Ignore kalau memanggil dirinya sendiri
                            if (!targetClass.equals(className)) {

                                writer.println(className + "," + targetClass);
                                System.out.println(className + " -> " + targetClass);
                            }

                        } catch (Exception ignored) {
                        }
                    }
                }

            } catch (Exception e) {
                System.out.println("Error parsing " + file + ": " + e.getMessage());
            }
        }

        writer.close();
        System.out.println("DONE! Saved to dependencies_edges.csv");
    }
}
