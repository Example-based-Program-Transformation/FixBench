package ch.metzenthin.svm.dataTypes;

/**
 * @author Martin Schraner
 */
public class Schuljahre {

    public final static int SCHULJAHR_VALID_MIN = 2000;
    public final static int SCHULJAHR_VALID_MAX = 2049;

    public String[] getSchuljahre() {
        String[] schuljahre = new String[SCHULJAHR_VALID_MAX - SCHULJAHR_VALID_MIN + 1];
        for (int i = 0; i < SCHULJAHR_VALID_MAX - SCHULJAHR_VALID_MIN; i++) {
            int schuljahr1 = SCHULJAHR_VALID_MIN + i;
            int schuljahr2 = schuljahr1 + 1;
            schuljahre[i] = schuljahr1 + "/" + schuljahr2;
        }
        return schuljahre;
    }

    public static String getPreviousSchuljahr(String schuljahr) {
        int schuljahr1 = Integer.parseInt(schuljahr.substring(0, 4)) - 1;
        int schuljahr2 = Integer.parseInt(schuljahr.substring(5, 9)) - 1;
        return schuljahr1 + "/" + schuljahr2;
    }

}
