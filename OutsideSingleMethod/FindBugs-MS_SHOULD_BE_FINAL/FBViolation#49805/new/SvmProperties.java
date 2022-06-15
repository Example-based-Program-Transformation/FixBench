package ch.metzenthin.svm.common.utils;

import java.io.*;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Properties;
import java.util.TreeSet;


/**
 * @author Martin Schraner
 */
public class SvmProperties {

    public static final String SVM_PROPERTIES_FILE_NAME = System.getProperty("user.home") + File.separator + ".svm";
    public static final String KEY_TEMPLATES_DIRECTORY = "templates_directory";
    public static final String KEY_DEFAULT_OUTPUT_DIRECTORY = "default_output_directory";
    public static final String KEY_ABSENZENLISTE_TEMPLATE = "absenzenlisten_template";

    public static void createSvmPropertiesFileDefault() {

        Properties prop = new Properties() {
            // Alphabetische statt zufällige Sortierung der Properties-Einträge
            @Override
            public synchronized Enumeration<Object> keys() {
                return Collections.enumeration(new TreeSet<>(super.keySet()));
            }
        };

        OutputStream propertiesFile;
        File f = new File(SVM_PROPERTIES_FILE_NAME);
        if (!f.exists()) {
            try {
                propertiesFile = new FileOutputStream(SVM_PROPERTIES_FILE_NAME);

                // set the properties value
                prop.setProperty(KEY_TEMPLATES_DIRECTORY, System.getProperty("user.dir") + File.separator + "Listen-Templates");
                prop.setProperty(KEY_DEFAULT_OUTPUT_DIRECTORY, System.getProperty("user.home") + File.separator + "Desktop");
                prop.setProperty(KEY_ABSENZENLISTE_TEMPLATE, "<Schuljahr>" + File.separator + "Semester_<Semester>" + File.separator + "Absenzenliste-Template_<Schuljahr>_<Semester>_<Wochentag>.docx");
                prop.store(propertiesFile, null);

                propertiesFile.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static Properties getSvmProperties() {
        Properties prop = new Properties();
        InputStream propertiesFile;
        try {
            propertiesFile = new FileInputStream(SVM_PROPERTIES_FILE_NAME);
            prop.load(propertiesFile);
            propertiesFile.close();
        } catch (IOException ex) {
            throw new RuntimeException();
        }
        return prop;
    }
}
