import com.github.javaparser.*;
import com.github.javaparser.ast.*;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.type.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class DependencyExtractorToCSV {

    static List<String> classList = new ArrayList<>();
    static Map<String, Integer> indexMap = new HashMap<>();

    static int[][] CA; // Class-Attribute + Object Creation
    static int[][] CI; // Class Inheritance / Implementation
    static int[][] CM; // Class-Method Signature
    static int[][] MM; // Method-Method (via method call scope)

    public static void main(String[] args) throws Exception {

        Map<String, String> projects = new LinkedHashMap<>();

//        projects.put("ant", "/Users/yosuayerikho/IdeaProjects/DepedencyExtractor/java_project/ant/src/main/org/apache/tools/ant");
//        projects.put("argouml", "/Users/yosuayerikho/IdeaProjects/DepedencyExtractor/java_project/argouml/src/argouml-app/src/org/argouml");
//        projects.put("jEdit", "/Users/yosuayerikho/IdeaProjects/DepedencyExtractor/java_project/jEdit/org/gjt/sp/jedit");
//        projects.put("jhotdraw", "/Users/yosuayerikho/IdeaProjects/DepedencyExtractor/java_project/jhotdraw/jhotdraw-core/src/main/java/org/jhotdraw/draw");
//        projects.put("jmeter", "/Users/yosuayerikho/IdeaProjects/DepedencyExtractor/java_project/jmeter/src/core/src/main/java/org/apache/jmeter");
//        projects.put("wro4j", "/Users/yosuayerikho/IdeaProjects/DepedencyExtractor/java_project/wro4j/wro4j-core/src/main/java/ro/isdc/wro");
        projects.put("paper", "/Users/yosuayerikho/IdeaProjects/DepedencyExtractor/java_project/Paper/src/main/java");

        ParserConfiguration config = new ParserConfiguration();
        StaticJavaParser.setConfiguration(config);

        for (Map.Entry<String, String> entry : projects.entrySet()) {

            String SOURCE_ROOT = entry.getValue();
            String OUTPUT_DIR  = "output/" + entry.getKey();
            new File(OUTPUT_DIR).mkdirs();

            classList.clear();
            indexMap.clear();

            List<ClassOrInterfaceDeclaration> classes = new ArrayList<>();
            parseFolder(new File(SOURCE_ROOT), classes);

            for (ClassOrInterfaceDeclaration c : classes) {
                String name = c.getNameAsString();
                if (!indexMap.containsKey(name)) {
                    indexMap.put(name, classList.size());
                    classList.add(name);
                }
            }

            int n = classList.size();
            CA = new int[n][n];
            CI = new int[n][n];
            CM = new int[n][n];
            MM = new int[n][n];

            for (ClassOrInterfaceDeclaration clazz : classes) {
                int i = indexMap.get(clazz.getNameAsString());

                extractCA(clazz, i);
                extractCI(clazz, i);
                extractCM(clazz, i);
                extractMM(clazz, i);
            }

            writeCSV(OUTPUT_DIR + "/CAInteractionMatrix.csv", CA);
            writeCSV(OUTPUT_DIR + "/CIInteractionMatrix.csv", CI);
            writeCSV(OUTPUT_DIR + "/CMInteractionMatrix.csv", CM);
            writeCSV(OUTPUT_DIR + "/MMInteractionMatrix.csv", MM);

            System.out.println("CSV created for project: " + entry.getKey());
        }
    }

    static void parseFolder(File folder, List<ClassOrInterfaceDeclaration> classes) {
        File[] files = folder.listFiles();
        if (files == null) return;

        for (File f : files) {
            if (f.isDirectory()) {
                parseFolder(f, classes);
            } else if (f.getName().endsWith(".java")) {
                try {
                    CompilationUnit cu = StaticJavaParser.parse(f);
                    classes.addAll(cu.findAll(ClassOrInterfaceDeclaration.class));
                } catch (Exception e) {
                    // intentionally skipped
                }
            }
        }
    }

    static void extractCA(ClassOrInterfaceDeclaration clazz, int i) {
        for (FieldDeclaration field : clazz.getFields()) {
            addEdge(i, field.getElementType().asString(), CA);
        }

        for (ObjectCreationExpr oce : clazz.findAll(ObjectCreationExpr.class)) {
            addEdge(i, oce.getType().asString(), CA);
        }
    }

    static void extractCI(ClassOrInterfaceDeclaration clazz, int i) {
        clazz.getExtendedTypes().forEach(t ->
                addEdge(i, t.getNameAsString(), CI)
        );
        clazz.getImplementedTypes().forEach(t ->
                addEdge(i, t.getNameAsString(), CI)
        );
    }

    static void extractCM(ClassOrInterfaceDeclaration clazz, int i) {
        for (MethodDeclaration m : clazz.getMethods()) {
            addEdge(i, m.getType().asString(), CM);
            for (Parameter p : m.getParameters()) {
                addEdge(i, p.getType().asString(), CM);
            }
        }
    }

    static void extractMM(ClassOrInterfaceDeclaration clazz, int i) {
        for (MethodCallExpr call : clazz.findAll(MethodCallExpr.class)) {
            call.getScope().ifPresent(scope -> {
                if (scope.isNameExpr()) {
                    addEdge(i, scope.asNameExpr().getNameAsString(), MM);
                }
            });
        }
    }

    static void addEdge(int i, String rawName, int[][] matrix) {
        if (rawName == null) return;

        String clean = rawName
                .replaceAll("<.*>", "")
                .replace("[]", "")
                .trim();

        if (clean.contains(".")) {
            clean = clean.substring(clean.lastIndexOf('.') + 1);
        }

        if (clean.isEmpty()) return;
        if (Character.isLowerCase(clean.charAt(0))) return;
        if (!indexMap.containsKey(clean)) return;

        int j = indexMap.get(clean);

        if (i == j) return;

        matrix[i][j]++;
    }

    static void writeCSV(String path, int[][] matrix) throws IOException {
        BufferedWriter bw = new BufferedWriter(new FileWriter(path));

        bw.write("Class");
        for (String c : classList) bw.write("," + c);
        bw.newLine();

        for (int i = 0; i < classList.size(); i++) {
            bw.write(classList.get(i));
            for (int j = 0; j < classList.size(); j++) {
                bw.write("," + matrix[i][j]);
            }
            bw.newLine();
        }
        bw.close();
    }
}
