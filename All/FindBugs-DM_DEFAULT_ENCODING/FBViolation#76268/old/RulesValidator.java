/*
 * -----------------------------------------------------------------------\
 * PerfCake
 *  
 * Copyright (C) 2010 - 2013 the original author or authors.
 *  
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * -----------------------------------------------------------------------/
 */
package org.perfcake.validation;

import org.apache.log4j.Logger;
import org.perfcake.message.Message;
import org.w3c.dom.Element;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Validates the message with the defined Drools rules. There is a custom DSL file making the rules specification easier.
 *
 * @author Marek Baluch <baluchw@gmail.com>
 * @author Martin Večeřa <marvenec@gmail.com>
 */
public class RulesValidator implements MessageValidator {

   /**
    * Message property key set on the original message for the validator to denote the original message and its response.
    */
   public static final String RULES_ORIGINAL_MESSAGE = "rulesValidator-originalMessage";

   /**
    * A logger for this class.
    */
   private static final Logger log = Logger.getLogger(RulesValidator.class);

   /**
    * Rules helper that wraps the Drools logic.
    */
   private RulesValidatorHelper rulesValidatorHelper;

   @Override
   public boolean isValid(final Message originalMessage, final Message response) {
      if (rulesValidatorHelper == null) {
         log.error("Rules were not properly loaded.");
         return false;
      }

      final Map<Integer, String> unusedAssertions = rulesValidatorHelper.validate(originalMessage, response);

      for (final Entry<Integer, String> entry : unusedAssertions.entrySet()) {
         if (log.isDebugEnabled()) {
            log.debug(String.format("Drools message validation failed with message '%s' and rule '%s'.", response.toString(), entry.getValue()));
         }
      }

      if (!unusedAssertions.isEmpty()) {
         if (log.isInfoEnabled()) {
            log.info(String.format("Drools message validation failed with message '%s' - some rules failed, see previous log for more details.", response.toString()));
         }
         return false;
      }

      return true;
   }

   /**
    * Creates a new {@link org.perfcake.validation.RulesValidatorHelper} based on the provided assertions.
    * @param assertionsReader A buffered reader prepared to read the assertions. Each line represents a single assertion.
    * @throws Exception When it was not possible to read and process the assertions.
    */
   private void processAssertions(BufferedReader assertionsReader) throws Exception {
      HashMap<Integer, String> assertions = new HashMap<>();
      int lineNo = 0;
      String line;

      while ((line = assertionsReader.readLine()) != null) {
         line = StringUtil.trim(line);
         if (!"".equals(line) && !line.startsWith("#")) {
            assertions.put(lineNo, line);
            lineNo++;
         }
      }

      rulesValidatorHelper = new RulesValidatorHelper(assertions);
   }

   /**
    * Sets the rules file from which the assertions are loaded.
    * @param validationRuleFile The file name of the assertions file.
    */
   public void setRules(final String validationRuleFile) {
      try (final FileReader fr = new FileReader(validationRuleFile);
            final BufferedReader br = new BufferedReader(fr)) {
         processAssertions(br);
      } catch (final Exception ex) {
         log.error("Error creating Drools base message validator.", ex);
      }
   }

   /**
    * Sets the rules based on an XML element holding the assertions.
    * @param validationRule The XML element with the assertions.
    */
   public void setRules(final Element validationRule) {
      try (final BufferedReader br = new BufferedReader(new StringReader(validationRule.getTextContent()))) {
         processAssertions(br);
      } catch (final Exception ex) {
         log.error("Error creating Drools base message validator.", ex);
      }
   }

}
