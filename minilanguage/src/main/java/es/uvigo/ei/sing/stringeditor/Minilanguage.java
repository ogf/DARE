package es.uvigo.ei.sing.stringeditor;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.ReflectPermission;
import java.net.MalformedURLException;
import java.nio.charset.Charset;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.CodeSource;
import java.security.Permissions;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.util.PropertyPermission;

import org.apache.bsf.BSFEngine;
import org.apache.bsf.BSFException;
import org.apache.bsf.BSFManager;
import org.w3c.dom.Document;

public class Minilanguage {

    private static BSFEngine engine;

    static {
        BSFManager.registerScriptingEngine("ruby",
                "org.jruby.javasupport.bsf.JRubyEngine", new String[] { "rb" });
        try {
            engine = new BSFManager().loadScriptingEngine("ruby");
            engine.exec("ruby", 1, 1, extractString(loadFile("transformer.rb")));
        } catch (BSFException e) {
            throw new RuntimeException(e);
        }
    }

    private static InputStreamReader loadFile(String fileName) {
        return new InputStreamReader(
                Minilanguage.class.getResourceAsStream(fileName));
    }

    public static Transformer eval(File file) {
        return eval(file, Charset.forName("utf8"));
    }

    public static Transformer eval(File file, Charset charset) {
        try {
            return eval(new BufferedReader(new InputStreamReader(
                    new FileInputStream(file), charset)), file.getName());
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public static Transformer eval(Reader reader) {
        return eval(extractString(reader));
    }

    public static Transformer eval(Reader reader, String fileName) {
        return eval(extractString(reader), fileName, null);
    }

    public static Transformer eval(Reader reader, String fileName, Integer line) {
        return eval(extractString(reader), fileName, line);
    }

    public static Transformer eval(String minilanguageProgram, String fileName,
            Integer lineNumber) {
        return XMLInputOutput.loadTransformer(callScriptFunction(
                Document.class, "get_xml", minilanguageProgram, fileName,
                lineNumber));
    }

    private static String extractString(Reader reader) {
        StringBuilder result = new StringBuilder();
        char[] buffer = new char[1024];
        int read = -1;
        try {
            while ((read = reader.read(buffer)) != -1) {
                result.append(buffer, 0, read);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                reader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return result.toString();
    }

    public static Transformer eval(String minilanguageProgram) {
        return eval(minilanguageProgram, null, null);
    }

    public static String[] exec(String minilanguageProgram, String... input) {
        return Util.runRobot(Minilanguage.eval(minilanguageProgram), input);
    }

    public static String[] exec(File minilanguageProgram, String... input) {
        return Util.runRobot(Minilanguage.eval(minilanguageProgram), input);
    }

    public static String[] exec(Reader minilanguageProgram, String... input) {
        return Util.runRobot(Minilanguage.eval(minilanguageProgram), input);
    }

    public static String[] exec(Reader minilanguageProgram, String name,
            int line, String... input) {
        return Util.runRobot(
                Minilanguage.eval(minilanguageProgram, name, line), input);
    }

    public static String xmlToLanguage(File file) {
        try {
            return xmlToLanguage(new BufferedInputStream(new FileInputStream(
                    file)));
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public static String xmlToLanguage(InputStream inputStream) {
        return xmlToLanguage(XMLInputOutput.asDoc(inputStream));
    }

    public static String xmlToLanguage(Document document) {
        return callScriptFunction(String.class, "to_minilanguage", document);
    }

    private static <T> T callScriptFunction(final Class<T> klass,
            final String function, final Object... parameters) {
        return AccessController.doPrivileged(new PrivilegedAction<T>() {

            @Override
            public T run() {
                try {
                    return klass.cast(engine.call(null, function, parameters));
                } catch (BSFException e) {
                    throw new RuntimeException(e);
                }
            }
        }, sandboxContext());
    }

    private static AccessControlContext CACHED_CONTEXT = null;

    private static AccessControlContext sandboxContext() {
        if (CACHED_CONTEXT != null) {
            return CACHED_CONTEXT;
        }
        CodeSource dummyCodeSource = new CodeSource(null, new Certificate[0]);
        AccessControlContext result = new AccessControlContext(
                new ProtectionDomain[] { new ProtectionDomain(dummyCodeSource,
                        permissionsNeededForJRuby()) });
        return CACHED_CONTEXT = result;
    }

    private static Permissions permissionsNeededForJRuby() {
        Permissions result = new Permissions();
        result.add(new PropertyPermission("jruby.*", "read"));
        result.add(new RuntimePermission("accessDeclaredMembers"));
        result.add(new ReflectPermission("suppressAccessChecks"));
        return result;
    }

    public static void main(String[] args) throws MalformedURLException,
            IOException {

        for (String s : Minilanguage.exec(
                "url | xpath('//a/@href') | patternMatcher('(http://.*)') ",
                "http://www.google.com")) {
            System.out.println(s);
        }
        // String asLanguage = Minilanguage.xmlToLanguage(new URL(
        // "http://sing.ei.uvigo.es:8080/aautomator/_microRNA.xml")
        // .openConnection().getInputStream());
        // System.out.println(asLanguage);
        // try {
        // String[] exec = Minilanguage.exec(
        // "url | xpath('ññ//asdfasf/@asdfafd') ",
        // "http://www.google.es");
        // System.out.println(Arrays.toString(exec));
        // } catch (Exception e) {
        // e.printStackTrace();
        // }

    }

}
