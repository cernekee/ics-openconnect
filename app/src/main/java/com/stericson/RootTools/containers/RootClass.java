package com.stericson.RootTools.containers;

import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/* #ANNOTATIONS @SupportedAnnotationTypes("com.stericson.RootTools.containers.RootClass.Candidate") */
/* #ANNOTATIONS @SupportedSourceVersion(SourceVersion.RELEASE_6) */
public class RootClass /* #ANNOTATIONS extends AbstractProcessor */ {

    /* #ANNOTATIONS
    @Override
    public boolean process(Set<? extends TypeElement> typeElements, RoundEnvironment roundEnvironment) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, "I was invoked!!!");

        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }
    */

    enum READ_STATE { STARTING, FOUND_ANNOTATION; };

    public RootClass(String[] args) throws ClassNotFoundException, NoSuchMethodException,
            IllegalAccessException, InvocationTargetException, InstantiationException {

        String className = args[0];
        RootArgs actualArgs = new RootArgs();
        actualArgs.args = new String[args.length - 1];
        System.arraycopy(args, 1, actualArgs.args, 0, args.length - 1);
        Class<?> classHandler = Class.forName(className);
        Constructor<?> classConstructor = classHandler.getConstructor(RootArgs.class);
        classConstructor.newInstance(actualArgs);
    }

    public @interface Candidate {};

    public class RootArgs {
        public String args[];
    }

    static void displayError(Exception e) {
        // Not using system.err to make it easier to capture from
        // calling library.
        System.out.println("##ERR##" + e.getMessage() + "##");
        e.printStackTrace();
    }

    // I reckon it would be better to investigate classes using getAttribute()
    // however this method allows the developer to simply select "Run" on RootClass
    // and immediately re-generate the necessary jar file.
    static public class AnnotationsFinder {

        private final String AVOIDDIRPATH = "stericson" + File.separator + "RootTools" + File.separator;
        private List<File> classFiles;

        public AnnotationsFinder() {
            System.out.println("Discovering root class annotations...");
            classFiles = new ArrayList<File>();
            lookup(new File("src"), classFiles);
            System.out.println("Done discovering annotations. Building jar file.");
            File builtPath = getBuiltPath();
            if(null != builtPath) {
                // Android! Y U no have com.google.common.base.Joiner class?
                String rc1 = "com" + File.separator
                        + "stericson" + File.separator
                        + "RootTools" + File.separator
                        + "containers" + File.separator
                        + "RootClass.class";
                String rc2 = "com" + File.separator
                        + "stericson" + File.separator
                        + "RootTools" + File.separator
                        + "containers" + File.separator
                        + "RootClass$RootArgs.class";
                String [] cmd;
                boolean onWindows = (-1 != System.getProperty("os.name").toLowerCase().indexOf("win"));
                if(onWindows) {
                    StringBuilder sb = new StringBuilder(
                            " " + rc1 + " " + rc2
                    );
                    for(File file:classFiles) {
                        sb.append(" " + file.getPath());
                    }
                    cmd = new String[] {
                            "cmd", "/C",
                            "jar cvf" +
                            " anbuild.jar" +
                            sb.toString()
                    };
                }
                else {
                    ArrayList<String> al = new ArrayList<String>();
                    al.add("jar");
                    al.add("cf");
                    al.add("anbuild.jar");
                    al.add(rc1);
                    al.add(rc2);
                    for(File file:classFiles) {
                        al.add(file.getPath());
                    }
                    cmd = al.toArray(new String[al.size()]);
                }
                ProcessBuilder jarBuilder = new ProcessBuilder(cmd);
                jarBuilder.directory(builtPath);
                try {
                    jarBuilder.start().waitFor();
                } catch (IOException e) {} catch (InterruptedException e) {}

                System.out.println("Done building jar file. Creating dex file.");
                if(onWindows) {
                    cmd = new String[] {
                            "cmd", "/C",
                            "dx --dex --output=anbuild.dex anbuild.jar"
                    };
                }
                else {
                    cmd = new String[] {
                            "/Users/Chris/Projects/android-sdk-macosx/platform-tools/dx",
                            "--dex",
                            "--output=anbuild.dex",
                            "anbuild.jar"
                    };
                }
                ProcessBuilder dexBuilder = new ProcessBuilder(cmd);
                dexBuilder.directory(builtPath);
                try {
                    dexBuilder.start().waitFor();
                } catch (IOException e) {} catch (InterruptedException e) {}
            }
            System.out.println("All done. ::: Be sure to move anbuild.dex to your project's res/raw/ folder :::");
        }

        protected void lookup(File path, List<File> fileList) {
            File[] files = path.listFiles();
            for(File file:files) {
                if(file.isDirectory()) {
                    if(-1 == file.getAbsolutePath().indexOf(AVOIDDIRPATH)) {
                        lookup(file, fileList);
                    }
                }
                else {
                    if(file.getName().endsWith(".java")) {
                        if(hasClassAnnotation(file))
                            fileList.add(
                                    new File(
                                            file.getPath()
                                                    .replace("src/", "")
                                                    .replace(".java", ".class")));
                    }
                }
            }
        }

        protected boolean hasClassAnnotation(File file) {
            READ_STATE readState = READ_STATE.STARTING;
            Pattern p = Pattern.compile(" class ([A-Za-z0-9_]+)");
            try {
                BufferedReader reader = new BufferedReader(new FileReader(file));
                String line;
                while(null != (line = reader.readLine())) {
                    switch(readState) {
                        case STARTING:
                            if(-1 < line.indexOf("@RootClass.Candidate"))
                                readState = READ_STATE.FOUND_ANNOTATION;
                            break;
                        case FOUND_ANNOTATION:
                            Matcher m = p.matcher(line);
                            if(m.find()) {
                                System.out.println(" Found annotated class: " + m.group(0));
                                return true;
                            }
                            else {
                                System.err.println("Error: unmatched annotation in " +
                                        file.getAbsolutePath());
                                readState = READ_STATE.STARTING;
                            }
                            break;
                    }
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return false;
        }

        protected File getBuiltPath() {
            File foundPath = null;

            File ideaPath = new File("out" + File.separator + "production"); // IntelliJ
            if(ideaPath.isDirectory()) {
                File[] children = ideaPath.listFiles(new FileFilter() {
                    @Override
                    public boolean accept(File pathname) {
                        return pathname.isDirectory();
                    }
                });
                if(children.length > 0) {
                    foundPath = new File(ideaPath.getAbsolutePath() + File.separator + children[0].getName());
                }
            }
            if(null == foundPath) {
                File eclipsePath = new File("bin" + File.separator + "classes"); // Eclipse IDE
                if(eclipsePath.isDirectory()) {
                    foundPath = eclipsePath;
                }
            }

            return foundPath;
        }


    };

    public static void main (String [] args) {
        if(args.length == 0)
            new RootClass.AnnotationsFinder();
        else {
            try {
                new RootClass(args);
            } catch (Exception e) {
                displayError(e);
            }
        }
    }
}
