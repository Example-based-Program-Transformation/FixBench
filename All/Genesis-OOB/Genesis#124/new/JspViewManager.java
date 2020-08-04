package org.springframework.roo.addon.web.mvc.jsp;

import java.beans.Introspector;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;

import org.springframework.roo.addon.web.mvc.controller.details.FinderMetadataDetails;
import org.springframework.roo.addon.web.mvc.controller.details.JavaTypeMetadataDetails;
import org.springframework.roo.addon.web.mvc.controller.details.JavaTypePersistenceMetadataDetails;
import org.springframework.roo.addon.web.mvc.controller.scaffold.WebScaffoldAnnotationValues;
import org.springframework.roo.classpath.customdata.PersistenceCustomDataKeys;
import org.springframework.roo.classpath.details.FieldMetadata;
import org.springframework.roo.classpath.details.FieldMetadataBuilder;
import org.springframework.roo.classpath.details.MemberFindingUtils;
import org.springframework.roo.classpath.details.MethodMetadata;
import org.springframework.roo.classpath.details.annotations.AnnotationAttributeValue;
import org.springframework.roo.classpath.details.annotations.AnnotationMetadata;
import org.springframework.roo.model.JavaSymbolName;
import org.springframework.roo.model.JavaType;
import org.springframework.roo.support.util.Assert;
import org.springframework.roo.support.util.StringUtils;
import org.springframework.roo.support.util.XmlElementBuilder;
import org.springframework.roo.support.util.XmlRoundTripUtils;
import org.springframework.roo.support.util.XmlUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Helper class which generates the contents of the various jsp documents
 * 
 * @author Stefan Schmidt
 * @since 1.1
 */
public class JspViewManager {
	
	// Fields
	private final JavaType formbackingType;
	private final JavaTypeMetadataDetails formbackingTypeMetadata;
	private final JavaTypePersistenceMetadataDetails formbackingTypePersistenceMetadata;
	private final List<FieldMetadata> fields;
	private final Map<JavaType, JavaTypeMetadataDetails> relatedDomainTypes;
	private final String entityName;
	private final String controllerPath;
	private final WebScaffoldAnnotationValues webScaffoldAnnotationValues;

	/**
	 * Constructor
	 * 
	 * @param fields can't be <code>null</code>
	 * @param webScaffoldAnnotationValues can't be <code>null</code>
	 * @param relatedDomainTypes can't be <code>null</code>
	 */
	public JspViewManager(final List<FieldMetadata> fields, final WebScaffoldAnnotationValues webScaffoldAnnotationValues, final Map<JavaType, JavaTypeMetadataDetails> relatedDomainTypes) {
		Assert.notNull(fields, "List of fields required");
		Assert.notNull(webScaffoldAnnotationValues, "Web scaffold annotation values required");
		Assert.notNull(relatedDomainTypes, "Related domain types required");
		this.fields = Collections.unmodifiableList(fields);
		this.webScaffoldAnnotationValues = webScaffoldAnnotationValues;
		this.formbackingType = webScaffoldAnnotationValues.getFormBackingObject();
		this.relatedDomainTypes = relatedDomainTypes;
		entityName = uncapitalize(formbackingType.getSimpleTypeName());
		formbackingTypeMetadata = relatedDomainTypes.get(formbackingType);
		Assert.notNull(formbackingTypeMetadata, "Form backing type metadata required");
		formbackingTypePersistenceMetadata = formbackingTypeMetadata.getPersistenceDetails();
		Assert.notNull(formbackingTypePersistenceMetadata, "Persistence metadata required for form backing type");

		Assert.notNull(webScaffoldAnnotationValues.getPath(), "Path is not specified in the @RooWebScaffold annotation for '" + webScaffoldAnnotationValues.getGovernorTypeDetails().getName() + "'");

		if (webScaffoldAnnotationValues.getPath().startsWith("/")) {
			controllerPath = webScaffoldAnnotationValues.getPath();
		} else {
			controllerPath = "/" + webScaffoldAnnotationValues.getPath();
		}
	}

	public Document getListDocument() {
		DocumentBuilder builder = XmlUtils.getDocumentBuilder();
		Document document = builder.newDocument();

		// Add document namespaces
		Element div = new XmlElementBuilder("div", document).addAttribute("xmlns:page", "urn:jsptagdir:/WEB-INF/tags/form").addAttribute("xmlns:table", "urn:jsptagdir:/WEB-INF/tags/form/fields").addAttribute("xmlns:jsp", "http://java.sun.com/JSP/Page").addAttribute("version", "2.0").addChild(new XmlElementBuilder("jsp:directive.page", document).addAttribute("contentType", "text/html;charset=UTF-8").build()).addChild(new XmlElementBuilder("jsp:output", document).addAttribute("omit-xml-declaration", "yes").build()).build();
		document.appendChild(div);

		Element fieldTable = new XmlElementBuilder("table:table", document).addAttribute("id", XmlUtils.convertId("l:" + formbackingType.getFullyQualifiedTypeName())).addAttribute("data", "${" + formbackingTypeMetadata.getPlural().toLowerCase() + "}").addAttribute("path", controllerPath).build();

		if (!webScaffoldAnnotationValues.isUpdate()) {
			fieldTable.setAttribute("update", "false");
		}
		if (!webScaffoldAnnotationValues.isDelete()) {
			fieldTable.setAttribute("delete", "false");
		}
		if (!formbackingTypePersistenceMetadata.getIdentifierField().getFieldName().getSymbolName().equals("id")) {
			fieldTable.setAttribute("typeIdFieldName", formbackingTypePersistenceMetadata.getIdentifierField().getFieldName().getSymbolName());
		}
		fieldTable.setAttribute("z", XmlRoundTripUtils.calculateUniqueKeyFor(fieldTable));

		int fieldCounter = 0;
		for (FieldMetadata field : fields) {
			if (++fieldCounter < 7) {
				Element columnElement = new XmlElementBuilder("table:column", document).addAttribute("id", XmlUtils.convertId("c:" + formbackingType.getFullyQualifiedTypeName() + "." + field.getFieldName().getSymbolName())).addAttribute("property", uncapitalize(field.getFieldName().getSymbolName())).build();
				String fieldName = uncapitalize(field.getFieldName().getSymbolName());
				if (field.getFieldType().equals(new JavaType(Date.class.getName()))) {
					columnElement.setAttribute("date", "true");
					columnElement.setAttribute("dateTimePattern", "${" + entityName + "_" + fieldName.toLowerCase() + "_date_format}");
				} else if (field.getFieldType().equals(new JavaType(Calendar.class.getName()))) {
					columnElement.setAttribute("calendar", "true");
					columnElement.setAttribute("dateTimePattern", "${" + entityName + "_" + fieldName.toLowerCase() + "_date_format}");
				}
				columnElement.setAttribute("z", XmlRoundTripUtils.calculateUniqueKeyFor(columnElement));
				fieldTable.appendChild(columnElement);
			}
		}

		// Create page:list element
		Element pageList = new XmlElementBuilder("page:list", document).addAttribute("id", XmlUtils.convertId("pl:" + formbackingType.getFullyQualifiedTypeName())).addAttribute("items", "${" + formbackingTypeMetadata.getPlural().toLowerCase() + "}").addChild(fieldTable).build();

		pageList.setAttribute("z", XmlRoundTripUtils.calculateUniqueKeyFor(pageList));

		div.appendChild(pageList);

		return document;
	}

	public Document getShowDocument() {
		DocumentBuilder builder = XmlUtils.getDocumentBuilder();
		Document document = builder.newDocument();

		// Add document namespaces
		Element div = (Element) document.appendChild(new XmlElementBuilder("div", document).addAttribute("xmlns:page", "urn:jsptagdir:/WEB-INF/tags/form").addAttribute("xmlns:field", "urn:jsptagdir:/WEB-INF/tags/form/fields").addAttribute("xmlns:jsp", "http://java.sun.com/JSP/Page").addAttribute("version", "2.0").addChild(new XmlElementBuilder("jsp:directive.page", document).addAttribute("contentType", "text/html;charset=UTF-8").build()).addChild(new XmlElementBuilder("jsp:output", document).addAttribute("omit-xml-declaration", "yes").build()).build());

		Element pageShow = new XmlElementBuilder("page:show", document).addAttribute("id", XmlUtils.convertId("ps:" + formbackingType.getFullyQualifiedTypeName())).addAttribute("object", "${" + entityName.toLowerCase() + "}").addAttribute("path", controllerPath).build();
		if (!webScaffoldAnnotationValues.isCreate()) {
			pageShow.setAttribute("create", "false");
		}
		if (!webScaffoldAnnotationValues.isUpdate()) {
			pageShow.setAttribute("update", "false");
		}
		if (!webScaffoldAnnotationValues.isDelete()) {
			pageShow.setAttribute("delete", "false");
		}
		pageShow.setAttribute("z", XmlRoundTripUtils.calculateUniqueKeyFor(pageShow));

		// Add field:display elements for each field
		for (FieldMetadata field : fields) {
			// Ignoring java.util.Map field types (see ROO-194)
			if (field.getFieldType().equals(new JavaType(Map.class.getName()))) {
				continue;
			}
			String fieldName = uncapitalize(field.getFieldName().getSymbolName());
			Element fieldDisplay = new XmlElementBuilder("field:display", document).addAttribute("id", XmlUtils.convertId("s:" + formbackingType.getFullyQualifiedTypeName() + "." + field.getFieldName().getSymbolName())).addAttribute("object", "${" + entityName.toLowerCase() + "}").addAttribute("field", fieldName).build();
			if (field.getFieldType().equals(new JavaType(Date.class.getName()))) {
				fieldDisplay.setAttribute("date", "true");
				fieldDisplay.setAttribute("dateTimePattern", "${" + entityName + "_" + fieldName.toLowerCase() + "_date_format}");
			} else if (field.getFieldType().equals(new JavaType(Calendar.class.getName()))) {
				fieldDisplay.setAttribute("calendar", "true");
				fieldDisplay.setAttribute("dateTimePattern", "${" + entityName + "_" + fieldName.toLowerCase() + "_date_format}");
			} else if (field.getFieldType().isCommonCollectionType() && field.getCustomData().get(PersistenceCustomDataKeys.ONE_TO_MANY_FIELD) != null) {
				continue;
			}
			fieldDisplay.setAttribute("z", XmlRoundTripUtils.calculateUniqueKeyFor(fieldDisplay));
			
			pageShow.appendChild(fieldDisplay);
		}
		div.appendChild(pageShow);

		return document;
	}

	public Document getCreateDocument() {
		DocumentBuilder builder = XmlUtils.getDocumentBuilder();
		Document document = builder.newDocument();
		
		// Add document namespaces
		Element div = (Element) document.appendChild(new XmlElementBuilder("div", document).addAttribute("xmlns:form", "urn:jsptagdir:/WEB-INF/tags/form").addAttribute("xmlns:field", "urn:jsptagdir:/WEB-INF/tags/form/fields").addAttribute("xmlns:jsp", "http://java.sun.com/JSP/Page").addAttribute("xmlns:c", "http://java.sun.com/jsp/jstl/core").addAttribute("xmlns:spring", "http://www.springframework.org/tags").addAttribute("version", "2.0").addChild(new XmlElementBuilder("jsp:directive.page", document).addAttribute("contentType", "text/html;charset=UTF-8").build()).addChild(new XmlElementBuilder("jsp:output", document).addAttribute("omit-xml-declaration", "yes").build()).build());
		
		// Add form create element
		Element formCreate = new XmlElementBuilder("form:create", document).addAttribute("id", XmlUtils.convertId("fc:" + formbackingType.getFullyQualifiedTypeName())).addAttribute("modelAttribute", entityName).addAttribute("path", controllerPath).addAttribute("render", "${empty dependencies}").build();
		
		if (!controllerPath.toLowerCase().equals(formbackingType.getSimpleTypeName().toLowerCase())) {
			formCreate.setAttribute("path", controllerPath);
		}
		
		final List<FieldMetadata> formFields = new ArrayList<FieldMetadata>();
		final List<FieldMetadata> fieldCopy = new ArrayList<FieldMetadata>(fields);
		
		// Handle Roo identifiers
		if (!formbackingTypePersistenceMetadata.getRooIdentifierFields().isEmpty()) {
			formCreate.setAttribute("compositePkField", formbackingTypePersistenceMetadata.getIdentifierField().getFieldName().getSymbolName());
			for (FieldMetadata embeddedField : formbackingTypePersistenceMetadata.getRooIdentifierFields()) {
				FieldMetadataBuilder fieldBuilder = new FieldMetadataBuilder(embeddedField);
				fieldBuilder.setFieldName(new JavaSymbolName(formbackingTypePersistenceMetadata.getIdentifierField().getFieldName().getSymbolName() + "." + embeddedField.getFieldName().getSymbolName()));
				fieldLoop: for (int i = 0; i < fieldCopy.size(); i++) {
					// Make sure form fields are not presented twice.
					if (!(fieldCopy.get(i).getFieldName().equals(embeddedField.getFieldName()) && fieldCopy.get(i).getFieldType().equals(embeddedField.getFieldType()))) {
						fieldCopy.remove(i);
						break fieldLoop;
					}
				}
				formFields.add(fieldBuilder.build());
			}
		}
		formFields.addAll(fieldCopy);
		
		createFieldsForCreateAndUpdate(formFields, document, formCreate, true);
		formCreate.setAttribute("z", XmlRoundTripUtils.calculateUniqueKeyFor(formCreate));
		
		Element dependency = new XmlElementBuilder("form:dependency", document).addAttribute("id", XmlUtils.convertId("d:" + formbackingType.getFullyQualifiedTypeName())).addAttribute("render", "${not empty dependencies}").addAttribute("dependencies", "${dependencies}").build();
		dependency.setAttribute("z", XmlRoundTripUtils.calculateUniqueKeyFor(dependency));
		
		div.appendChild(formCreate);
		div.appendChild(dependency);
		
		return document;
	}

	public Document getUpdateDocument() {
		DocumentBuilder builder = XmlUtils.getDocumentBuilder();
		Document document = builder.newDocument();

		// Add document namespaces
		Element div = (Element) document.appendChild(new XmlElementBuilder("div", document).addAttribute("xmlns:form", "urn:jsptagdir:/WEB-INF/tags/form").addAttribute("xmlns:field", "urn:jsptagdir:/WEB-INF/tags/form/fields").addAttribute("xmlns:jsp", "http://java.sun.com/JSP/Page").addAttribute("version", "2.0").addChild(new XmlElementBuilder("jsp:directive.page", document).addAttribute("contentType", "text/html;charset=UTF-8").build()).addChild(new XmlElementBuilder("jsp:output", document).addAttribute("omit-xml-declaration", "yes").build()).build());

		// Add form update element
		Element formUpdate = new XmlElementBuilder("form:update", document).addAttribute("id", XmlUtils.convertId("fu:" + formbackingType.getFullyQualifiedTypeName())).addAttribute("modelAttribute", entityName).build();

		if (!controllerPath.toLowerCase().equals(formbackingType.getSimpleTypeName().toLowerCase())) {
			formUpdate.setAttribute("path", controllerPath);
		}
		if (!"id".equals(formbackingTypePersistenceMetadata.getIdentifierField().getFieldName().getSymbolName())) {
			formUpdate.setAttribute("idField", formbackingTypePersistenceMetadata.getIdentifierField().getFieldName().getSymbolName());
		}
		final MethodMetadata versionAccessorMethod = formbackingTypePersistenceMetadata.getVersionAccessorMethod();
		if (versionAccessorMethod == null) {
			formUpdate.setAttribute("versionField", "none");
		} else {
			final String methodName = versionAccessorMethod.getMethodName().getSymbolName();
			formUpdate.setAttribute("versionField", methodName.substring("get".length()));
		}

		createFieldsForCreateAndUpdate(fields, document, formUpdate, false);
		formUpdate.setAttribute("z", XmlRoundTripUtils.calculateUniqueKeyFor(formUpdate));
		div.appendChild(formUpdate);

		return document;
	}

	public Document getFinderDocument(FinderMetadataDetails finderMetadataDetails) {
		DocumentBuilder builder = XmlUtils.getDocumentBuilder();
		Document document = builder.newDocument();

		// Add document namespaces
		Element div = (Element) document.appendChild(new XmlElementBuilder("div", document).addAttribute("xmlns:form", "urn:jsptagdir:/WEB-INF/tags/form").addAttribute("xmlns:field", "urn:jsptagdir:/WEB-INF/tags/form/fields").addAttribute("xmlns:jsp", "http://java.sun.com/JSP/Page").addAttribute("version", "2.0").addChild(new XmlElementBuilder("jsp:directive.page", document).addAttribute("contentType", "text/html;charset=UTF-8").build()).addChild(new XmlElementBuilder("jsp:output", document).addAttribute("omit-xml-declaration", "yes").build()).build());

		Element formFind = new XmlElementBuilder("form:find", document).addAttribute("id", XmlUtils.convertId("ff:" + formbackingType.getFullyQualifiedTypeName())).addAttribute("path", controllerPath).addAttribute("finderName", finderMetadataDetails.getFinderMethodMetadata().getMethodName().getSymbolName().replace("find" + formbackingTypeMetadata.getPlural(), "")).build();
		formFind.setAttribute("z", XmlRoundTripUtils.calculateUniqueKeyFor(formFind));

		div.appendChild(formFind);

		for (FieldMetadata field: finderMetadataDetails.getFinderMethodParamFields()) {
			JavaType type = field.getFieldType();
			JavaSymbolName paramName = field.getFieldName();
			
			// Ignoring java.util.Map field types (see ROO-194)
			if (type.equals(new JavaType(Map.class.getName()))) {
				continue;
			}
			Assert.notNull(paramName, "could not find field '" + paramName + "' in '" + type.getFullyQualifiedTypeName() + "'");
			Element fieldElement = null;
			
			JavaTypeMetadataDetails typeMetadataHolder = relatedDomainTypes.get(getJavaTypeForField(field));
			
			if (type.isCommonCollectionType() && relatedDomainTypes.containsKey(getJavaTypeForField(field))) {
				JavaTypeMetadataDetails collectionTypeMetadataHolder = relatedDomainTypes.get(getJavaTypeForField(field));
				JavaTypePersistenceMetadataDetails typePersistenceMetadataHolder = collectionTypeMetadataHolder.getPersistenceDetails();
				if (typePersistenceMetadataHolder != null) {
					fieldElement = new XmlElementBuilder("field:select", document).addAttribute("required", "true").addAttribute("items", "${" + collectionTypeMetadataHolder.getPlural().toLowerCase() + "}").addAttribute("itemValue", typePersistenceMetadataHolder.getIdentifierField().getFieldName().getSymbolName()).addAttribute("path", "/" + getPathForType(getJavaTypeForField(field))).build();
					if (field.getCustomData().keySet().contains(PersistenceCustomDataKeys.MANY_TO_MANY_FIELD)) {
						fieldElement.setAttribute("multiple", "true");
					}
				}
			} else if (typeMetadataHolder != null && typeMetadataHolder.isEnumType() && field.getCustomData().keySet().contains(PersistenceCustomDataKeys.ENUMERATED_FIELD)) {
				fieldElement = new XmlElementBuilder("field:select", document).addAttribute("required", "true").addAttribute("items", "${" + typeMetadataHolder.getPlural().toLowerCase() + "}").addAttribute("path", "/" + getPathForType(type)).build();
			} else if (type.getFullyQualifiedTypeName().equals(Boolean.class.getName()) || type.getFullyQualifiedTypeName().equals(boolean.class.getName())) {
				fieldElement = document.createElement("field:checkbox");
			} else if (typeMetadataHolder != null && typeMetadataHolder.isApplicationType()) {
				JavaTypePersistenceMetadataDetails typePersistenceMetadataHolder = typeMetadataHolder.getPersistenceDetails();
				if (typePersistenceMetadataHolder != null) {
					fieldElement = new XmlElementBuilder("field:select", document).addAttribute("required", "true").addAttribute("items", "${" + typeMetadataHolder.getPlural().toLowerCase() + "}").addAttribute("itemValue", typePersistenceMetadataHolder.getIdentifierField().getFieldName().getSymbolName()).addAttribute("path", "/" + getPathForType(type)).build();
				}
			} else if (type.getFullyQualifiedTypeName().equals(Date.class.getName()) || type.getFullyQualifiedTypeName().equals(Calendar.class.getName())) {
				fieldElement = new XmlElementBuilder("field:datetime", document).addAttribute("required", "true").addAttribute("dateTimePattern", "${" + entityName + "_" + paramName.getSymbolName().toLowerCase() + "_date_format}").build();
			} 
			if (fieldElement == null) {
				fieldElement = new XmlElementBuilder("field:input", document).addAttribute("required", "true").build();
			}
			addCommonAttributes(field, fieldElement);
			fieldElement.setAttribute("disableFormBinding", "true");
			fieldElement.setAttribute("field", paramName.getSymbolName());
			fieldElement.setAttribute("id", XmlUtils.convertId("f:" + formbackingType.getFullyQualifiedTypeName() + "." + paramName));
			fieldElement.setAttribute("z", XmlRoundTripUtils.calculateUniqueKeyFor(fieldElement));
			formFind.appendChild(fieldElement);
		}
		
		XmlUtils.removeTextNodes(document);
		return document;
	}

	private void createFieldsForCreateAndUpdate(List<FieldMetadata> formFields, Document document, Element root, boolean isCreate) {
		for (FieldMetadata field : formFields) {
			String fieldName = field.getFieldName().getSymbolName();
			JavaType fieldType = field.getFieldType();
			AnnotationMetadata annotationMetadata;

			// Ignoring java.util.Map field types (see ROO-194)
			if (fieldType.equals(new JavaType(Map.class.getName()))) {
				continue;
			}
			// Fields contained in the embedded Id type have been added separately to the field list
			if (field.getCustomData().keySet().contains(PersistenceCustomDataKeys.EMBEDDED_ID_FIELD)) {
				continue;
			}
			
			fieldType = getJavaTypeForField(field);
			
			JavaTypeMetadataDetails typeMetadataHolder = relatedDomainTypes.get(fieldType);
			JavaTypePersistenceMetadataDetails typePersistenceMetadataHolder = null;
			if (typeMetadataHolder != null) {
				typePersistenceMetadataHolder = typeMetadataHolder.getPersistenceDetails();
			}

			Element fieldElement = null;

			if (fieldType.getFullyQualifiedTypeName().equals(Boolean.class.getName()) || fieldType.getFullyQualifiedTypeName().equals(boolean.class.getName())) {
				fieldElement = document.createElement("field:checkbox");
				// Handle enum fields
			} else if (typeMetadataHolder != null && typeMetadataHolder.isEnumType()) {
				fieldElement = new XmlElementBuilder("field:select", document).addAttribute("items", "${" + typeMetadataHolder.getPlural().toLowerCase() + "}").addAttribute("path", getPathForType(fieldType)).build();
			} else if (field.getCustomData().keySet().contains(PersistenceCustomDataKeys.ONE_TO_MANY_FIELD)) {
				// OneToMany relationships are managed from the 'many' side of the relationship, therefore we provide a link to the relevant form
				// the link URL is determined as a best effort attempt following Roo REST conventions, this link might be wrong if custom paths are used
				// if custom paths are used the developer can adjust the path attribute in the field:reference tag accordingly
				if (typePersistenceMetadataHolder != null) {
					fieldElement = new XmlElementBuilder("field:simple", document).addAttribute("messageCode", "entity_reference_not_managed").addAttribute("messageCodeAttribute", new JavaSymbolName(fieldType.getSimpleTypeName()).getReadableSymbolName()).build();
				} else {
					continue;
				}
			} else if (field.getCustomData().keySet().contains(PersistenceCustomDataKeys.MANY_TO_ONE_FIELD) || field.getCustomData().keySet().contains(PersistenceCustomDataKeys.MANY_TO_MANY_FIELD) || field.getCustomData().keySet().contains(PersistenceCustomDataKeys.ONE_TO_ONE_FIELD)) {
				JavaType referenceType = getJavaTypeForField(field);
				JavaTypeMetadataDetails referenceTypeMetadata = relatedDomainTypes.get(referenceType);
				if (referenceType != null/** fix for ROO-1888 --> **/ && referenceTypeMetadata != null && referenceTypeMetadata.isApplicationType() && typePersistenceMetadataHolder != null) {
					fieldElement = new XmlElementBuilder("field:select", document).addAttribute("items", "${" + referenceTypeMetadata.getPlural().toLowerCase() + "}").addAttribute("itemValue", typePersistenceMetadataHolder.getIdentifierField().getFieldName().getSymbolName()).addAttribute("path", "/" + getPathForType(getJavaTypeForField(field))).build();

					if (field.getCustomData().keySet().contains(PersistenceCustomDataKeys.MANY_TO_MANY_FIELD)) {
						fieldElement.setAttribute("multiple", "true");
					}
				}
			} else if (fieldType.getFullyQualifiedTypeName().equals(Date.class.getName()) || fieldType.getFullyQualifiedTypeName().equals(Calendar.class.getName())) {
				// Only include the date picker for styles supported by Dojo (SMALL & MEDIUM)
				fieldElement = new XmlElementBuilder("field:datetime", document).addAttribute("dateTimePattern", "${" + entityName + "_" + fieldName.toLowerCase() + "_date_format}").build();

				if (null != MemberFindingUtils.getAnnotationOfType(field.getAnnotations(), new JavaType("javax.validation.constraints.Future"))) {
					fieldElement.setAttribute("future", "true");
				} else if (null != MemberFindingUtils.getAnnotationOfType(field.getAnnotations(), new JavaType("javax.validation.constraints.Past"))) {
					fieldElement.setAttribute("past", "true");
				}
			} else if (field.getCustomData().keySet().contains(PersistenceCustomDataKeys.LOB_FIELD)) {
				fieldElement = new XmlElementBuilder("field:textarea", document).build();
			} 
			if (null != (annotationMetadata = MemberFindingUtils.getAnnotationOfType(field.getAnnotations(), new JavaType("javax.validation.constraints.Size")))) {
				AnnotationAttributeValue<?> max = annotationMetadata.getAttribute(new JavaSymbolName("max"));
				if (max != null) {
					int maxValue = (Integer) max.getValue();
					if (fieldElement == null && maxValue > 30) {
						fieldElement = new XmlElementBuilder("field:textarea", document).build();
					}
				}
			}
			// Use a default input field if no other criteria apply
			if (fieldElement == null) {
				fieldElement = document.createElement("field:input");
			}
			addCommonAttributes(field, fieldElement);
			fieldElement.setAttribute("field", fieldName);
			fieldElement.setAttribute("id", XmlUtils.convertId("c:" + formbackingType.getFullyQualifiedTypeName() + "." + field.getFieldName().getSymbolName()));
			fieldElement.setAttribute("z", XmlRoundTripUtils.calculateUniqueKeyFor(fieldElement));

			root.appendChild(fieldElement);
		}
	}

	private JavaType getJavaTypeForField(FieldMetadata field) {
		if (field.getFieldType().isCommonCollectionType()) {
			// Currently there is no scaffolding available for Maps (see ROO-194)
			if (field.getFieldType().equals(new JavaType(Map.class.getName()))) {
				return null;
			}
			List<JavaType> parameters = field.getFieldType().getParameters();
			if (parameters.isEmpty()) {
				throw new IllegalStateException("Unable to determine the parameter type for the " + field.getFieldName().getSymbolName() + " field in " + formbackingType.getSimpleTypeName());
			}
			return parameters.get(0);
		}
		return field.getFieldType();
	}

	private String getPathForType(JavaType type) {
		JavaTypeMetadataDetails javaTypeMetadataHolder = relatedDomainTypes.get(type);
		Assert.notNull(javaTypeMetadataHolder, "Unable to obtain metadata for type " + type.getFullyQualifiedTypeName());
		return javaTypeMetadataHolder.getControllerPath();
	}

	private void addCommonAttributes(FieldMetadata field, Element fieldElement) {
		AnnotationMetadata annotationMetadata;
		if (field.getFieldType().equals(JavaType.INT_OBJECT) || field.getFieldType().getFullyQualifiedTypeName().equals(int.class.getName()) || field.getFieldType().equals(JavaType.SHORT_OBJECT) || field.getFieldType().getFullyQualifiedTypeName().equals(short.class.getName()) || field.getFieldType().equals(new JavaType(Long.class.getName())) || field.getFieldType().getFullyQualifiedTypeName().equals(long.class.getName()) || field.getFieldType().equals(new JavaType("java.math.BigInteger"))) {
			fieldElement.setAttribute("validationMessageCode", "field_invalid_integer");
		} else if (uncapitalize(field.getFieldName().getSymbolName()).contains("email")) {
			fieldElement.setAttribute("validationMessageCode", "field_invalid_email");
		} else if (field.getFieldType().equals(JavaType.DOUBLE_OBJECT) || field.getFieldType().getFullyQualifiedTypeName().equals(double.class.getName()) || field.getFieldType().equals(JavaType.FLOAT_OBJECT) || field.getFieldType().getFullyQualifiedTypeName().equals(float.class.getName()) || field.getFieldType().equals(new JavaType("java.math.BigDecimal"))) {
			fieldElement.setAttribute("validationMessageCode", "field_invalid_number");
		}
		if ("field:input".equals(fieldElement.getTagName()) && null != (annotationMetadata = MemberFindingUtils.getAnnotationOfType(field.getAnnotations(), new JavaType("javax.validation.constraints.Min")))) {
			AnnotationAttributeValue<?> min = annotationMetadata.getAttribute(new JavaSymbolName("value"));
			if (min != null) {
				fieldElement.setAttribute("min", min.getValue().toString());
				fieldElement.setAttribute("required", "true");
			}
		}
		if ("field:input".equals(fieldElement.getTagName()) && null != (annotationMetadata = MemberFindingUtils.getAnnotationOfType(field.getAnnotations(), new JavaType("javax.validation.constraints.Max"))) && !"field:textarea".equals(fieldElement.getTagName())) {
			AnnotationAttributeValue<?> maxA = annotationMetadata.getAttribute(new JavaSymbolName("value"));
			if (maxA != null) {
				fieldElement.setAttribute("max", maxA.getValue().toString());
			}
		}
		if ("field:input".equals(fieldElement.getTagName()) && null != (annotationMetadata = MemberFindingUtils.getAnnotationOfType(field.getAnnotations(), new JavaType("javax.validation.constraints.DecimalMin"))) && !"field:textarea".equals(fieldElement.getTagName())) {
			AnnotationAttributeValue<?> decimalMin = annotationMetadata.getAttribute(new JavaSymbolName("value"));
			if (decimalMin != null) {
				fieldElement.setAttribute("decimalMin", decimalMin.getValue().toString());
				fieldElement.setAttribute("required", "true");
			}
		}
		if ("field:input".equals(fieldElement.getTagName()) && null != (annotationMetadata = MemberFindingUtils.getAnnotationOfType(field.getAnnotations(), new JavaType("javax.validation.constraints.DecimalMax")))) {
			AnnotationAttributeValue<?> decimalMax = annotationMetadata.getAttribute(new JavaSymbolName("value"));
			if (decimalMax != null) {
				fieldElement.setAttribute("decimalMax", decimalMax.getValue().toString());
			}
		}
		if (null != (annotationMetadata = MemberFindingUtils.getAnnotationOfType(field.getAnnotations(), new JavaType("javax.validation.constraints.Pattern")))) {
			AnnotationAttributeValue<?> regexp = annotationMetadata.getAttribute(new JavaSymbolName("regexp"));
			if (regexp != null) {
				fieldElement.setAttribute("validationRegex", regexp.getValue().toString());
			}
		}
		if ("field:input".equals(fieldElement.getTagName()) && null != (annotationMetadata = MemberFindingUtils.getAnnotationOfType(field.getAnnotations(), new JavaType("javax.validation.constraints.Size")))) {
			AnnotationAttributeValue<?> max = annotationMetadata.getAttribute(new JavaSymbolName("max"));
			if (max != null) {
				fieldElement.setAttribute("max", max.getValue().toString());
			}
			AnnotationAttributeValue<?> min = annotationMetadata.getAttribute(new JavaSymbolName("min"));
			if (min != null) {
				fieldElement.setAttribute("min", min.getValue().toString());
				fieldElement.setAttribute("required", "true");
			}
		}
		if (null != (annotationMetadata = MemberFindingUtils.getAnnotationOfType(field.getAnnotations(), new JavaType("javax.validation.constraints.NotNull")))) {
			String tagName = fieldElement.getTagName();
			if (tagName.endsWith("textarea") || tagName.endsWith("input") || tagName.endsWith("datetime") || tagName.endsWith("textarea") || tagName.endsWith("select") || tagName.endsWith("reference")) {
				fieldElement.setAttribute("required", "true");
			}
		}
		if (field.getCustomData().keySet().contains(PersistenceCustomDataKeys.COLUMN_FIELD)) {
			@SuppressWarnings("unchecked")
			Map<String, Object> values = (Map<String, Object>) field.getCustomData().get(PersistenceCustomDataKeys.COLUMN_FIELD);
			if (values.keySet().contains("nullable") && ((Boolean) values.get("nullable")) == false) {
				fieldElement.setAttribute("required", "true"); 
			}
		}
		// Disable form binding for nested fields (mainly PKs)
		if (field.getFieldName().getSymbolName().contains(".")) {
			fieldElement.setAttribute("disableFormBinding", "true");
		}
	}

	private String uncapitalize(String term) {
		// [ROO-1790] this is needed to adhere to the JavaBean naming
		// conventions (see JavaBean spec section 8.8)
		return Introspector.decapitalize(StringUtils.capitalize(term));
	}
}
