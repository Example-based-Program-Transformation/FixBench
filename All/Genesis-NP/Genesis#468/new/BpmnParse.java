/* Licensed under the Apache License, Version 2.0 (the "License");
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
 */
package org.activiti.engine.impl.bpmn.parser;

import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.activiti.engine.ActivitiException;
import org.activiti.engine.delegate.JavaDelegation;
import org.activiti.engine.impl.bpmn.BoundaryTimerEventActivity;
import org.activiti.engine.impl.bpmn.BpmnInterface;
import org.activiti.engine.impl.bpmn.BpmnInterfaceImplementation;
import org.activiti.engine.impl.bpmn.CallActivityBehaviour;
import org.activiti.engine.impl.bpmn.ClassStructure;
import org.activiti.engine.impl.bpmn.Condition;
import org.activiti.engine.impl.bpmn.DelegateEventListener;
import org.activiti.engine.impl.bpmn.ExclusiveGatewayActivity;
import org.activiti.engine.impl.bpmn.ExpressionEventListener;
import org.activiti.engine.impl.bpmn.FieldDeclarationDelegate;
import org.activiti.engine.impl.bpmn.ItemDefinition;
import org.activiti.engine.impl.bpmn.ItemKind;
import org.activiti.engine.impl.bpmn.JavaDelegationDelegate;
import org.activiti.engine.impl.bpmn.MailActivityBehavior;
import org.activiti.engine.impl.bpmn.ManualTaskActivity;
import org.activiti.engine.impl.bpmn.Message;
import org.activiti.engine.impl.bpmn.NoneEndEventActivity;
import org.activiti.engine.impl.bpmn.NoneStartEventActivity;
import org.activiti.engine.impl.bpmn.Operation;
import org.activiti.engine.impl.bpmn.OperationImplementation;
import org.activiti.engine.impl.bpmn.ParallelGatewayActivity;
import org.activiti.engine.impl.bpmn.ReceiveTaskActivity;
import org.activiti.engine.impl.bpmn.ScriptTaskActivity;
import org.activiti.engine.impl.bpmn.ServiceTaskExpressionActivityBehavior;
import org.activiti.engine.impl.bpmn.Structure;
import org.activiti.engine.impl.bpmn.SubProcessActivity;
import org.activiti.engine.impl.bpmn.TaskActivity;
import org.activiti.engine.impl.bpmn.UserTaskActivity;
import org.activiti.engine.impl.bpmn.WebServiceActivityBehavior;
import org.activiti.engine.impl.cfg.ProcessEngineConfiguration;
import org.activiti.engine.impl.el.Expression;
import org.activiti.engine.impl.el.ExpressionManager;
import org.activiti.engine.impl.el.FixedValue;
import org.activiti.engine.impl.el.UelExpressionCondition;
import org.activiti.engine.impl.form.DefaultStartFormHandler;
import org.activiti.engine.impl.form.DefaultTaskFormHandler;
import org.activiti.engine.impl.form.StartFormHandler;
import org.activiti.engine.impl.form.TaskFormHandler;
import org.activiti.engine.impl.jobexecutor.TimerDeclarationImpl;
import org.activiti.engine.impl.jobexecutor.TimerExecuteNestedActivityJobHandler;
import org.activiti.engine.impl.pvm.delegate.ActivityBehavior;
import org.activiti.engine.impl.pvm.delegate.EventListener;
import org.activiti.engine.impl.pvm.process.ActivityImpl;
import org.activiti.engine.impl.pvm.process.ProcessDefinitionImpl;
import org.activiti.engine.impl.pvm.process.ScopeImpl;
import org.activiti.engine.impl.pvm.process.TransitionImpl;
import org.activiti.engine.impl.repository.DeploymentEntity;
import org.activiti.engine.impl.repository.ProcessDefinitionEntity;
import org.activiti.engine.impl.scripting.ScriptingEngines;
import org.activiti.engine.impl.task.TaskDefinition;
import org.activiti.engine.impl.util.ReflectUtil;
import org.activiti.engine.impl.util.xml.Element;
import org.activiti.engine.impl.util.xml.Parse;
import org.activiti.engine.impl.variable.VariableDeclaration;
import org.activiti.engine.impl.webservice.WSDLImporter;

/**
 * Specific parsing of one BPMN 2.0 XML file, created by the {@link BpmnParser}.
 * 
 * @author Tom Baeyens
 * @author Joram Barrez
 * @author Christian Stettler
 * @author Frederik Heremans
 */
public class BpmnParse extends Parse {

  public static final String PROPERTYNAME_CONDITION = "condition";
  public static final String PROPERTYNAME_VARIABLE_DECLARATIONS = "variableDeclarations";
  public static final String PROPERTYNAME_TIMER_DECLARATION = "timerDeclarations";
  public static final String PROPERTYNAME_INITIAL = "initial";
  public static final String PROPERTYNAME_INITIATOR_VARIABLE_NAME = "initiatorVariableName";

  private static final Logger LOG = Logger.getLogger(BpmnParse.class.getName());
  
  protected DeploymentEntity deployment;
  
  /**
   * The end result of the parsing: a list of process definition.
   */
  protected List<ProcessDefinitionEntity> processDefinitions = new ArrayList<ProcessDefinitionEntity>();

  /**
   * Map containing the BPMN 2.0 messages, stored during the first phase
   * of parsing since other elements can reference these messages.
   * 
   * Messages are defined outside the process definition(s), which means
   * that this map doesn't need to be re-initialized for each new process
   * definition.
   */
  protected Map<String, Message> messages = new HashMap<String, Message>();
  
  /**
   * Map that contains the {@link Structure}
   */
  protected Map<String, Structure> structures = new HashMap<String, Structure>();

  /**
   * Map that contains the {@link BpmnInterfaceImplementation}
   */
  protected Map<String, BpmnInterfaceImplementation> interfaceImplementations = new HashMap<String, BpmnInterfaceImplementation>();

  /**
   * Map that contains the {@link OperationImplementation}
   */
  protected Map<String, OperationImplementation> operationImplementations = new HashMap<String, OperationImplementation>();

  /**
   * Map containing the BPMN 2.0 item definitions, stored during the first phase
   * of parsing since other elements can reference these item definitions.
   * 
   * Item definitions are defined outside the process definition(s), which means
   * that this map doesn't need to be re-initialized for each new process
   * definition.
   */
  protected Map<String, ItemDefinition> itemDefinitions = new HashMap<String, ItemDefinition>();

  /**
   * Map containing the the {@link BpmnInterface}s defined in the XML file. The
   * key is the id of the interface.
   * 
   * Interfaces are defined outside the process definition(s), which means that
   * this map doesn't need to be re-initialized for each new process definition.
   */
  protected Map<String, BpmnInterface> bpmnInterfaces = new HashMap<String, BpmnInterface>();

  /**
   * Map containing the {@link Operation}s defined in the XML file. The key is
   * the id of the operations.
   * 
   * Operations are defined outside the process definition(s), which means that
   * this map doesn't need to be re-initialized for each new process definition.
   */
  protected Map<String, Operation> operations = new HashMap<String, Operation>();

  protected ExpressionManager expressionManager;
  
  protected List<BpmnParseListener> parseListeners;
  
  protected Map<String, XMLImporter> importers = new HashMap<String, XMLImporter>();

  /**
   * Constructor to be called by the {@link BpmnParser}.
   * 
   * Note the package modifier here: only the {@link BpmnParser} is allowed to
   * create instances.
   */
  BpmnParse(BpmnParser parser) {
    super(parser);
    this.expressionManager = parser.getExpressionManager();
    this.parseListeners = parser.getParseListeners();
    setSchemaResource(ReflectUtil.getResource(BpmnParser.SCHEMA_RESOURCE).toString());
    this.importers.put("http://schemas.xmlsoap.org/wsdl/", new WSDLImporter());
  }
  
  public BpmnParse deployment(DeploymentEntity deployment) {
    this.deployment = deployment;
    return this;
  }

  @Override
  public BpmnParse execute() {
    super.execute(); // schema validation

    parseDefinitionsAttributes();
    parseImports();
    parseItemDefinitions();
    parseMessages();
    parseInterfaces();
    parseProcessDefinitions();
    
    if (hasWarnings()) {
      logWarnings();
    }
    if (hasErrors()) {
      throwActivitiExceptionForErrors();
    }
    
    return this;
  }
  
  private void parseDefinitionsAttributes() {
    String typeLanguage = rootElement.attribute("typeLanguage");
    String expressionLanguage = rootElement.attribute("expressionLanguage");
    
    if (typeLanguage != null) {
      if (typeLanguage.contains("XMLSchema")) {
        LOG.info("XMLSchema currently not supported as typeLanguage");
      }
    }
    
    
    if (expressionLanguage != null) {
      if(expressionLanguage.contains("XPath")) {
        LOG.info("XPath currently not supported as typeLanguage");
      }
    }
  }

  /**
   * Parses the rootElement importing structures
   * 
   * @param rootElement
   *          The root element of the XML file.
   */
  private void parseImports() {
    List<Element> imports = rootElement.elements("import");
    for (Element theImport : imports) {
      String importType = theImport.attribute("importType");
      XMLImporter importer = this.importers.get(importType);
      if (importer == null) {
        addError("Could not import item of type " + importType, theImport);
      } else {
        importer.importFrom(theImport, this);
      }
    }
  }

  /**
   * Parses the itemDefinitions of the given definitions file. Item definitions
   * are not contained within a process element, but they can be referenced from
   * inner process elements.
   * 
   * @param definitionsElement
   *          The root element of the XML file.
   */
  public void parseItemDefinitions() {
    for (Element itemDefinitionElement : rootElement.elements("itemDefinition")) {
      String id = itemDefinitionElement.attribute("id");
      String structureRef = itemDefinitionElement.attribute("structureRef");
      String itemKind = itemDefinitionElement.attribute("itemKind");
      Structure structure = null;
      
      try {
        //it is a class
        Class<?> classStructure = ReflectUtil.loadClass(structureRef);
        structure = new ClassStructure(classStructure);
      } catch (ActivitiException e) {
        //it is a reference to a different structure
        structure = this.structures.get(structureRef);
      }
      
      ItemDefinition itemDefinition = new ItemDefinition(id, structure);
      if (itemKind != null) {
        itemDefinition.setItemKind(ItemKind.valueOf(itemKind));
      }
      itemDefinitions.put(id, itemDefinition);
    }
  }
  
  /**
   * Parses the messages of the given definitions file. Messages
   * are not contained within a process element, but they can be referenced from
   * inner process elements.
   * 
   * @param definitionsElement
   *          The root element of the XML file/
   */
  public void parseMessages() {
    for (Element messageElement : rootElement.elements("message")) {
      String id = messageElement.attribute("id");
      String itemRef = messageElement.attribute("itemRef");
      
      if (!this.itemDefinitions.containsKey(itemRef)) {
        addError(itemRef + " does not exist", messageElement);
      } else {
        ItemDefinition itemDefinition = this.itemDefinitions.get(itemRef);
        Message message = new Message(id, itemDefinition);
        this.messages.put(id, message);
      }
    }
  }

  /**
   * Parses the interfaces and operations defined withing the root element.
   * 
   * @param definitionsElement
   *          The root element of the XML file/
   */
  public void parseInterfaces() {
    for (Element interfaceElement : rootElement.elements("interface")) {

      // Create the interface
      String id = interfaceElement.attribute("id");
      String name = interfaceElement.attribute("name");
      String implementationRef = interfaceElement.attribute("implementationRef");
      BpmnInterface bpmnInterface = new BpmnInterface(id, name);
      bpmnInterface.setImplementation(this.interfaceImplementations.get(implementationRef));

      // Handle all its operations
      for (Element operationElement : interfaceElement.elements("operation")) {
        Operation operation = parseOperation(operationElement, bpmnInterface);
        bpmnInterface.addOperation(operation);
      }

      bpmnInterfaces.put(id, bpmnInterface);
    }
  }

  public Operation parseOperation(Element operationElement, BpmnInterface bpmnInterface) {
    Element inMessageRefElement = operationElement.element("inMessageRef");

    if (!this.messages.containsKey(inMessageRefElement.getText())) {
      addError(inMessageRefElement.getText() + " does not exist", inMessageRefElement);
      return null;
    } else {
      Message inMessage = this.messages.get(inMessageRefElement.getText());
      String id = operationElement.attribute("id");
      String name = operationElement.attribute("name");
      String implementationRef = operationElement.attribute("implementationRef");
      Operation operation = new Operation(id, name, bpmnInterface, inMessage);
      operation.setImplementation(this.operationImplementations.get(implementationRef));

      Element outMessageRefElement = operationElement.element("outMessageRef");
      if (this.messages.containsKey(outMessageRefElement.getText())) {
        Message outMessage = this.messages.get(outMessageRefElement.getText());
        operation.setOutMessage(outMessage);
      }
      
      operations.put(id, operation);
      return operation;
    }
  }

  /**
   * Parses all the process definitions defined within the 'definitions' root
   * element.
   * 
   * @param definitionsElement
   *          The root element of the XML file.
   */
  public void parseProcessDefinitions() {
    // TODO: parse specific definitions signalData (id, imports, etc)
    for (Element processElement : rootElement.elements("process")) {
      processDefinitions.add(parseProcess(processElement));
    }
  }

  /**
   * Parses one process (ie anything inside a &lt;process&gt; element).
   * 
   * @param processElement
   *          The 'process' element.
   * @return The parsed version of the XML: a {@link ProcessDefinitionImpl}
   *         object.
   */
  public ProcessDefinitionEntity parseProcess(Element processElement) {
    ProcessDefinitionEntity processDefinition = new ProcessDefinitionEntity();

    /*
     * Mapping object model - bpmn xml: processDefinition.id -> generated by
     * activiti engine processDefinition.key -> bpmn id (required)
     * processDefinition.name -> bpmn name (optional)
     */
    processDefinition.setKey(processElement.attribute("id"));
    processDefinition.setName(processElement.attribute("name"));
    processDefinition.setCategory(rootElement.attribute("targetNamespace"));
    processDefinition.setProperty("documentation", parseDocumentation(processElement));
    processDefinition.setTaskDefinitions(new HashMap<String, TaskDefinition>());
    processDefinition.setDeploymentId(deployment.getId());
    
    String historyLevelText = processElement.attribute("history");
    if (historyLevelText!=null) {
      processDefinition.setHistoryLevel(ProcessEngineConfiguration.parseHistoryLevel(historyLevelText));
    }

    if (LOG.isLoggable(Level.FINE)) {
      LOG.fine("Parsing process " + processDefinition.getKey());
    }

    parseScope(processElement, processDefinition);
    
    for (BpmnParseListener parseListener: parseListeners) {
      parseListener.parseProcess(processElement, processDefinition);
    }
     
    return processDefinition;
  }
  
  /**
   * Parses a scope: a process, subprocess, etc.
   * 
   * Note that a process definition is a scope on itself.
   * 
   * @param scopeElement The XML element defining the scope
   * @param parentScope The scope that contains the nested scope. 
   */
  public void parseScope(Element scopeElement, ScopeImpl parentScope) {
    
    // Not yet supported on process level (PVM additions needed):
    // parseProperties(processElement);
    
    parseStartEvents(scopeElement, parentScope);
    parseActivities(scopeElement, parentScope);
    parseEndEvents(scopeElement, parentScope);
    parseBoundaryEvents(scopeElement, parentScope);
    parseSequenceFlow(scopeElement, parentScope);
    parseEventListenersOnScope(scopeElement, parentScope);
  }

  /**
   * Parses the start events of a certain level in the process (process,
   * subprocess or another scope).
   * 
   * @param parentElement
   *          The 'parent' element that contains the start events (process,
   *          subprocess).
   * @param scope
   *          The {@link ScopeImpl} to which the start events must be
   *          added.
   */
  public void parseStartEvents(Element parentElement, ScopeImpl scope) {
    List<Element> startEventElements = parentElement.elements("startEvent");
    if (startEventElements.size() > 1) {
      addError("Multiple start events are currently unsupported", parentElement);
    } else if (startEventElements.size() > 0) {

      Element startEventElement = startEventElements.get(0);

      String id = startEventElement.attribute("id");
      String name = startEventElement.attribute("name");
      String documentation = parseDocumentation(startEventElement);

      ActivityImpl startEventActivity = scope.createActivity(id);
      startEventActivity.setProperty("name", name);
      startEventActivity.setProperty("documentation", documentation);
      
      if (scope instanceof ProcessDefinitionEntity) {
        ProcessDefinitionEntity processDefinition = (ProcessDefinitionEntity) scope;
        if (processDefinition.getInitial()!=null) {
          // in order to support this, the initial should here be replaced with 
          // a kind of hidden decision activity that has pvm transitions to all 
          // of the visible bpmn start events
          addError("multiple startEvents in a process definition are not yet supported", startEventElement);
        }
        processDefinition.setInitial(startEventActivity);

        StartFormHandler startFormHandler;
        String startFormHandlerClassName = startEventElement.attributeNS(BpmnParser.ACTIVITI_BPMN_EXTENSIONS_NS, "formHandlerClass");
        if (startFormHandlerClassName!=null) {
          startFormHandler = (StartFormHandler) ReflectUtil.instantiate(startFormHandlerClassName);
        } else {
          startFormHandler = new DefaultStartFormHandler();
        }
        startFormHandler.parseConfiguration(startEventElement, deployment, this);
        processDefinition.setStartFormHandler(startFormHandler);
        
        String initiatorVariableName = startEventElement.attributeNS(BpmnParser.ACTIVITI_BPMN_EXTENSIONS_NS, "initiator");
        if (initiatorVariableName != null) {
          processDefinition.setProperty(PROPERTYNAME_INITIATOR_VARIABLE_NAME, initiatorVariableName);
        }

      } else {
        scope.setProperty(PROPERTYNAME_INITIAL, startEventActivity);
      }

      // Currently only none start events supported
      
      // TODO: a subprocess is only allowed to have a none start event 
      startEventActivity.setActivityBehavior(new NoneStartEventActivity());
      
      for (BpmnParseListener parseListener: parseListeners) {
        parseListener.parseStartEvent(startEventElement, scope, startEventActivity);
      }
    }
  }

  /**
   * Parses the activities of a certain level in the process (process,
   * subprocess or another scope).
   * 
   * @param parentElement
   *          The 'parent' element that contains the activities (process,
   *          subprocess).
   * @param scopeElement
   *          The {@link ScopeImpl} to which the activities must be
   *          added.
   */
  public void parseActivities(Element parentElement, ScopeImpl scopeElement) {
    for (Element activityElement : parentElement.elements()) {
      if (activityElement.getTagName().equals("exclusiveGateway")) {
        parseExclusiveGateway(activityElement, scopeElement);
      } else if (activityElement.getTagName().equals("parallelGateway")) {
        parseParallelGateway(activityElement, scopeElement);
      } else if (activityElement.getTagName().equals("scriptTask")) {
        parseScriptTask(activityElement, scopeElement);
      } else if (activityElement.getTagName().equals("serviceTask")) {
        parseServiceTask(activityElement, scopeElement);
      } else if (activityElement.getTagName().equals("task")) {
        parseTask(activityElement, scopeElement);
      } else if (activityElement.getTagName().equals("manualTask")) {
        parseManualTask(activityElement, scopeElement);
      } else if (activityElement.getTagName().equals("userTask")) {
        parseUserTask(activityElement, scopeElement);
      } else if (activityElement.getTagName().equals("receiveTask")) {
        parseReceiveTask(activityElement, scopeElement);
      } else if (activityElement.getTagName().equals("subProcess")) {
        parseSubProcess(activityElement, scopeElement);
      } else if (activityElement.getTagName().equals("callActivity")) {
        parseCallActivity(activityElement, scopeElement);
      } else if (activityElement.getTagName().equals("sendTask")
              || activityElement.getTagName().equals("adHocSubProcess")
              || activityElement.getTagName().equals("businessRuleTask")
              || activityElement.getTagName().equals("complexGateway")
              || activityElement.getTagName().equals("eventBasedGateway")
              || activityElement.getTagName().equals("transaction")) {
        addWarning("Ignoring unsupported activity type", activityElement);
      }
    }
  }

  /**
   * Generic parsing method for most flow elements: parsing of the documentation
   * sub-element.
   */
  public String parseDocumentation(Element element) {
    Element docElement = element.element("documentation");
    if (docElement != null) {
      return docElement.getText().trim();
    }
    return null;
  }

  /**
   * Parses the generic information of an activity element (id, name), and
   * creates a new {@link ActivityImpl} on the given scope element.
   */
  public ActivityImpl parseAndCreateActivityOnScopeElement(Element activityElement, ScopeImpl scopeElement) {
    String id = activityElement.attribute("id");
    String name = activityElement.attribute("name");
    String documentation = parseDocumentation(activityElement);
    if (LOG.isLoggable(Level.FINE)) {
      LOG.fine("Parsing activity " + id);
    }
    ActivityImpl activity = scopeElement.createActivity(id);
    activity.setProperty("name", name);
    activity.setProperty("documentation", documentation);
    activity.setProperty("type", activityElement.getTagName());
    activity.setProperty("line", activityElement.getLine());
    return activity;
  }

  /**
   * Parses an exclusive gateway declaration.
   */
  public void parseExclusiveGateway(Element exclusiveGwElement, ScopeImpl scope) {
    ActivityImpl activity = parseAndCreateActivityOnScopeElement(exclusiveGwElement, scope);
    activity.setActivityBehavior(new ExclusiveGatewayActivity());
    
    parseEventListenersOnScope(exclusiveGwElement, activity);
    
    for (BpmnParseListener parseListener: parseListeners) {
      parseListener.parseExclusiveGateway(exclusiveGwElement, scope, activity);
    }
  }

  /**
   * Parses a parallel gateway declaration.
   */
  public void parseParallelGateway(Element parallelGwElement, ScopeImpl scope) {
    ActivityImpl activity = parseAndCreateActivityOnScopeElement(parallelGwElement, scope);
    activity.setActivityBehavior(new ParallelGatewayActivity());
    
    parseEventListenersOnScope(parallelGwElement, activity);
    
    for (BpmnParseListener parseListener: parseListeners) {
      parseListener.parseParallelGateway(parallelGwElement, scope, activity);
    }
  }

  /**
   * Parses a scriptTask declaration.
   */
  public void parseScriptTask(Element scriptTaskElement, ScopeImpl scope) {
    ActivityImpl activity = parseAndCreateActivityOnScopeElement(scriptTaskElement, scope);
    String script = null;
    String language = null;
    String resultVariableName = null;

    Element scriptElement = scriptTaskElement.element("script");
    if (scriptElement != null) {
      script = scriptElement.getText();
      
      if (language == null) {
        language = scriptTaskElement.attribute("scriptFormat");
      }
      
      if (language == null) {
        language = ScriptingEngines.DEFAULT_SCRIPTING_LANGUAGE;
      }

      resultVariableName = scriptTaskElement.attributeNS(BpmnParser.ACTIVITI_BPMN_EXTENSIONS_NS, "resultVariableName");
    }
    
    activity.setActivityBehavior(new ScriptTaskActivity(script, language, resultVariableName));
    
    parseEventListenersOnScope(scriptTaskElement, activity);

    for (BpmnParseListener parseListener: parseListeners) {
      parseListener.parseScript(scriptTaskElement, scope, activity);
    }
  }

  /**
   * Parses a serviceTask declaration.
   */
  public void parseServiceTask(Element serviceTaskElement, ScopeImpl scope) {
    ActivityImpl activity = parseAndCreateActivityOnScopeElement(serviceTaskElement, scope);

    String type = serviceTaskElement.attributeNS(BpmnParser.ACTIVITI_BPMN_EXTENSIONS_NS, "type");
    String className = serviceTaskElement.attributeNS(BpmnParser.ACTIVITI_BPMN_EXTENSIONS_NS, "class");
    String expression = serviceTaskElement.attributeNS(BpmnParser.ACTIVITI_BPMN_EXTENSIONS_NS, "expression");
    String resultVariableName = serviceTaskElement.attributeNS(BpmnParser.ACTIVITI_BPMN_EXTENSIONS_NS, "resultVariableName");
    String implementation = serviceTaskElement.attribute("implementation");
    String operationRef = serviceTaskElement.attribute("operationRef");
    List<FieldDeclaration> fieldDeclarations = parseFieldDeclarationsOnServiceTask(serviceTaskElement);

    if (type != null) {
      if (type.equalsIgnoreCase("mail")) {
        parseEmailServiceTask(activity, serviceTaskElement, fieldDeclarations);
      } else {
        addError("Invalid usage of type attribute: '" + type + "'", serviceTaskElement);
      }
    
    } else if (className != null && className.trim().length() > 0) {
      if (resultVariableName != null) {
        addError("'resultVariableName' not supported for service tasks using 'class'", serviceTaskElement);
      }

      Object delegateInstance = new FieldDeclarationDelegate(className, fieldDeclarations).getDelegateInstance();
      if (delegateInstance instanceof ActivityBehavior) {
        activity.setActivityBehavior((ActivityBehavior) delegateInstance);
      } else if (delegateInstance instanceof JavaDelegation) {
        activity.setActivityBehavior(new JavaDelegationDelegate((JavaDelegation) delegateInstance));
      } else {
        addError(delegateInstance.getClass().getName()+" doesn't implement "+JavaDelegation.class.getName()+" nor "+ActivityBehavior.class.getName(), serviceTaskElement);
      }
      
    } else if (expression != null && expression.trim().length() > 0) {
      activity.setActivityBehavior(new ServiceTaskExpressionActivityBehavior(expressionManager.createExpression(expression), resultVariableName));
      
    } else if (implementation != null && operationRef != null && implementation.equalsIgnoreCase("##WebService")) {
      if (!this.operations.containsKey(operationRef)) {
        addError(operationRef + " does not exist" , serviceTaskElement);
      } else {
        Operation operation = this.operations.get(operationRef);
        activity.setActivityBehavior(new WebServiceActivityBehavior(operation));
      }
    } else {
      addError("'class', 'type', or 'expression' attribute is mandatory on serviceTask", serviceTaskElement);
    }
    
    parseEventListenersOnScope(serviceTaskElement, activity);

    for (BpmnParseListener parseListener: parseListeners) {
      parseListener.parseServiceTask(serviceTaskElement, scope, activity);
    }
  }

  protected void parseEmailServiceTask(ActivityImpl activity, Element serviceTaskElement, List<FieldDeclaration> fieldDeclarations) {
    validateFieldDeclarationsForEmail(serviceTaskElement, fieldDeclarations);
    ActivityBehavior mailBehavior = (ActivityBehavior) new FieldDeclarationDelegate(MailActivityBehavior.class.getName(), fieldDeclarations).getDelegateInstance();
    activity.setActivityBehavior(mailBehavior);
  }
  
  protected void validateFieldDeclarationsForEmail(Element serviceTaskElement, List<FieldDeclaration> fieldDeclarations) {
    boolean toDefined = false;
    boolean textOrHtmlDefined = false;
    for (FieldDeclaration fieldDeclaration : fieldDeclarations) {
      if (fieldDeclaration.getName().equals("to")) {
        toDefined = true;
      }
      if (fieldDeclaration.getName().equals("html")) {
        textOrHtmlDefined = true;
      }
      if (fieldDeclaration.getName().equals("text")) {
        textOrHtmlDefined = true;
      }
    }
    
    if (!toDefined) {
      addError("No recipient is defined on the mail activity", serviceTaskElement);
    }
    if (!textOrHtmlDefined) {
      addError("Text or html field should be provided", serviceTaskElement);
    }
  }
  
  public List<FieldDeclaration> parseFieldDeclarationsOnServiceTask(Element serviceTaskElement) {
    Element extensionElement = serviceTaskElement.element("extensionElements");
    if (extensionElement != null) {
      return parseFieldDeclarations(extensionElement);
    }
    return new ArrayList<FieldDeclaration>();
  }
  
  public List<FieldDeclaration> parseFieldDeclarations(Element element) {
    List<FieldDeclaration> fieldDeclarations = new ArrayList<FieldDeclaration>();
    
    List<Element> fieldDeclarationElements = element.elementsNS(BpmnParser.ACTIVITI_BPMN_EXTENSIONS_NS, "field");
    if (fieldDeclarationElements != null && !fieldDeclarationElements.isEmpty()) {
      
      for (Element fieldDeclarationElement : fieldDeclarationElements) {
        FieldDeclaration fieldDeclaration = parseFieldDeclaration(element, fieldDeclarationElement);
        if(fieldDeclaration != null) {
          fieldDeclarations.add(fieldDeclaration);            
        }
      }
    }
    
    return fieldDeclarations;
  }

  protected FieldDeclaration parseFieldDeclaration(Element serviceTaskElement, Element fieldDeclarationElement) {
    String fieldName = fieldDeclarationElement.attribute("name");
    
    FieldDeclaration fieldDeclaration = parseStringFieldDeclaration(fieldDeclarationElement, serviceTaskElement, fieldName);    
    if(fieldDeclaration == null) {
     fieldDeclaration = parseExpressionFieldDeclaration(fieldDeclarationElement, serviceTaskElement, fieldName);
    }
    
    if(fieldDeclaration == null) {
      addError("One of the following is mandatory on a field declaration: one of attributes stringValue|expression " + 
        "or one of child elements string|expression",  serviceTaskElement);
    }
    return fieldDeclaration;
  }
  
  protected FieldDeclaration parseStringFieldDeclaration(Element fieldDeclarationElement, Element serviceTaskElement, String fieldName) {
    try {
      String fieldValue = getStringValueFromAttributeOrElement("stringValue", "string", null, fieldDeclarationElement);
      if(fieldValue != null) {
        return new FieldDeclaration(fieldName, Expression.class.getName(), new FixedValue(fieldValue)); 
      }
    } catch (ActivitiException ae) {
      if (ae.getMessage().contains("multiple elements with tag name")) {
        addError("Multiple string field declarations found", serviceTaskElement);
      } else {
        addError("Error when paring field declarations: " + ae.getMessage(), serviceTaskElement);
      }
    }
    return null;
  }
  
  
  protected FieldDeclaration parseExpressionFieldDeclaration(Element fieldDeclarationElement, Element serviceTaskElement, String fieldName) {
    try {
      String expression = getStringValueFromAttributeOrElement("expression", "expression", null, fieldDeclarationElement);
      if(expression != null && expression.trim().length() > 0) {
        return new FieldDeclaration(fieldName, Expression.class.getName(), expressionManager.createExpression(expression));
      }
    } catch(ActivitiException ae) {
      if (ae.getMessage().contains("multiple elements with tag name")) {
        addError("Multiple expression field declarations found", serviceTaskElement);
      } else {
        addError("Error when paring field declarations: " + ae.getMessage(), serviceTaskElement);
      }
    }
    return null;
  }
  
  protected String getStringValueFromAttributeOrElement(String attributeName, String elementName, String namespace, Element element) {
    String value = null;
    
    String attributeValue = element.attributeNS(namespace, attributeName);
    Element childElement = element.elementNS(namespace, elementName);
    String stringElementText = null;
    
    if(attributeValue != null && childElement != null) {
      addError("Can't use attribute '" + attributeName + "' and element '" + elementName + "' together, only use one", element);
    } else if (childElement != null) {
      stringElementText = childElement.getText();
      if (stringElementText == null || stringElementText.length() == 0) {
        addError("No valid value found in attribute '" + attributeName + "' nor element '" + elementName + "'", element);
      } else {
        // Use text of element
        value = stringElementText;
      }
    } else if(attributeValue != null && attributeValue.length() > 0) {
      // Using attribute
      value = attributeValue;
    } 
    
    return value;
  }

  /**
   * Parses a task with no specific type (behaves as passthrough).
   */
  public void parseTask(Element taskElement, ScopeImpl scope) {
    ActivityImpl activity = parseAndCreateActivityOnScopeElement(taskElement, scope);
    activity.setActivityBehavior(new TaskActivity());
    
    parseEventListenersOnScope(taskElement, activity);

    for (BpmnParseListener parseListener: parseListeners) {
      parseListener.parseTask(taskElement, scope, activity);
    }
  }

  /**
   * Parses a manual task.
   */
  public void parseManualTask(Element manualTaskElement, ScopeImpl scope) {
    ActivityImpl activity = parseAndCreateActivityOnScopeElement(manualTaskElement, scope);
    activity.setActivityBehavior(new ManualTaskActivity());
    
    parseEventListenersOnScope(manualTaskElement, activity);

    for (BpmnParseListener parseListener: parseListeners) {
      parseListener.parseManualTask(manualTaskElement, scope, activity);
    }
  }

  /**
   * Parses a receive task.
   */
  public void parseReceiveTask(Element receiveTaskElement, ScopeImpl scope) {
    ActivityImpl activity = parseAndCreateActivityOnScopeElement(receiveTaskElement, scope);
    activity.setActivityBehavior(new ReceiveTaskActivity());

    parseEventListenersOnScope(receiveTaskElement, activity);
    
    for (BpmnParseListener parseListener: parseListeners) {
      parseListener.parseManualTask(receiveTaskElement, scope, activity);
    }
  }

  /* userTask specific finals */

  protected static final String HUMAN_PERFORMER = "humanPerformer";
  protected static final String POTENTIAL_OWNER = "potentialOwner";

  protected static final String RESOURCE_ASSIGNMENT_EXPR = "resourceAssignmentExpression";
  protected static final String FORMAL_EXPRESSION = "formalExpression";

  protected static final String USER_PREFIX = "user(";
  protected static final String GROUP_PREFIX = "group(";

  protected static final String ASSIGNEE_EXTENSION = "assignee";
  protected static final String CANDIDATE_USERS_EXTENSION = "candidateUsers";
  protected static final String CANDIDATE_GROUPS_EXTENSION = "candidateGroups";

  /**
   * Parses a userTask declaration.
   */
  public void parseUserTask(Element userTaskElement, ScopeImpl scope) {
    ActivityImpl activity = parseAndCreateActivityOnScopeElement(userTaskElement, scope);
    TaskDefinition taskDefinition = parseTaskDefinition(userTaskElement, activity.getId(), (ProcessDefinitionEntity) scope.getProcessDefinition());

    UserTaskActivity userTaskActivity = new UserTaskActivity(expressionManager, taskDefinition);

    activity.setActivityBehavior(userTaskActivity);

    parseProperties(userTaskElement, activity);
    
    parseEventListenersOnScope(userTaskElement, activity);

    for (BpmnParseListener parseListener: parseListeners) {
      parseListener.parseUserTask(userTaskElement, scope, activity);
    }
  }

  public TaskDefinition parseTaskDefinition(Element taskElement, String taskDefinitionKey, ProcessDefinitionEntity processDefinition) {
    TaskFormHandler taskFormHandler;
    String taskFormHandlerClassName = taskElement.attributeNS(BpmnParser.ACTIVITI_BPMN_EXTENSIONS_NS, "formHandlerClass");
    if (taskFormHandlerClassName!=null) {
      taskFormHandler = (TaskFormHandler) ReflectUtil.instantiate(taskFormHandlerClassName);
    } else {
      taskFormHandler = new DefaultTaskFormHandler();
    }
    taskFormHandler.parseConfiguration(taskElement, deployment, this);

    TaskDefinition taskDefinition = new TaskDefinition(taskFormHandler);
    
    taskDefinition.setKey(taskDefinitionKey);
    processDefinition.getTaskDefinitions().put(taskDefinitionKey, taskDefinition);
    
    String name = taskElement.attribute("name");
    if (name != null) {
      taskDefinition.setNameExpression(expressionManager.createExpression(name));
    }
    
    String descriptionStr = parseDocumentation(taskElement);
    if(descriptionStr != null) {
      taskDefinition.setDescriptionExpression(expressionManager.createExpression(descriptionStr));      
    }
    
    parseHumanPerformer(taskElement, taskDefinition);
    parsePotentialOwner(taskElement, taskDefinition);

    // Activiti custom extension
    parseUserTaskCustomExtensions(taskElement, taskDefinition);

    return taskDefinition;
  }

  protected void parseHumanPerformer(Element taskElement, TaskDefinition taskDefinition) {
    List<Element> humanPerformerElements = taskElement.elements(HUMAN_PERFORMER);

    if (humanPerformerElements.size() > 1) {
      addError("Invalid task definition: multiple " + HUMAN_PERFORMER + " sub elements defined for " 
              + taskDefinition.getNameExpression(), taskElement);
    } else if (humanPerformerElements.size() == 1) {
      Element humanPerformerElement = humanPerformerElements.get(0);
      if (humanPerformerElement != null) {
        parseHumanPerformerResourceAssignment(humanPerformerElement, taskDefinition);
      }
    }
  }

  protected void parsePotentialOwner(Element taskElement, TaskDefinition taskDefinition) {
    List<Element> potentialOwnerElements = taskElement.elements(POTENTIAL_OWNER);
    for (Element potentialOwnerElement : potentialOwnerElements) {
      parsePotentialOwnerResourceAssignment(potentialOwnerElement, taskDefinition);
    }
  }

  protected void parseHumanPerformerResourceAssignment(Element performerElement, TaskDefinition taskDefinition) {
    Element raeElement = performerElement.element(RESOURCE_ASSIGNMENT_EXPR);
    if (raeElement != null) {
      Element feElement = raeElement.element(FORMAL_EXPRESSION);
      if (feElement != null) {
        taskDefinition.setAssigneeExpression(expressionManager.createExpression(feElement.getText()));
      }
    }
  }

  protected void parsePotentialOwnerResourceAssignment(Element performerElement, TaskDefinition taskDefinition) {
    Element raeElement = performerElement.element(RESOURCE_ASSIGNMENT_EXPR);
    if (raeElement != null) {
      Element feElement = raeElement.element(FORMAL_EXPRESSION);
      if (feElement != null) {
        String[] assignmentExpressions = splitCommaSeparatedExpression(feElement.getText());
        for (String assignmentExpression : assignmentExpressions) {
          assignmentExpression = assignmentExpression.trim();
          if (assignmentExpression.startsWith(USER_PREFIX)) {
            String userAssignementId = getAssignmentId(assignmentExpression, USER_PREFIX);
            taskDefinition.addCandidateUserIdExpression(expressionManager.createExpression(userAssignementId));
          } else if (assignmentExpression.startsWith(GROUP_PREFIX)) {
            String groupAssignementId = getAssignmentId(assignmentExpression, GROUP_PREFIX);
            taskDefinition.addCandidateGroupIdExpression(expressionManager.createExpression(groupAssignementId));
          } else { // default: given string is a goupId, as-is.
            taskDefinition.addCandidateGroupIdExpression(expressionManager.createExpression(assignmentExpression));
          }
        }
      }
    }
  }

  protected String[] splitCommaSeparatedExpression(String expression) {
    if (expression == null) {
      addError("Invalid: no content for " + FORMAL_EXPRESSION + " provided", rootElement);
    }
    return expression.split(",");
  }

  protected String getAssignmentId(String expression, String prefix) {
    return expression.substring(prefix.length(), expression.length() - 1).trim();
  }

  protected void parseUserTaskCustomExtensions(Element taskElement, TaskDefinition taskDefinition) {

    // assignee
    String assignee = taskElement.attributeNS(BpmnParser.ACTIVITI_BPMN_EXTENSIONS_NS, ASSIGNEE_EXTENSION);
    if (assignee != null) {
      if (taskDefinition.getAssigneeExpression() == null) {
        taskDefinition.setAssigneeExpression(expressionManager.createExpression(assignee));
      } else {
        addError("Invalid usage: duplicate assignee declaration for task " 
                + taskDefinition.getNameExpression(), taskElement);
      }
    }

    // Candidate users
    String candidateUsersString = taskElement.attributeNS(BpmnParser.ACTIVITI_BPMN_EXTENSIONS_NS, CANDIDATE_USERS_EXTENSION);
    if (candidateUsersString != null) {
      String[] candidateUsers = candidateUsersString.split(",");
      for (String candidateUser : candidateUsers) {
        taskDefinition.addCandidateUserIdExpression(expressionManager.createExpression(candidateUser.trim()));
      }
    }

    // Candidate groups
    String candidateGroupsString = taskElement.attributeNS(BpmnParser.ACTIVITI_BPMN_EXTENSIONS_NS, CANDIDATE_GROUPS_EXTENSION);
    if (candidateGroupsString != null) {
      String[] candidateGroups = candidateGroupsString.split(",");
      for (String candidateGroup : candidateGroups) {
        taskDefinition.addCandidateGroupIdExpression(expressionManager.createExpression(candidateGroup.trim()));
      }
    }
  }

  /**
   * Parses the end events of a certain level in the process (process,
   * subprocess or another scope).
   * 
   * @param parentElement
   *          The 'parent' element that contains the end events (process,
   *          subprocess).
   * @param scope
   *          The {@link ScopeImpl} to which the end events must be
   *          added.
   */
  public void parseEndEvents(Element parentElement, ScopeImpl scope) {
    for (Element endEventElement : parentElement.elements("endEvent")) {
      String id = endEventElement.attribute("id");
      String name = endEventElement.attribute("name");
      String documentation = parseDocumentation(endEventElement);

      ActivityImpl activity = scope.createActivity(id);
      activity.setProperty("name", name);
      activity.setProperty("documentation", documentation);

      // Only none end events are currently supported
      activity.setActivityBehavior(new NoneEndEventActivity());

      for (BpmnParseListener parseListener: parseListeners) {
        parseListener.parseEndEvent(endEventElement, scope, activity);
      }
    }
  }

  /**
   * Parses the boundary events of a certain 'level' (process, subprocess or
   * other scope).
   * 
   * Note that the boundary events are not parsed during the parsing of the bpmn
   * activities, since the semantics are different (boundaryEvent needs to be
   * added as nested activity to the reference activity on PVM level).
   * 
   * @param parentElement
   *          The 'parent' element that contains the activities (process,
   *          subprocess).
   * @param scopeElement
   *          The {@link ScopeImpl} to which the activities must be
   *          added.
   */
  public void parseBoundaryEvents(Element parentElement, ScopeImpl scopeElement) {
    for (Element boundaryEventElement : parentElement.elements("boundaryEvent")) {

      // The boundary event is attached to an activity, reference by the
      // 'attachedToRef' attribute
      String attachedToRef = boundaryEventElement.attribute("attachedToRef");
      if (attachedToRef == null || attachedToRef.equals("")) {
        addError("AttachedToRef is required when using a timerEventDefinition", boundaryEventElement);
      }

      // Representation structure-wise is a nested activity in the activity to
      // which its attached
      String id = boundaryEventElement.attribute("id");
      if (LOG.isLoggable(Level.FINE)) {
        LOG.fine("Parsing boundary event " + id);
      }

      ActivityImpl parentActivity = scopeElement.findActivity(attachedToRef);
      if (parentActivity == null) {
        addError("Invalid reference in boundary event. Make sure that the referenced activity is " 
                + "defined in the same scope as the boundary event", boundaryEventElement);
      }
      ActivityImpl nestedActivity = parentActivity.createActivity(id);
      nestedActivity.setProperty("name", boundaryEventElement.attribute("name"));
      nestedActivity.setProperty("documentation", parseDocumentation(boundaryEventElement));

      String cancelActivity = boundaryEventElement.attribute("cancelActivity", "true");
      boolean interrupting = cancelActivity.equals("true") ? true : false;

      // Depending on the sub-element definition, the correct activityBehavior
      // parsing is selected
      Element timerEventDefinition = boundaryEventElement.element("timerEventDefinition");
      if (timerEventDefinition != null) {
        parseBoundaryTimerEventDefinition(timerEventDefinition, interrupting, nestedActivity);
      } else {
        addError("Unsupported boundary event type", boundaryEventElement);
      }
    }
  }

  /**
   * Parses a boundary timer event. The end-result will be that the given nested
   * activity will get the appropriate {@link ActivityBehavior}.
   * 
   * @param timerEventDefinition
   *          The XML element corresponding with the timer event details
   * @param interrupting
   *          Indicates whether this timer is interrupting.
   * @param timerActivity
   *          The activity which maps to the structure of the timer event on the
   *          boundary of another activity. Note that this is NOT the activity
   *          onto which the boundary event is attached, but a nested activity
   *          inside this activity, specifically created for this event.
   */
  public void parseBoundaryTimerEventDefinition(Element timerEventDefinition, boolean interrupting, ActivityImpl timerActivity) {
    BoundaryTimerEventActivity boundaryTimerEventActivity = new BoundaryTimerEventActivity();
    boundaryTimerEventActivity.setInterrupting(interrupting);

    // TimeDate

    // TimeCycle

    // TimeDuration
    Element timeDuration = timerEventDefinition.element("timeDuration");
    String timeDurationText = null;
    if (timeDuration != null) {
      timeDurationText = timeDuration.getText();
    }

    // Parse the timer declaration
    // TODO move the timer declaration into the bpmn activity or next to the TimerSession
    TimerDeclarationImpl timerDeclaration = new TimerDeclarationImpl(timeDurationText, TimerExecuteNestedActivityJobHandler.TYPE);
    timerDeclaration.setJobHandlerConfiguration(timerActivity.getId());
    addTimerDeclaration(timerActivity.getParent(), timerDeclaration);
    if (timerActivity.getParent() instanceof ActivityImpl) {
      ((ActivityImpl)timerActivity.getParent()).setScope(true);
    }
    
    timerActivity.setActivityBehavior(boundaryTimerEventActivity);
    
    for (BpmnParseListener parseListener: parseListeners) {
      parseListener.parseBoundaryTimerEventDefinition(timerEventDefinition, interrupting, timerActivity);
    }
  }

  @SuppressWarnings("unchecked")
  protected void addTimerDeclaration(ScopeImpl scope, TimerDeclarationImpl timerDeclaration) {
    List<TimerDeclarationImpl> timerDeclarations = (List<TimerDeclarationImpl>) scope.getProperty(PROPERTYNAME_TIMER_DECLARATION);
    if (timerDeclarations==null) {
      timerDeclarations = new ArrayList<TimerDeclarationImpl>();
      scope.setProperty(PROPERTYNAME_TIMER_DECLARATION, timerDeclarations);
    }
    timerDeclarations.add(timerDeclaration);
  }
  
  @SuppressWarnings("unchecked")
  protected void addVariableDeclaration(ScopeImpl scope, VariableDeclaration variableDeclaration) {
    List<VariableDeclaration> variableDeclarations = (List<VariableDeclaration>) scope.getProperty(PROPERTYNAME_VARIABLE_DECLARATIONS);
    if (variableDeclarations==null) {
      variableDeclarations = new ArrayList<VariableDeclaration>();
      scope.setProperty(PROPERTYNAME_VARIABLE_DECLARATIONS, variableDeclarations);
    }
    variableDeclarations.add(variableDeclaration);
  }
  
  /**
   * Parses a subprocess (formely known as an embedded subprocess): a subprocess
   * defined withing another process definition.
   * 
   * @param subProcessElement The XML element corresponding with the subprocess definition
   * @param scope The current scope on which the subprocess is defined.
   */
  public void parseSubProcess(Element subProcessElement, ScopeImpl scope) {
    ActivityImpl activity = parseAndCreateActivityOnScopeElement(subProcessElement, scope);
    activity.setScope(true);
    activity.setActivityBehavior(new SubProcessActivity());
    parseScope(subProcessElement, activity);
   
    for (BpmnParseListener parseListener: parseListeners) {
      parseListener.parseSubProcess(subProcessElement, scope, activity);
    }
  }
  
  /**
   * Parses a call activity (currenly only supporting calling subprocesses).
   * 
   * @param callActivityElement The XML element defining the call activity
   * @param scope The current scope on which the call activity is defined.
   */
  public void parseCallActivity(Element callActivityElement, ScopeImpl scope) {
    ActivityImpl activity = parseAndCreateActivityOnScopeElement(callActivityElement, scope);
    String calledElement = callActivityElement.attribute("calledElement");
    if (calledElement == null) {
      addError("Missing attribute 'calledElement'", callActivityElement);
    }
    activity.setActivityBehavior(new CallActivityBehaviour(calledElement));
    
    parseEventListenersOnScope(callActivityElement, activity);

    for (BpmnParseListener parseListener: parseListeners) {
      parseListener.parseCallActivity(callActivityElement, scope, activity);
    }
  }

  /**
   * Parses the properties of an element (if any) that can contain properties
   * (processes, activities, etc.)
   * 
   * Returns true if property subelemens are found.
   * 
   * @param element
   *          The element that can contain properties.
   * @param activity
   *          The activity where the property declaration is done.
   */
  public void parseProperties(Element element, ActivityImpl activity) {
    List<Element> propertyElements = element.elements("property");
    for (Element propertyElement : propertyElements) {
      parseProperty(propertyElement, activity);
    }
  }

  /**
   * Parses one property definition.
   * 
   * @param propertyElement
   *          The 'property' element that defines how a property looks like and
   *          is handled.
   */
  public void parseProperty(Element propertyElement, ActivityImpl activity) {
    String id = propertyElement.attribute("id");
    String name = propertyElement.attribute("name");

    // If name isn't given, use the id as name
    if (name == null) {
      if (id == null) {
        addError("Invalid property usage on line " + propertyElement.getLine() + ": no id or name specified.", propertyElement);
      } else {
        name = id;
      }
    }

    String itemSubjectRef = propertyElement.attribute("itemSubjectRef");
    String type = null;
    if (itemSubjectRef != null) {
      ItemDefinition itemDefinition = itemDefinitions.get(itemSubjectRef);
      if (itemDefinition != null) {
        Structure structure = itemDefinition.getStructure();
        type = structure.getId();
      } else {
        addError("Invalid itemDefinition reference: " + itemSubjectRef + " not found", propertyElement);
      }
    }

    parsePropertyCustomExtensions(activity, propertyElement, name, type);
  }

  /**
   * Parses the custom extensions for properties.
   * 
   * @param activity
   *          The activity where the property declaration is done.
   * @param propertyElement
   *          The 'property' element defining the property.
   * @param propertyName
   *          The name of the property.
   * @param propertyType
   *          The type of the property.
   */
  public void parsePropertyCustomExtensions(ActivityImpl activity, Element propertyElement, String propertyName, String propertyType) {

    if (propertyType == null) {
      String type = propertyElement.attributeNS(BpmnParser.ACTIVITI_BPMN_EXTENSIONS_NS, "type");
      propertyType = type != null ? type : "string"; // default is string
    }

    VariableDeclaration variableDeclaration = new VariableDeclaration(propertyName, propertyType);
    addVariableDeclaration(activity, variableDeclaration);
    activity.setScope(true);

    String src = propertyElement.attributeNS(BpmnParser.ACTIVITI_BPMN_EXTENSIONS_NS, "src");
    if (src != null) {
      variableDeclaration.setSourceVariableName(src);
    }

    String srcExpr = propertyElement.attributeNS(BpmnParser.ACTIVITI_BPMN_EXTENSIONS_NS, "srcExpr");
    if (srcExpr != null) {
      Expression sourceExpression = expressionManager.createExpression(srcExpr);
      variableDeclaration.setSourceExpression(sourceExpression);
    }

    String dst = propertyElement.attributeNS(BpmnParser.ACTIVITI_BPMN_EXTENSIONS_NS, "dst");
    if (dst != null) {
      variableDeclaration.setDestinationVariableName(dst);
    }

    String destExpr = propertyElement.attributeNS(BpmnParser.ACTIVITI_BPMN_EXTENSIONS_NS, "dstExpr");
    if (destExpr != null) {
      Expression destinationExpression = expressionManager.createExpression(destExpr);
      variableDeclaration.setDestinationExpression(destinationExpression);
    }

    String link = propertyElement.attributeNS(BpmnParser.ACTIVITI_BPMN_EXTENSIONS_NS, "link");
    if (link != null) {
      variableDeclaration.setLink(link);
    }

    String linkExpr = propertyElement.attributeNS(BpmnParser.ACTIVITI_BPMN_EXTENSIONS_NS, "linkExpr");
    if (linkExpr != null) {
      Expression linkExpression = expressionManager.createExpression(linkExpr);
      variableDeclaration.setLinkExpression(linkExpression);
    }
    
    for (BpmnParseListener parseListener: parseListeners) {
      parseListener.parseProperty(propertyElement, variableDeclaration, activity);
    }
  }

  /**
   * Parses all sequence flow of a scope.
   * 
   * @param processElement
   *          The 'process' element wherein the sequence flow are defined.
   * @param scope
   *          The scope to which the sequence flow must be added.
   */
  public void parseSequenceFlow(Element processElement, ScopeImpl scope) {
    for (Element sequenceFlowElement : processElement.elements("sequenceFlow")) {

      String id = sequenceFlowElement.attribute("id");
      String sourceRef = sequenceFlowElement.attribute("sourceRef");
      String destinationRef = sequenceFlowElement.attribute("targetRef");
      
      // Implicit check: sequence flow cannot cross (sub) process boundaries: we don't do a processDefinition.findActivity here
      ActivityImpl sourceActivity = scope.findActivity(sourceRef);
      ActivityImpl destinationActivity = scope.findActivity(destinationRef);

      if (sourceActivity != null && destinationActivity != null) {
        
        TransitionImpl transition = sourceActivity.createOutgoingTransition(id);
        transition.setProperty("name", sequenceFlowElement.attribute("name"));
        transition.setProperty("documentation", parseDocumentation(sequenceFlowElement));
        transition.setDestination(destinationActivity);
        parseSequenceFlowConditionExpression(sequenceFlowElement, transition);
        parseEventListenersOnTransition(sequenceFlowElement, transition);

        for (BpmnParseListener parseListener: parseListeners) {
          parseListener.parseSequenceFlow(sequenceFlowElement, scope, transition);
        }
        
      } else  if (sourceActivity == null) {
        addError("Invalid source '" + sourceRef + "' of sequence flow '" + id + "'", sequenceFlowElement);
      } else if (destinationActivity == null) {
        addError("Invalid destination '" + destinationRef + "' of sequence flow '" + id + "'", sequenceFlowElement);
      }

    }
  }

  /**
   * Parses a condition expression on a sequence flow.
   * 
   * @param seqFlowElement
   *          The 'sequenceFlow' element that can contain a condition.
   * @param seqFlow
   *          The sequenceFlow object representation to which the condition must
   *          be added.
   */
  public void parseSequenceFlowConditionExpression(Element seqFlowElement, TransitionImpl seqFlow) {
    Element conditionExprElement = seqFlowElement.element("conditionExpression");
    if (conditionExprElement != null) {
      String expr = conditionExprElement.getText().trim();
      String type = conditionExprElement.attributeNS(BpmnParser.XSI_NS, "type");
      if (type != null && !type.equals("tFormalExpression")) {
        addError("Invalid type, only tFormalExpression is currently supported", conditionExprElement);
      }
      
      Condition condition = new UelExpressionCondition(expressionManager.createExpression(expr));     
      seqFlow.setProperty(PROPERTYNAME_CONDITION, condition);
    }
  }
  
  /**
   * Parses all event-listeners on a scope.
   * 
   * @param scopeElement the XML element containing the scope definition.
   * @param scope the scope to add the event-listeners to.
   */
  public void parseEventListenersOnScope(Element scopeElement, ScopeImpl scope) {
    Element extentionsElement = scopeElement.element("extensionElements");
    if(extentionsElement != null) {
      List<Element> listenerElements = extentionsElement.elementsNS(BpmnParser.ACTIVITI_BPMN_EXTENSIONS_NS, "listener");
      for(Element listenerElement :listenerElements) {
        String eventName = listenerElement.attribute("eventName");
        if(isValidEventNameForScope(eventName, listenerElement)) {
          EventListener listener = parseEventListener(listenerElement);
          if(listener != null) {
            scope.addEventListener(eventName, listener);
          }
        }
      }      
    }
      
  }
  
  /**
   * Check if the given event name is valid. If not, an appropriate error is added.
   */
  private boolean isValidEventNameForScope(String eventName, Element listenerElement) {
    if(eventName != null && eventName.trim().length() > 0) {
      if("start".equals(eventName) || "end".equals(eventName)) {
        return true;
      } else {
        addError("Attribute 'eventName' must be one of {start|end}", listenerElement);
      }
    } else {      
      addError("Attribute 'eventName' is mandatory on listener", listenerElement);
    }
    return false;
  }
  
  public void parseEventListenersOnTransition(Element activitiElement, TransitionImpl activity) {
    Element extentionsElement = activitiElement.element("extensionElements");
    if(extentionsElement != null) {
      List<Element> listenerElements = extentionsElement.elementsNS(BpmnParser.ACTIVITI_BPMN_EXTENSIONS_NS, "listener");
      for(Element listenerElement : listenerElements) {
        EventListener listener = parseEventListener(listenerElement);
        if(listener != null) {
          // Since a transition only fires event 'take', we don't parse the eventName, it is ignored
          activity.addEventListener(listener);
        }
      }      
    }
  }
  
  /**
   * Parses an {@link EventListener} implementation for the given event-listener element.
   * 
   * @param eventListenerElement the XML element containing the event-listener definition.
   */
  public EventListener parseEventListener(Element eventListenerElement) {
    EventListener eventListener = null;
    
    String className = eventListenerElement.attribute("class");
    String expression = eventListenerElement.attribute( "expression");
    
    List<FieldDeclaration> fieldDeclarations = parseFieldDeclarations(eventListenerElement);
    
    if(className != null && className.trim().length() > 0) {
      Object delegateInstance = new DelegateEventListener(className, fieldDeclarations).getDelegateInstance();
      if (delegateInstance instanceof EventListener) {
        eventListener = (EventListener) delegateInstance; 
      } else if (delegateInstance instanceof JavaDelegation) {
        eventListener = new JavaDelegationDelegate((JavaDelegation) delegateInstance);
      } else {
        addError(delegateInstance.getClass().getName()+" doesn't implement "+JavaDelegation.class.getName()+" nor "+EventListener.class.getName(), eventListenerElement);
      }
    } else if(expression != null && expression.trim().length() > 0) {
      eventListener = new ExpressionEventListener(expressionManager.createExpression(expression));
    } else {
      addError("Element 'class' or 'expression' is mandatory on event-listener", eventListenerElement);
    }
    return eventListener;
  }

  /**
   * Retrieves the {@link Operation} corresponding with the given operation
   * identifier.
   */
  public Operation getOperation(String operationId) {
    return operations.get(operationId);
  }

  /* Getters, setters and Parser overriden operations */

  public List<ProcessDefinitionEntity> getProcessDefinitions() {
    return processDefinitions;
  }

  public BpmnParse name(String name) {
    super.name(name);
    return this;
  }

  public BpmnParse sourceInputStream(InputStream inputStream) {
    super.sourceInputStream(inputStream);
    return this;
  }

  public BpmnParse sourceResource(String resource, ClassLoader classLoader) {
    super.sourceResource(resource, classLoader);
    return this;
  }

  public BpmnParse sourceResource(String resource) {
    super.sourceResource(resource);
    return this;
  }

  public BpmnParse sourceString(String string) {
    super.sourceString(string);
    return this;
  }

  public BpmnParse sourceUrl(String url) {
    super.sourceUrl(url);
    return this;
  }

  public BpmnParse sourceUrl(URL url) {
    super.sourceUrl(url);
    return this;
  }

  public void addStructure(Structure structure) {
    this.structures.put(structure.getId(), structure);
  }

  public void addService(BpmnInterfaceImplementation bpmnInterfaceImplementation) {
    this.interfaceImplementations.put(bpmnInterfaceImplementation.getName(), bpmnInterfaceImplementation);
  }

  public void addOperation(OperationImplementation operationImplementation) {
    this.operationImplementations.put(operationImplementation.getName(), operationImplementation);
  }

  public Boolean parseBooleanAttribute(String booleanText) {
    if ("true".equals(booleanText)
        || "enabled".equals(booleanText)
        || "on".equals(booleanText)
        || "active".equals(booleanText)
        || "yes".equals(booleanText)
        ) {
      return Boolean.TRUE;
    }
    if ("false".equals(booleanText)
        || "disabled".equals(booleanText)
        || "off".equals(booleanText)
        || "inactive".equals(booleanText)
        || "no".equals(booleanText)
        ) {
      return Boolean.FALSE;
    }
    return null;
  }
}
