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

import javax.script.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * Validates messages using Java Script Engine and the provided script.
 * The script engine must be installed in the extensions directory. The original message is passed
 * to the script in the 'originalMessage' property and the response is inserted as 'message', both using
 * script bindings. Script return value is evaluated for validation success (true = passed, false = failed).
 *
 * @author Martin Večeřa <marvenec@gmail.com>
 */
public class ScriptValidator implements MessageValidator {

   private String engine;
   private String script;
   private String scriptFile;
   private CompiledScript compiledScript = null;

   private static final Logger log = Logger.getLogger(ScriptValidator.class);

   private CompiledScript getCompiledScript() throws ScriptException, ValidationException {
      if (compiledScript == null) {
         ScriptEngineManager manager = new ScriptEngineManager();
         ScriptEngine engine = manager.getEngineByName(this.engine);

         if (script != null) {
            compiledScript = ((Compilable) engine).compile(script);
         } else if (scriptFile != null) {
            try (Reader fr = new BufferedReader(new InputStreamReader(new FileInputStream(new File(scriptFile)), StandardCharsets.UTF_8))) {
               compiledScript = ((Compilable) engine).compile(fr);
            } catch (IOException e) {
               throw new ValidationException("Error loading script file: ", e);
            }
         } else {
            throw new ValidationException("No script was set.");
         }
      }

      return compiledScript;
   }

   @Override
   public boolean isValid(final Message originalMessage, Message response) {
      boolean result = false;

      try {
         CompiledScript script = getCompiledScript();
         Bindings b = script.getEngine().createBindings();
         b.put("originalMessage", originalMessage);
         b.put("message", response);
         b.put("log", log);
         Object ret = script.eval(b);
         if (ret instanceof Boolean) {
            result = (Boolean) ret;
         }
      } catch (ScriptException | ValidationException e) {
         log.warn("Error validating a message: ", e);
         result = false;
      }

      if (!result) {
         log.info(String.format("Script validating failed with the message '%s' using script validator.", response.toString()));
      }
      return result;
   }

   public String getEngine() {
      return engine;
   }

   public void setEngine(String engine) {
      this.compiledScript = null;
      this.engine = engine;
   }

   public String getScript() {
      return script;
   }

   public void setScript(String script) {
      this.scriptFile = null;
      this.compiledScript = null;
      this.script = script;
   }

   public void setScript(Element script) {
      this.scriptFile = null;
      this.compiledScript = null;
      this.script = script.getTextContent();
   }

   public String getScriptFile() {
      return scriptFile;
   }

   public void setScriptFile(String scriptFile) {
      this.script = null;
      this.compiledScript = null;
      this.scriptFile = scriptFile;
   }
}
