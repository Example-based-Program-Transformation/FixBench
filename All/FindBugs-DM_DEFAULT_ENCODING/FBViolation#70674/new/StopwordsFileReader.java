/*
 * Copyright (C) 2014 Jens Bertram <code@jens-bertram.net>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.unihildesheim.iw.tools;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Simple file reader to build a list of stopwords.
 *
 * @author Jens Bertram
 */
public class StopwordsFileReader {

  /**
   * Private empty constructor for utility class.
   */
  private StopwordsFileReader() {
  }

  public static Set<String> readWords(final Format format,
      final String source, final Charset cs)
      throws IOException {
    Objects.requireNonNull(format, "Format was null.");
    if (Objects.requireNonNull(source, "Source was null.").trim().isEmpty()) {
      throw new IllegalArgumentException("Empty source.");
    }

    Set<String> words;
    try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(new FileInputStream(source), cs))) {

      words = new HashSet<>();

      String line;
      while ((line = reader.readLine()) != null) {
        line = line.trim();

        // ignore empty lines
        if (line.isEmpty()) {
          continue;
        }

        // skip snowball comment lines
        if (Format.SNOWBALL.equals(format) && line.charAt(0) == '|') {
          continue;
        }

        // add the first word
        words.add(line.split(" ", 2)[0]);
      }
    }
    return words;
  }

  /**
   * Tries to get the format type from the given string.
   *
   * @param format String naming the format
   * @return Format or null, if none is matching
   */
  public static Format getFormatFromString(final String format) {
    if (Objects.requireNonNull(format, "Format was null.").trim().isEmpty()) {
      throw new IllegalArgumentException("Format type string was empty.");
    }
    if (Format.PLAIN.name().equalsIgnoreCase(format)) {
      return Format.PLAIN;
    } else if (Format.SNOWBALL.name().equalsIgnoreCase(format)) {
      return Format.SNOWBALL;
    }
    return null;
  }

  public enum Format {
    /**
     * Plain file with one word per line.
     */
    PLAIN,
    /**
     * Snowball format. Comments starting with | (pipe).
     */
    SNOWBALL
  }
}
