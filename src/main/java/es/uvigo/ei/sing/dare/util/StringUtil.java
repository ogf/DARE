package es.uvigo.ei.sing.dare.util;


public class StringUtil {

    public static String quote(String stringToBeQuoted) {
        return "'" + stringToBeQuoted + "'";
    }

    private StringUtil() {
        // utility class, not instantiable
    }

}
