package com.psddev.dari.util;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;

/** Debug servlet for inspecting {@linkplain Settings global settings}. */
@DebugFilter.Path("settings")
public class SettingsDebugServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    // --- HttpServlet support ---

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        new DebugFilter.PageWriter(getServletContext(), request, response) {{
            startPage("Settings");

            writeStart("table", "class", "table table-condensed table-striped");
                writeStart("thead");
                    writeStart("tr");
                        writeStart("th").writeHtml("Key").writeEnd();
                        writeStart("th").writeHtml("Value").writeEnd();
                        writeStart("th").writeHtml("Class").writeEnd();
                    writeEnd();
                writeEnd();

                writeStart("tbody");
                    for (Map.Entry<String, Object> entry : flatten(Settings.asMap()).entrySet()) {
                        String key = entry.getKey();
                        String keyLowered = key.toLowerCase(Locale.ENGLISH);
                        Object value = entry.getValue();

                        writeStart("tr");
                            writeStart("td").writeHtml(key).writeEnd();

                            writeStart("td");
                            if (keyLowered.contains("password") ||
                                    keyLowered.contains("secret")) {
                                writeStart("span", "class", "label label-warning").writeHtml("Hidden").writeEnd();
                            } else {
                                writeHtml(value);
                            }

                            writeStart("td");
                                writeHtml(value != null ? value.getClass().getName() : "N/A");
                            writeEnd();
                        writeEnd();
                    }
                writeEnd();
            writeEnd();

            endPage();
        }};
    }

    private Map<String, Object> flatten(Map<String, Object> map) {
        Map<String, Object> flattened = new TreeMap<String, Object>(String.CASE_INSENSITIVE_ORDER);
        collectFlattenedValues(flattened, null, map);
        return flattened;
    }

    private void collectFlattenedValues(Map<String, Object> flattened, String key, Object value) {
        if (value instanceof Map) {
            String prefix = key == null ? "" : key + "/";
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                collectFlattenedValues(flattened, prefix + entry.getKey(), entry.getValue());
            }

        } else if (value instanceof DataSource) {
            try {
                Map<String, Object> map = new TreeMap<String, Object>();

                for (PropertyDescriptor desc : Introspector.getBeanInfo(value.getClass()).getPropertyDescriptors()) {
                    try {
                        Method getter = desc.getReadMethod();
                        Method setter = desc.getWriteMethod();

                        if (getter != null && setter != null) {
                            getter.setAccessible(true);
                            map.put(desc.getName(), getter.invoke(value));
                        }
                    } catch (Exception error) {
                    }
                }

                collectFlattenedValues(flattened, key, map);

            } catch (IntrospectionException error) {
                flattened.put(key, value);
            }

        } else {
            flattened.put(key, value);
        }
    }
}
