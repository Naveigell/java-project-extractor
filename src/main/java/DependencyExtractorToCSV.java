import com.github.javaparser.*;
import com.github.javaparser.ast.*;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;

import java.io.*;
import java.util.*;

public class DependencyExtractorToCSV {

    static List<String> classList = new ArrayList<>();
    static Map<String, Integer> indexMap = new HashMap<>();

    static int[][] CA, CI, CM, MM;

    public static void main(String[] args) throws Exception {

        Map<String, String> projects = new HashMap<>();

        projects.put("ant", "/Users/yosuayerikho/IdeaProjects/DepedencyExtractor/java_project/ant/src/main/org/apache/tools/ant");
        projects.put("argouml", "/Users/yosuayerikho/IdeaProjects/DepedencyExtractor/java_project/argouml/src/argouml-app/src/org/argouml");
        projects.put("jEdit", "/Users/yosuayerikho/IdeaProjects/DepedencyExtractor/java_project/jEdit/org/gjt/sp/jedit");
        projects.put("jhotdraw", "/Users/yosuayerikho/IdeaProjects/DepedencyExtractor/java_project/jhotdraw/jhotdraw-core/src/main/java/org/jhotdraw/draw");
        projects.put("jmeter", "/Users/yosuayerikho/IdeaProjects/DepedencyExtractor/java_project/jmeter/src/core/src/main/java/org/apache/jmeter");
        projects.put("wro4j", "/Users/yosuayerikho/IdeaProjects/DepedencyExtractor/java_project/wro4j/wro4j-core/src/main/java/ro/isdc/wro");

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

            // indexing class names
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
                String src = clazz.getNameAsString();
                int i = indexMap.get(src);

                extractCA(clazz, i);
                extractCI(clazz, i);
                extractCM(clazz, i);
                extractMM(clazz, i);
            }

            writeCSV(OUTPUT_DIR + "/CAInteractionMatrix.csv", CA);
            writeCSV(OUTPUT_DIR + "/CIInteractionMatrix.csv", CI);
            writeCSV(OUTPUT_DIR + "/CMInteractionMatrix.csv", CM);
            writeCSV(OUTPUT_DIR + "/MMInteractionMatrix.csv", MM);

            System.out.printf("CSV %s created.%n", entry.getKey());
        }
    }

    // ===================== PARSER =====================

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
                    System.err.println("Skip file: " + f.getPath());
                }
            }
        }
    }

    static void extractCA(ClassOrInterfaceDeclaration clazz, int i) {
        for (FieldDeclaration field : clazz.getFields()) {
            String typeName = field.getElementType().asString();
            addEdge(i, typeName, CA);
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
            call.getScope().ifPresent(scope ->
                    addEdge(i, scope.toString(), MM)
            );
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

        if (indexMap.containsKey(clean)) {
            int j = indexMap.get(clean);
            matrix[i][j]++;
        }
    }

    static void writeCSV(String path, int[][] matrix) throws Exception {
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
