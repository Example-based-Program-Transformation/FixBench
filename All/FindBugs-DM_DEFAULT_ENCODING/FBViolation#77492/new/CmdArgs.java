/*
 *  Copyright 2012 Peter Karich 
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.util;

import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Stores command line options in a mapped. The case of the key is ignored.
 *
 * @author Peter Karich
 */
public class CmdArgs {

    private final Map<String, String> map;

    public CmdArgs() {
        this(new LinkedHashMap<String, String>(5));
    }

    public CmdArgs(Map<String, String> map) {
        this.map = map;
    }

    public CmdArgs put(String key, String str) {
        map.put(key.toLowerCase(), str);
        return this;
    }

    public long getLong(String key, long _default) {
        String str = get(key);
        if (!Helper.isEmpty(str)) {
            try {
                return Long.parseLong(str);
            } catch (Exception ex) {
            }
        }
        return _default;
    }

    public int getInt(String key, int _default) {
        String str = get(key);
        if (!Helper.isEmpty(str)) {
            try {
                return Integer.parseInt(str);
            } catch (Exception ex) {
            }
        }
        return _default;
    }

    public boolean getBool(String key, boolean _default) {
        String str = get(key);
        if (!Helper.isEmpty(str)) {
            try {
                return Boolean.parseBoolean(str);
            } catch (Exception ex) {
            }
        }
        return _default;
    }

    public double getDouble(String key, double _default) {
        String str = get(key);
        if (!Helper.isEmpty(str)) {
            try {
                return Double.parseDouble(str);
            } catch (Exception ex) {
            }
        }
        return _default;
    }

    public String get(String key, String _default) {
        String str = get(key);
        if (Helper.isEmpty(str))
            return _default;
        return str;
    }

    String get(String key) {
        if (Helper.isEmpty(key))
            return "";
        return map.get(key.toLowerCase());
    }

    public static CmdArgs readFromConfig(String fileStr) throws IOException {
        Map<String, String> map = new LinkedHashMap<String, String>();
        Helper.loadProperties(map, new InputStreamReader(new FileInputStream(fileStr), "UTF-8"));
        CmdArgs args = new CmdArgs();
        args.merge(map);
        return args;
    }

    public static CmdArgs read(String[] args) {
        Map<String, String> map = new LinkedHashMap<String, String>();
        for (String arg : args) {
            String strs[] = arg.split("\\=");
            if (strs.length != 2)
                continue;

            String key = strs[0];
            if (key.startsWith("-")) {
                key = key.substring(1);
            }
            if (key.startsWith("-")) {
                key = key.substring(1);
            }
            String value = strs[1];
            map.put(key, value);
        }

        return new CmdArgs(map);
    }

    public CmdArgs merge(CmdArgs read) {
        return merge(read.map);
    }

    CmdArgs merge(Map<String, String> map) {
        for (Entry<String, String> e : map.entrySet()) {
            if (Helper.isEmpty(e.getKey()))
                continue;
            this.map.put(e.getKey().toLowerCase(), e.getValue());
        }
        return this;
    }

    @Override public String toString() {
        return map.toString();
    }
}