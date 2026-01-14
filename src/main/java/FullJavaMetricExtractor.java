import com.github.javaparser.*;
import com.github.javaparser.ast.*;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.resolution.*;
import com.github.javaparser.resolution.declarations.*;
import com.github.javaparser.resolution.types.*;
import com.github.javaparser.symbolsolver.*;
import com.github.javaparser.symbolsolver.resolution.typesolvers.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class FullJavaMetricExtractor {

    static class Metrics {
        String className;
        int LOC = 0;
        int Methods = 0;
        int Fields = 0;
        int WMC = 0;
        int DIT = 0;
        int NOC = 0;
        int NMO = 0;
        boolean isAbstract = false;
    }

    static Map<String, Metrics> metricsMap = new HashMap<>();
    static Map<String, Set<String>> inheritanceTree = new HashMap<>();

    public static void main(String[] args) throws Exception {

//        if (args.length < 1) {
//            System.out.println("Usage: java JavaMetricsExtractor <src_root>");
//            return;
//        }

        Path srcRoot = Paths.get("/Users/yosuayerikho/IdeaProjects/DepedencyExtractor/java_project/wro4j/wro4j-core/src/main/java/ro/isdc/wro");

        CombinedTypeSolver solver = new CombinedTypeSolver();
        solver.add(new ReflectionTypeSolver());
        solver.add(new JavaParserTypeSolver(srcRoot));

        ParserConfiguration config = new ParserConfiguration()
                .setSymbolResolver(new JavaSymbolSolver(solver));

        StaticJavaParser.setConfiguration(config);

        Files.walk(srcRoot)
                .filter(p -> p.toString().endsWith(".java"))
                .forEach(FullJavaMetricExtractor::parseFile);

        calculateDITandNOC();
        printResults();
    }

    static void parseFile(Path path) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(path);

            cu.findAll(ClassOrInterfaceDeclaration.class).forEach(cls -> {
                String name = cls.getNameAsString();
                Metrics m = new Metrics();
                m.className = name;
                m.isAbstract = cls.isAbstract();

                // LOC
                m.LOC = cls.toString().split("\n").length;

                // Fields
                m.Fields = cls.getFields().stream()
                        .mapToInt(f -> f.getVariables().size())
                        .sum();

                // Methods + WMC + NMO
                for (MethodDeclaration md : cls.getMethods()) {
                    m.Methods++;
                    int complexity = calculateCyclomaticComplexity(md);
                    m.WMC += complexity;

                    if (md.getAnnotations().stream()
                            .anyMatch(a -> a.getNameAsString().equals("Override"))) {
                        m.NMO++;
                    }
                }

                metricsMap.put(name, m);

                // Inheritance tracking
                try {
                    ResolvedReferenceTypeDeclaration resolved =
                            cls.resolve();

                    inheritanceTree.putIfAbsent(name, new HashSet<>());

                    for (ResolvedReferenceType ancestor : resolved.getAncestors()) {
                        Optional<ResolvedReferenceTypeDeclaration> decl =
                                ancestor.getTypeDeclaration();
                        if (decl.isPresent()) {
                            inheritanceTree
                                    .computeIfAbsent(
                                            decl.get().getName(),
                                            k -> new HashSet<>()
                                    )
                                    .add(name);
                        }
                    }

                } catch (Exception ignored) {
                }
            });

        } catch (Exception e) {
            System.err.println("Failed to parse: " + path);
        }
    }

    static int calculateCyclomaticComplexity(MethodDeclaration md) {
        int complexity = 1;

        complexity += md.findAll(IfStmt.class).size();
        complexity += md.findAll(ForStmt.class).size();
        complexity += md.findAll(WhileStmt.class).size();
        complexity += md.findAll(DoStmt.class).size();
        complexity += md.findAll(CatchClause.class).size();
        complexity += md.findAll(ConditionalExpr.class).size();

        for (BinaryExpr be : md.findAll(BinaryExpr.class)) {
            if (be.getOperator() == BinaryExpr.Operator.AND ||
                    be.getOperator() == BinaryExpr.Operator.OR) {
                complexity++;
            }
        }

        return complexity;
    }

    static void calculateDITandNOC() {
        for (String cls : metricsMap.keySet()) {
            Metrics m = metricsMap.get(cls);
            m.NOC = inheritanceTree.getOrDefault(cls, Set.of()).size();
            m.DIT = calculateDIT(cls);
        }
    }

    static int calculateDIT(String className) {
        int dit = 0;
        String current = className;

        while (true) {
            boolean foundParent = false;

            for (Map.Entry<String, Set<String>> e : inheritanceTree.entrySet()) {
                if (e.getValue().contains(current)) {
                    dit++;
                    current = e.getKey();
                    foundParent = true;
                    break;
                }
            }
            if (!foundParent) break;
        }
        return dit;
    }

    static void printResults() {
        System.out.println(
                "Class,LOC,Methods,Fields,WMC,DIT,NOC,NMO,Abstract");

        metricsMap.values().forEach(m -> {
            System.out.println(
                    m.className + "," +
                            m.LOC + "," +
                            m.Methods + "," +
                            m.Fields + "," +
                            m.WMC + "," +
                            m.DIT + "," +
                            m.NOC + "," +
                            m.NMO + "," +
                            (m.isAbstract ? 1 : 0)
            );
        });
    }
}
