package com.ugcs.mavlink.generator;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import com.sun.codemodel.JClass;
import com.sun.codemodel.JClassAlreadyExistsException;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JEnumConstant;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JForLoop;
import com.sun.codemodel.JInvocation;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import com.sun.codemodel.JOp;
import com.sun.codemodel.JPackage;
import com.sun.codemodel.JSwitch;
import com.sun.codemodel.JType;
import com.sun.codemodel.JVar;
import com.ugcs.mavlink.CodedInputStream;
import com.ugcs.mavlink.CodedOutputStream;
import com.ugcs.mavlink.MavlinkCrc;
import com.ugcs.mavlink.MavlinkMessage;
import com.ugcs.mavlink.MavlinkMessageBuilder;
import com.ugcs.mavlink.ProtocolDescriptor;
import com.ugcs.mavlink.xmlschema.Entry;
import com.ugcs.mavlink.xmlschema.Field;
import com.ugcs.mavlink.xmlschema.Mavlink;
import com.ugcs.mavlink.xmlschema.Param;

public class Generator {
	private static final String MAVLINK_VERSION_0_9 = "0.9";
	private static final String MAVLINK_VERSION_1_0 = "1.0";

	/* mavlink configuration */
	
	private String mavlinkVersion;
	private int mavlinkStx;
	private boolean crcExtraByte;
	private boolean fieldsReordering;
	private boolean littleEndian;
	private boolean expandedSystemId;

	/* generator state */
	
	private State state;
	
	static class State {
	
		/* args */
		
		private String packageName = "com.ugcs.mavlink.messages";
		private String sourceDirectoryPath = "src/main/java";
		
		/* loaded from message definitons (xml) */
		
		private int mavlinkMessagesVersion;
		private Map<String, Set<com.ugcs.mavlink.xmlschema.Enum>> mavlinkEnums = 
				new HashMap<String, Set<com.ugcs.mavlink.xmlschema.Enum>>();
		private Map<Integer, com.ugcs.mavlink.xmlschema.Message> mavlinkMessages = 
				new HashMap<Integer, com.ugcs.mavlink.xmlschema.Message>();
		
		/* ganarator state */
		
		private Map<Integer, JDefinedClass> messageClasses = new HashMap<Integer, JDefinedClass>();
		private Map<Integer, JDefinedClass> messageBuilderClasses = new HashMap<Integer, JDefinedClass>();
		private Map<Integer, Integer> messageLengths = new HashMap<Integer, Integer>();
		private Map<Integer, Integer> messageCrcExtraBytes = new HashMap<Integer, Integer>();
	}

	public Generator(String mavlinkVersion, boolean expandedSystemId) {
		if (mavlinkVersion == null || mavlinkVersion.isEmpty())
			throw new IllegalArgumentException("mavlinkVersion");
		
		if (mavlinkVersion.equals(MAVLINK_VERSION_0_9)) {
			this.mavlinkVersion = MAVLINK_VERSION_0_9;
			this.mavlinkStx = 0x55;
			this.crcExtraByte = false;
			this.fieldsReordering = false;
			this.littleEndian = false;
			this.expandedSystemId = expandedSystemId;
			return;
		}
		if (mavlinkVersion.equals(MAVLINK_VERSION_1_0)) {
			this.mavlinkVersion = MAVLINK_VERSION_1_0;
			this.mavlinkStx = 0xfe;
			this.crcExtraByte = true;
			this.fieldsReordering = true;
			this.littleEndian = true;
			this.expandedSystemId = expandedSystemId;
			return;
		}
		throw new IllegalArgumentException("Unsupported Mavlink version: " + mavlinkVersion);
	}		
		
	public void generate(String path, String sourceDirectoryPath, String packageName) throws IOException, JAXBException, JClassAlreadyExistsException {
		if (path == null || path.isEmpty())
			throw new IllegalArgumentException("Empty path");
		path = path.replace('\\', File.separatorChar);
		path = path.replace('/', File.separatorChar);
		
		// reset generator state
		state = new State();
		
		// load definitions
		loadMessageDefinitions(path, null);
		
		if (sourceDirectoryPath == null || sourceDirectoryPath.isEmpty()) {
			state.sourceDirectoryPath = ".";
		} else {
			state.sourceDirectoryPath = sourceDirectoryPath;
		}
		if (packageName == null || packageName.isEmpty()) {
			String basename = "";
			int k = path.lastIndexOf(File.separatorChar);
			if (k == -1)
				basename = path;
			basename = path.substring(k + 1);
			String name = ""; 
			int k2 = basename.lastIndexOf('.');
			if (k2 == -1)
				name = basename;
			name = basename.substring(0, k2);
			state.packageName = (name.length() == 0 ? "messages" : name) + 
					".v" + Integer.toString(state.mavlinkMessagesVersion); 
		} else {
			state.packageName = packageName;
		}
		
		// generate enums and messages
		for (Map.Entry<String, Set<com.ugcs.mavlink.xmlschema.Enum>> entry : state.mavlinkEnums.entrySet())
			generateEnum(entry.getKey(), entry.getValue());
		for (com.ugcs.mavlink.xmlschema.Message message : state.mavlinkMessages.values())
			generateMessage(message);
		// generate descriptor
		generateMavlinkDescriptor();
	}	
	
	private Mavlink unmarshalMessageDefinitions(File f) throws IOException, JAXBException {
		InputStream in = new BufferedInputStream(new FileInputStream(f));
		try {
			JAXBContext context = JAXBContext.newInstance(Mavlink.class);
			Unmarshaller unmarshaller = context.createUnmarshaller();
			return (Mavlink) unmarshaller.unmarshal(in);
		} finally {
			in.close();
		}
	}
	
	private void loadMessageDefinitions(String path, Set<File> visited) throws IOException, JAXBException {
		if (path == null || path.isEmpty())
			throw new IllegalArgumentException("path");

		if (visited == null)
			visited = new HashSet<File>();
		File f = new File(path);
		if (visited.contains(f))
			return;
		
		visited.add(f);
		Mavlink mavlink = unmarshalMessageDefinitions(f);
		
		// version
		if (visited.isEmpty())
			state.mavlinkMessagesVersion = mavlink.getVersion();
		
		// include
		List<String> includes = mavlink.getInclude();
		if (includes != null) {
			for (String include : includes) {
				if (include == null || include.isEmpty())
					continue;
				if (include.charAt(0) == File.separatorChar)
					loadMessageDefinitions(include, visited);
				else
					loadMessageDefinitions(f.getParent() + File.separatorChar + include, visited);
			}
		}
		
		// enums
		if (mavlink.getEnums() != null) {
			for (com.ugcs.mavlink.xmlschema.Enum enum_ : mavlink.getEnums().getEnum()) {
				// checks
				if (enum_ == null)
					throw new IllegalArgumentException("Empty enum");				
				if (enum_.getName() == null || enum_.getName().isEmpty())
					throw new IllegalArgumentException("Empty enum name");				
				
				Set<com.ugcs.mavlink.xmlschema.Enum> enums = state.mavlinkEnums.get(enum_.getName());
				if (enums == null) {
					enums = new HashSet<com.ugcs.mavlink.xmlschema.Enum>();
					state.mavlinkEnums.put(enum_.getName(), enums);
				}
				enums.add(enum_);
			}
		}
		
		// messages
		if (mavlink.getMessages() != null) {
			for (com.ugcs.mavlink.xmlschema.Message message : mavlink.getMessages().getMessage()) {
				// checks
				if (message == null)
					throw new IllegalArgumentException("Empty message");
				if (message.getName() == null || message.getName().isEmpty())
					throw new IllegalArgumentException("Empty message name");
				Integer messageId = Integer.valueOf(message.getId());
				if (state.mavlinkMessages.containsKey(messageId))
					throw new IllegalArgumentException("Duplicate message type " + messageId + 
							" for message " + message.getName());				
				
				state.mavlinkMessages.put(messageId, message);
			}
		}
	}
	
	/* ENUMS */
	
	static Integer parseEntryValue(String value) {
		if (value == null || value.isEmpty())
			return null;
		if (value.startsWith("0b") || value.startsWith("0B"))
			return (int) Long.parseLong(value.substring(2), 2);
		if (value.startsWith("0x") || value.startsWith("0X"))
			return (int) Long.parseLong(value.substring(2), 16);
		return (int) Long.parseLong(value);
	}
	
	public void generateEnum(String enumName, Set<com.ugcs.mavlink.xmlschema.Enum> enums) throws JClassAlreadyExistsException, IOException {
		if (enumName == null || enumName.isEmpty())
			throw new IllegalArgumentException("Empty enum name");
		if (enums == null || enums.isEmpty())
			throw new IllegalArgumentException("Empty enum set for name " + enumName);

		Map<Entry, Integer> entries = new LinkedHashMap<Entry, Integer>();
		Integer maxValue = -1;
		
		for (com.ugcs.mavlink.xmlschema.Enum enum_ : enums) {
			for (Entry entry : enum_.getEntry()) {
				Integer entryValue = parseEntryValue(entry.getValue());
				if (entryValue != null)
					maxValue = Math.max(maxValue, entryValue);
				entries.put(entry, entryValue);
			}
		}
		
		for (Map.Entry<Entry, Integer> entryValue : entries.entrySet()) {
			if (entryValue.getValue() == null)
				entryValue.setValue(++maxValue);
		}

		/* decl */
		
		JCodeModel cm = new JCodeModel();
		JPackage pkg = cm._package(state.packageName);
		
		String enumClassName = JavaNames.toClassName(enumName);
		JDefinedClass enumClass = pkg._enum(enumClassName);
		for (com.ugcs.mavlink.xmlschema.Enum enum_ : enums)
			enumClass.javadoc().add(wrapToLines(enum_.getDescription()));

		/* enum class */
		
		enumConstants(cm, enumClass, entries);
		enumFields(cm, enumClass, entries);
		
		enumCtor(cm, enumClass);
		enumGetValue(cm, enumClass);
		enumValueOf(cm, enumClass, entries);

		// saving class file
		cm.build(new File(state.sourceDirectoryPath));
	}
	
	private void enumConstants(JCodeModel cm, JDefinedClass enumClass, Map<Entry, Integer> entries) {
		for (Map.Entry<Entry, Integer> item : entries.entrySet()) {
			Entry entry = item.getKey();
			Integer value = item.getValue();
			String entryName = JavaNames.toConstantName(entry.getName());
			
			// ENUM CONSTANT: {entry.name}
			JEnumConstant enumConstant = enumClass.enumConstant(entryName);
			enumConstant.arg(JExpr.lit(value));
			if (entry.getValue() == null) {
				enumConstant.javadoc().add("Warning! AUTOGENERATED VALUE!\n");	
				enumConstant.javadoc().add("Message definition doesn't specify value for the constant\n");	
			}
			enumConstant.javadoc().add(wrapToLines(entry.getDescription()));
			if (!entry.getParam().isEmpty()) {
				enumConstant.javadoc().add("\nParams:\n");
				for (Param param : entry.getParam()) {
					enumConstant.javadoc().add(wrapToLines(Integer.toString(param.getIndex()) + ": " + param.getContent()));
					enumConstant.javadoc().add("\n");
				}
			}
		}
	}

	private void enumFields(JCodeModel cm, JDefinedClass enumClass, Map<Entry, Integer> entries) {
		for (Map.Entry<Entry, Integer> item : entries.entrySet()) {
			Entry entry = item.getKey();
			Integer value = item.getValue();
			String entryName = JavaNames.toConstantName(entry.getName());
			String fieldName = JavaNames.toConstantName(entry.getName() + "_value");
			
			// FIELD: {entry.name}_VALUE
			JFieldVar valueField = enumClass.field(JMod.PUBLIC | JMod.STATIC | JMod.FINAL, cm.INT, fieldName, JExpr.lit(value));
			valueField.javadoc().add("Value for the {@code " + entryName + "} enum constant");
		}
		
		// FIELD: value
		enumClass.field(JMod.PRIVATE | JMod.FINAL, cm.INT, "value");
	}

	private void enumCtor(JCodeModel cm, JDefinedClass enumClass) {
		// METHOD: ctor
		JMethod ctor = enumClass.constructor(JMod.PRIVATE);
		JVar valueParam = ctor.param(cm.INT, "value");
		ctor.body().assign(JExpr.refthis("value"), valueParam);
	}
	
	private void enumGetValue(JCodeModel cm, JDefinedClass enumClass) {
		// METHOD: getValue
		JMethod getValueMethod = enumClass.method(JMod.PUBLIC, cm.INT, "getValue");
		getValueMethod.body()._return(JExpr.refthis("value"));
	}
	
	private void enumValueOf(JCodeModel cm, JDefinedClass enumClass, Map<Entry, Integer> entries) {
		// METHOD: valueOf
		JMethod valueOfMethod = enumClass.method(JMod.PUBLIC | JMod.STATIC, enumClass, "valueOf");
		JVar valueOfParam1 = valueOfMethod.param(cm.INT, "value");
		JSwitch switch1 = valueOfMethod.body()._switch(valueOfParam1);
		for (Map.Entry<Entry, Integer> item : entries.entrySet()) {
			Entry entry = item.getKey();
			Integer value = item.getValue();
			String entryName = JavaNames.toConstantName(entry.getName());
			
			switch1._case(JExpr.lit(value))
			.body()._return(JExpr.ref(entryName));
		}
		switch1._default().body()._return(JExpr._null());
	}
	
	/* MESSAGES */
	
	public void generateMessage(com.ugcs.mavlink.xmlschema.Message message) throws JClassAlreadyExistsException, IOException {
		if (message == null)
			throw new IllegalArgumentException("Empty message");
		if (message.getName() == null || message.getName().isEmpty())
			throw new IllegalArgumentException("Empty message name");
		
		List<MavlinkField> fields = new ArrayList<MavlinkField>(message.getField().size());
		for (Field field : message.getField())
			fields.add(new MavlinkField(field));
		
		// reordering fields
		if (fieldsReordering)
			Collections.sort(fields, new MavlinkFieldComparator());
		
		/* decl */
		
		JCodeModel cm = new JCodeModel();
		JPackage pkg = cm._package(state.packageName);
		
		String classNameBase = JavaNames.toClassName(message.getName());
		String messageClassName = classNameBase;
		
		// uniqueness check
		int version = 0;
		while (true) {
			boolean unique = true;
			for (JDefinedClass definedClass : state.messageClasses.values()) {
				if (definedClass.name().toLowerCase()
						.equals(messageClassName.toLowerCase())) {
					unique = false;
					break;
				}
			}
			if (!unique) {
				++version;
				messageClassName = classNameBase + 
						Integer.toString(version);
			} else
				break;
		}
		
		JDefinedClass messageClass = pkg._class(JMod.PUBLIC | JMod.FINAL, messageClassName);
		messageClass._implements(MavlinkMessage.class);
		messageClass.javadoc().add(wrapToLines(message.getDescription()));
		
		JDefinedClass builderClass = messageClass._class(JMod.PUBLIC | JMod.STATIC, "Builder");
		builderClass._implements(MavlinkMessageBuilder.class);
		
		int messageId = message.getId();
		state.messageClasses.put(messageId, messageClass);
		state.messageBuilderClasses.put(messageId, builderClass);
		
		// message length
		int messageLength = 0;
		for (MavlinkField entry : fields)
			messageLength += entry.getTypeDescriptor().getWireLength();
		if (messageLength < 0)
			throw new IllegalArgumentException("Illegal Mavlink message length: " + messageLength);
		state.messageLengths.put(messageId, messageLength);
		
		// message crc extra byte
		StringBuilder sb = new StringBuilder();
		sb.append(message.getName());
		sb.append(" ");
		for (MavlinkField field : fields) {
			sb.append(field.getTypeDescriptor().getMavlinkElementType());
			sb.append(" ");
			sb.append(field.getField().getName());
			sb.append(" ");
			if (field.getTypeDescriptor().isArray()) {
				sb.append((char) field.getTypeDescriptor().getArrayLength());
			}
		}
		MavlinkCrc crc = new MavlinkCrc();
		for (int i = 0; i < sb.length(); ++i)
			crc.append(sb.charAt(i) & 0xff);
		int v = crc.getChecksum();
		int crcExtraByte = (v >>> 8 & 0xff) ^ (v & 0xff);
		state.messageCrcExtraBytes.put(messageId, crcExtraByte);
		
		/* builder class */

		builderFields(cm, builderClass, fields);
		builderFieldSetters(cm, builderClass, fields);
		
		builderBuild(cm, messageClass, builderClass);
		builderReadFrom(cm, builderClass, fields);
		
		/* message class */
		
		messageFields(cm, messageClass, fields);
		messageFieldGetters(cm, messageClass, fields);
		
		messageCtor(cm, messageClass, builderClass, fields);
		messageGetMavlinkMessageType(cm, messageClass, message.getId());
		messageNewBuilder(cm, messageClass, builderClass);
		messageToBuilder(cm, messageClass, builderClass, fields);
		messageWriteTo(cm, messageClass, fields);
		messageReadFrom(cm, messageClass, builderClass);
		messageToString(cm, messageClass, fields);
		
		// saving class file
		cm.build(new File(state.sourceDirectoryPath));
	}
	
	/* builder class */
	
	private void builderFields(JCodeModel cm, JDefinedClass builderClass, List<MavlinkField> fields) {
		for (MavlinkField entry : fields) {
			Field field = entry.getField();
			MavlinkTypeDescriptor typeDescriptor = entry.getTypeDescriptor();
			String fieldName = JavaNames.toFieldName(field.getName());
			JType fieldType = cm._ref(typeDescriptor.getType());

			// FIELD
			JFieldVar fieldVar = null;
			if (typeDescriptor.getType().isArray()) {
				Class<?> componentType = typeDescriptor.getType().getComponentType();
				fieldVar = builderClass.field(JMod.PRIVATE, fieldType, fieldName, 
						JExpr.newArray(cm._ref(componentType), typeDescriptor.getArrayLength()));
			} else {
				fieldVar = builderClass.field(JMod.PRIVATE, fieldType, fieldName);
			}
			for(Serializable comment : field.getContent())
				fieldVar.javadoc().add(wrapToLines(comment.toString()));
		}
	}
	
	private void builderFieldSetters(JCodeModel cm, JDefinedClass builderClass, List<MavlinkField> fields) {
		for (MavlinkField entry : fields) {
			Field field = entry.getField();
			MavlinkTypeDescriptor typeDescriptor = entry.getTypeDescriptor();
			String fieldName = JavaNames.toFieldName(field.getName());
			JType fieldType = cm._ref(typeDescriptor.getType());

			String setMethodName = JavaNames.toFieldName("set_" + field.getName());
			if (typeDescriptor.getType().isArray()) {
				Class<?> componentType = typeDescriptor.getType().getComponentType();
				
				// METHOD: set
				JMethod setMethod = builderClass.method(JMod.PUBLIC, builderClass, setMethodName);
				JVar setParam1 = setMethod.param(fieldType, fieldName);
				// null-check
				setMethod.body()
					._if(JOp.eq(setParam1, JExpr._null()))
					._then()
					._throw(JExpr._new(cm._ref(IllegalArgumentException.class))
							.arg(JExpr.lit(setParam1.name())));
				// body
				JClass systemClass = cm.ref(System.class);
				JInvocation arrayCopyInv = systemClass.staticInvoke("arraycopy");
				arrayCopyInv.arg(setParam1);
				arrayCopyInv.arg(JExpr.lit(0));
				arrayCopyInv.arg(JExpr.refthis(fieldName));
				arrayCopyInv.arg(JExpr.lit(0));
				arrayCopyInv.arg(JExpr.ref(JExpr.refthis(fieldName), "length"));
				setMethod.body().add(arrayCopyInv);
				setMethod.body()._return(JExpr._this());
				for(Serializable comment : field.getContent())
					setMethod.javadoc().add(wrapToLines(comment.toString()));
				
				// METHOD: setByIndex
				JMethod setByIndexMethod = builderClass.method(JMod.PUBLIC, builderClass, setMethodName);
				JVar setByIndexParam1 = setByIndexMethod.param(cm.INT, "index");
				JVar setByIndexParam2 = setByIndexMethod.param(cm._ref(componentType), "value");
				// range-check
				setByIndexMethod.body()
					._if(JOp.lt(setByIndexParam1, JExpr.lit(0))
							.cor(JOp.gte(setByIndexParam1, JExpr.ref(JExpr.refthis(fieldName), "length"))))
					._then()
					._throw(JExpr._new(cm._ref(IndexOutOfBoundsException.class))
							.arg(JExpr.lit(setByIndexParam1.name())));
				// body
				setByIndexMethod.body().assign(
						JExpr.component(JExpr.refthis(fieldName), setByIndexParam1), setByIndexParam2);
				setByIndexMethod.body()._return(JExpr._this());
				for(Serializable comment : field.getContent())
					setByIndexMethod.javadoc().add(wrapToLines(comment.toString()));
			} else {
				// METHOD: set
				JMethod setMethod = builderClass.method(JMod.PUBLIC, builderClass, setMethodName);
				JVar setParam1 = setMethod.param(fieldType, fieldName);
				setMethod.body().assign(JExpr.refthis(fieldName), setParam1);
				setMethod.body()._return(JExpr._this());
				for(Serializable comment : field.getContent())
					setMethod.javadoc().add(wrapToLines(comment.toString()));
			}
		}
	}
	
	private void builderBuild(JCodeModel cm, JDefinedClass messageClass, JDefinedClass builderClass) {
		// METHOD: build
		JMethod buildMethod = builderClass.method(JMod.PUBLIC, messageClass, "build");
		buildMethod.body()._return(JExpr._new(messageClass).arg(JExpr._this()));
	}
	
	private void builderReadFrom(JCodeModel cm, JDefinedClass builderClass, List<MavlinkField> fields) {
		// METHOD: readFrom
		JMethod readFromMethod = builderClass.method(JMod.PUBLIC, builderClass, "readFrom");
		readFromMethod._throws(IOException.class);
		JVar readFromParam1 = readFromMethod.param(InputStream.class, "in");
		// null-check
		readFromMethod.body()._if(JOp.eq(readFromParam1, JExpr._null()))
			._then()
			._throw(JExpr._new(cm._ref(IllegalArgumentException.class))
					.arg(JExpr.lit(readFromParam1.name())));
		// inf
		JVar infVar = readFromMethod.body().decl(cm._ref(CodedInputStream.class), "inf", 
				JExpr._new(cm._ref(CodedInputStream.class)).arg(readFromParam1).arg(JExpr.lit(littleEndian)));
		infVar.annotate(SuppressWarnings.class).param("value", "resource");
		// reads
		for (MavlinkField item : fields) {
			Field field = item.getField();
			MavlinkTypeDescriptor typeDescriptor = item.getTypeDescriptor();
			String fieldName = JavaNames.toFieldName(field.getName());
			
			if (typeDescriptor.getType().isArray()) {
				JForLoop loop = readFromMethod.body()._for();
				JVar indexVar = loop.init(cm.INT, "i", JExpr.lit(0));
				loop.test(JOp.lt(indexVar, JExpr.lit(typeDescriptor.getArrayLength())));
				loop.update(JOp.incr(indexVar));
				JInvocation readInv = JExpr.invoke(infVar, getReadMethodName(typeDescriptor.getMavlinkElementType()));
				loop.body().assign(JExpr.component(JExpr.refthis(fieldName), indexVar), readInv);
			} else {
				JInvocation readInv = JExpr.invoke(infVar, getReadMethodName(typeDescriptor.getMavlinkElementType()));
				readFromMethod.body().assign(JExpr.refthis(fieldName), readInv);
			}
		}
		readFromMethod.body()._return(JExpr._this());
	}
	
	/* message class */
	
	private void messageFields(JCodeModel cm, JDefinedClass messageClass, List<MavlinkField> fields) {
		for (MavlinkField item : fields) {
			Field field = item.getField();
			MavlinkTypeDescriptor typeDescriptor = item.getTypeDescriptor();
			String fieldName = JavaNames.toFieldName(field.getName());
			JType fieldType = cm._ref(typeDescriptor.getType());
			
			// FIELD
			JFieldVar fieldVar = null;
			if (typeDescriptor.getType().isArray()) {
				Class<?> componentType = typeDescriptor.getType().getComponentType();
				fieldVar = messageClass.field(JMod.PRIVATE, fieldType, fieldName, 
						JExpr.newArray(cm._ref(componentType), typeDescriptor.getArrayLength()));
			} else {
				fieldVar = messageClass.field(JMod.PRIVATE, fieldType, fieldName);
			}
			for(Serializable comment : field.getContent())
				fieldVar.javadoc().add(wrapToLines(comment.toString()));
		}
	}
	
	private void messageFieldGetters(JCodeModel cm, JDefinedClass messageClass, List<MavlinkField> fields) {
		for (MavlinkField item : fields) {
			Field field = item.getField();
			MavlinkTypeDescriptor typeDescriptor = item.getTypeDescriptor();
			String fieldName = JavaNames.toFieldName(field.getName());
			JType fieldType = cm._ref(typeDescriptor.getType());
			
			// METHOD: get
			String getMethodName = JavaNames.toFieldName("get_" + field.getName());
			if (typeDescriptor.getType().isArray()) {
				// defensive copy may be a performance issue, but it is safe
				JMethod getMethod = messageClass.method(JMod.PUBLIC, fieldType, getMethodName);
				JClass arraysClass = cm.ref(Arrays.class);
				JInvocation copyOfInv = arraysClass.staticInvoke("copyOf");
				copyOfInv.arg(JExpr.refthis(fieldName));
				copyOfInv.arg(JExpr.ref(JExpr.refthis(fieldName), "length"));
				String copyName = JavaNames.toFieldName(fieldName + "_copy");
				JVar copyVar = getMethod.body().decl(fieldType, copyName, copyOfInv);
				getMethod.body()._return(copyVar);
				for(Serializable comment : field.getContent())
					getMethod.javadoc().add(wrapToLines(comment.toString()));
				// METHOD: getString
				if (typeDescriptor.getType().equals(char[].class)) {
					String getStringMethodName = JavaNames.toFieldName(getMethodName + "_string");
					JMethod getStringMethod = messageClass.method(JMod.PUBLIC, cm._ref(String.class), getStringMethodName);
					JVar iVar = getStringMethod.body().decl(cm.INT, JavaNames.toFieldName("i"), JExpr.lit(0));
					JForLoop loop = getStringMethod.body()._for();
					loop.test(JOp.lt(iVar, JExpr.lit(typeDescriptor.getArrayLength())));
					loop.update(JOp.incr(iVar));
					loop.body()._if(JOp.eq(JExpr.component(JExpr.refthis(fieldName), iVar), JExpr.lit(0)))
						._then()._break();
					getStringMethod.body()._return(JExpr._new(cm._ref(String.class))
							.arg(JExpr.refthis(fieldName))
							.arg(JExpr.lit(0))
							.arg(iVar));
				}
			} else {
				JMethod getMethod = messageClass.method(JMod.PUBLIC, fieldType, getMethodName);
				getMethod.body()._return(JExpr.refthis(fieldName));
				for(Serializable comment : field.getContent())
					getMethod.javadoc().add(wrapToLines(comment.toString()));
			}
		}
	}
	
	private void messageCtor(JCodeModel cm, JDefinedClass messageClass, JDefinedClass builderClass, List<MavlinkField> fields) {
		// METHOD: ctor
		JMethod ctor = messageClass.constructor(JMod.PRIVATE);
		JVar ctorParam1 = ctor.param(builderClass, "builder");
		// null-check
		ctor.body()._if(JOp.eq(ctorParam1, JExpr._null()))
			._then()
			._throw(JExpr._new(cm._ref(IllegalArgumentException.class))
					.arg(JExpr.lit(ctorParam1.name())));
		// body
		for (MavlinkField item : fields) {
			Field field = item.getField();
			MavlinkTypeDescriptor typeDescriptor = item.getTypeDescriptor();
			String fieldName = JavaNames.toFieldName(field.getName());
			if (typeDescriptor.getType().isArray()) {
				JClass systemClass = cm.ref(System.class);
				JInvocation arrayCopyInv = systemClass.staticInvoke("arraycopy");
				arrayCopyInv.arg(JExpr.ref(ctorParam1, fieldName));
				arrayCopyInv.arg(JExpr.lit(0));
				arrayCopyInv.arg(JExpr.refthis(fieldName));
				arrayCopyInv.arg(JExpr.lit(0));
				arrayCopyInv.arg(JExpr.ref(JExpr.refthis(fieldName), "length"));
				ctor.body().add(arrayCopyInv);
			} else {
				ctor.body().assign(JExpr.refthis(fieldName), JExpr.ref(ctorParam1, fieldName));
			}
		}
	}

	private void messageGetMavlinkMessageType(JCodeModel cm, JDefinedClass messageClass, int messageType) {
		if (messageType < 0)
			throw new IllegalArgumentException("Illegal Mavlink message type: " + messageType);
		
		// METHOD: getMavlinkMessageType
		JMethod getMavlinkMessageTypeMethod = messageClass.method(JMod.PUBLIC, cm.INT, "getMavlinkMessageType");
		getMavlinkMessageTypeMethod.body()._return(JExpr.lit(messageType));
	}
	
	private void messageNewBuilder(JCodeModel cm, JDefinedClass messageClass, JDefinedClass builderClass) {
		// METHOD: newBuilder
		JMethod newBuilderMethod = messageClass.method(JMod.PUBLIC | JMod.STATIC, builderClass, "newBuilder");
		newBuilderMethod.body()._return(JExpr._new(builderClass));
	}
	
	private void messageToBuilder(JCodeModel cm, JDefinedClass messageClass, JDefinedClass builderClass, List<MavlinkField> fields) {
		// METHOD: toBuilder
		JMethod toBuilderMethod = messageClass.method(JMod.PUBLIC, builderClass, "toBuilder");
		// body
		JVar builderVar = toBuilderMethod.body().decl(builderClass, "builder", JExpr._new(builderClass));
		for (MavlinkField item : fields) {
			Field field = item.getField();
			MavlinkTypeDescriptor typeDescriptor = item.getTypeDescriptor();
			String fieldName = JavaNames.toFieldName(field.getName());
			if (typeDescriptor.getType().isArray()) {
				JClass systemClass = cm.ref(System.class);
				JInvocation arrayCopyInv = systemClass.staticInvoke("arraycopy");
				arrayCopyInv.arg(JExpr.refthis(fieldName));
				arrayCopyInv.arg(JExpr.lit(0));
				arrayCopyInv.arg(JExpr.ref(builderVar, fieldName));
				arrayCopyInv.arg(JExpr.lit(0));
				arrayCopyInv.arg(JExpr.ref(JExpr.ref(builderVar, fieldName), "length"));
				toBuilderMethod.body().add(arrayCopyInv);
			} else {
				toBuilderMethod.body().assign(JExpr.ref(builderVar, fieldName), JExpr.refthis(fieldName));
			}
		}
		toBuilderMethod.body()._return(builderVar);
	}
	
	private void messageWriteTo(JCodeModel cm, JDefinedClass messageClass, List<MavlinkField> fields) {
		// METHOD: writeTo
		JMethod writeToMethod = messageClass.method(JMod.PUBLIC, cm.VOID, "writeTo");
		writeToMethod._throws(IOException.class);
		JVar writeToParam1 = writeToMethod.param(OutputStream.class, "out");
		// null-check
		writeToMethod.body()._if(JOp.eq(writeToParam1, JExpr._null()))
			._then()
			._throw(JExpr._new(cm._ref(IllegalArgumentException.class))
					.arg(JExpr.lit(writeToParam1.name())));
		// outf
		JVar outfVar = writeToMethod.body().decl(cm._ref(CodedOutputStream.class), "outf", 
				JExpr._new(cm._ref(CodedOutputStream.class)).arg(writeToParam1).arg(JExpr.lit(littleEndian)));
		outfVar.annotate(SuppressWarnings.class).param("value", "resource");
		// writes
		for (MavlinkField item : fields) {
			Field field = item.getField();
			MavlinkTypeDescriptor typeDescriptor = item.getTypeDescriptor();
			String fieldName = JavaNames.toFieldName(field.getName());
			
			if (typeDescriptor.getType().isArray()) {
				JForLoop loop = writeToMethod.body()._for();
				JVar indexVar = loop.init(cm.INT, "i", JExpr.lit(0));
				loop.test(JOp.lt(indexVar, JExpr.lit(typeDescriptor.getArrayLength())));
				loop.update(JOp.incr(indexVar));
				loop.body().invoke(outfVar, getWriteMethodName(typeDescriptor.getMavlinkElementType()))
					.arg(JExpr.component(JExpr.refthis(fieldName), indexVar));
			} else {
				String writeMethodName = getWriteMethodName(typeDescriptor.getMavlinkElementType());
				JInvocation writeInv = writeToMethod.body().invoke(outfVar, writeMethodName);
				writeInv.arg(JExpr.refthis(fieldName));
			}
		}
	}
	
	private void messageReadFrom(JCodeModel cm, JDefinedClass messageClass, JDefinedClass builderClass) {
		JMethod readFromMethod = messageClass.method(JMod.PUBLIC | JMod.STATIC, messageClass, "readFrom");
		readFromMethod._throws(IOException.class);
		JVar readFromParam1 = readFromMethod.param(InputStream.class, "in");
		// null-check
		readFromMethod.body()._if(JOp.eq(readFromParam1, JExpr._null()))
			._then()
			._throw(JExpr._new(cm._ref(IllegalArgumentException.class))
					.arg(JExpr.lit(readFromParam1.name())));
		readFromMethod.body()._return(JExpr.invoke(
				JExpr.invoke(JExpr._new(builderClass), "readFrom").arg(readFromParam1), "build"));
	}

	private void messageToString(JCodeModel cm, JDefinedClass messageClass, List<MavlinkField> fields) {
		// METHOD: toString
		JMethod toStringMethod = messageClass.method(JMod.PUBLIC, cm._ref(String.class), "toString");
		toStringMethod.annotate(Override.class);
		
		// nl
		JClass systemClass = cm.ref(System.class);
		JInvocation getPropertyInv = systemClass.staticInvoke("getProperty");
		getPropertyInv.arg("line.separator");
		JVar nlVar = toStringMethod.body().decl(cm._ref(String.class), "nl", getPropertyInv);
		
		// sb
		JVar sbVar = toStringMethod.body().decl(cm._ref(StringBuilder.class), "sb", 
				JExpr._new(cm._ref(StringBuilder.class)));

		// fields
		toStringMethod.body().invoke(sbVar, "append").arg(JExpr.lit(messageClass.name() + " {"));
		toStringMethod.body().invoke(sbVar, "append").arg(nlVar);

		for (MavlinkField item : fields) {
			MavlinkTypeDescriptor typeDescriptor = item.getTypeDescriptor();
			Field field = item.getField();
			String fieldName = JavaNames.toFieldName(field.getName());
			
			toStringMethod.body().invoke(sbVar, "append").arg(JExpr.lit("\t" + field.getName() + ": "));
			if (typeDescriptor.getType().isArray()) {
				JClass arraysClass = cm.ref(Arrays.class);
				JInvocation arrayToStringInv = arraysClass.staticInvoke("toString");
				arrayToStringInv.arg(JExpr.refthis(fieldName));	
				toStringMethod.body().invoke(sbVar, "append").arg(arrayToStringInv);
			} else {
				toStringMethod.body().invoke(sbVar, "append").arg(JExpr.refthis(fieldName));
			}
			toStringMethod.body().invoke(sbVar, "append").arg(nlVar);
		}
		
		toStringMethod.body().invoke(sbVar, "append").arg("}");
		toStringMethod.body()._return(JExpr.invoke(sbVar, "toString"));
	}

	/* descriptor */

	public void generateMavlinkDescriptor() throws JClassAlreadyExistsException, IOException {
		
		/* decl */
		
		JCodeModel cm = new JCodeModel();
		JPackage pkg = cm._package(state.packageName);
		
		JDefinedClass descriptorClass = pkg._class(JMod.PUBLIC | JMod.FINAL, "MavlinkDescriptor");
		descriptorClass._implements(ProtocolDescriptor.class);
		descriptorClass.javadoc().add("Mavlink protocol descriptor");
	
		/* message factory class */
		
		descriptorFields(cm, descriptorClass);
		descriptorGetters(cm, descriptorClass);
		
		descriptorCtor(cm, descriptorClass);
		descriptorGetMessageLength(cm, descriptorClass);
		descriptorGetMessageCrcExtraByte(cm, descriptorClass);
		descriptorNewMessageBuilder(cm, descriptorClass);
		
		// saving class file
		cm.build(new File(state.sourceDirectoryPath));
	}
	
	private void descriptorFields(JCodeModel cm, JDefinedClass descriptorClass) {
		// FIELD: messageLength
		descriptorClass.field(JMod.PRIVATE | JMod.FINAL, cm._ref(int[].class), "messageLengths", 
				JExpr.newArray(cm.INT, JExpr.lit(256)));
		// FIELD: messageCrcExtraBytes
		descriptorClass.field(JMod.PRIVATE | JMod.FINAL, cm._ref(int[].class), "messageCrcExtraBytes", 
				JExpr.newArray(cm.INT, JExpr.lit(256)));
	}
	
	private void descriptorGetters(JCodeModel cm, JDefinedClass descriptorClass) {
		// METHOD: getMavlinkVersion
		descriptorClass.method(JMod.PUBLIC, String.class, "getMavlinkVersion")
			.body()._return(JExpr.lit(mavlinkVersion));
		// METHOD: getMavlinkStx
		descriptorClass.method(JMod.PUBLIC, cm.INT, "getMavlinkStx")
			.body()._return(JExpr.lit(mavlinkStx));
		// METHOD: isCrcExtraByte
		descriptorClass.method(JMod.PUBLIC, cm.BOOLEAN, "isCrcExtraByte")
			.body()._return(JExpr.lit(crcExtraByte));
		// METHOD: isFieldsReordering
		descriptorClass.method(JMod.PUBLIC, cm.BOOLEAN, "isFieldsReordering")
			.body()._return(JExpr.lit(fieldsReordering));
		// METHOD: isLittleEndian
		descriptorClass.method(JMod.PUBLIC, cm.BOOLEAN, "isLittleEndian")
			.body()._return(JExpr.lit(littleEndian));
		// METHOD: isExpandedSystemId
		descriptorClass.method(JMod.PUBLIC, cm.BOOLEAN, "isExpandedSystemId")
			.body()._return(JExpr.lit(expandedSystemId));
	}
	
	private void descriptorCtor(JCodeModel cm, JDefinedClass descriptorClass) {
		// METHOD: ctor
		JMethod ctorMethod = descriptorClass.constructor(JMod.PUBLIC);
		
		// it seems CodeModel 2.5 doesn't support array initializers
		// so values are filled by hand
		for (Map.Entry<Integer, Integer> item : state.messageLengths.entrySet()) {
			ctorMethod.body().assign(
					JExpr.component(JExpr.refthis("messageLengths"), JExpr.lit(item.getKey())), 
					JExpr.lit(item.getValue()));
		}
		for (Map.Entry<Integer, Integer> item : state.messageCrcExtraBytes.entrySet()) {
			ctorMethod.body().assign(
					JExpr.component(JExpr.refthis("messageCrcExtraBytes"), JExpr.lit(item.getKey())), 
					JExpr.lit(item.getValue()));
		}
	}
	
	private void descriptorNewMessageBuilder(JCodeModel cm, JDefinedClass descriptorClass) {
		// METHOD: newMessageBuilder
		JMethod newMessageBuilderMethod = descriptorClass.method(JMod.PUBLIC, MavlinkMessageBuilder.class, "newMessageBuilder");
		JVar newMessageBuilderParam1 = newMessageBuilderMethod.param(cm.INT, "messageType");
		JSwitch switch1 = newMessageBuilderMethod.body()._switch(newMessageBuilderParam1);
		for (Map.Entry<Integer, JDefinedClass> item : state.messageBuilderClasses.entrySet()) {
			Integer messageType = item.getKey();
			JDefinedClass builderClass = item.getValue();
			
			switch1._case(JExpr.lit(messageType))
				.body()._return(JExpr._new(builderClass));
		}
		switch1._default().body().
			_throw(JExpr._new(cm._ref(IllegalArgumentException.class))
				.arg(JExpr.lit("Unsupported message type")));
	}
	
	private void descriptorGetMessageLength(JCodeModel cm, JDefinedClass descriptorClass) {
		// METHOD: getMessageLength
		JMethod getMessageLengthMethod = descriptorClass.method(JMod.PUBLIC, cm.INT, "getMessageLength");
		JVar getMessageLengthParam1 = getMessageLengthMethod.param(cm.INT, "messageType");

		getMessageLengthMethod.body()
			._return(JExpr.component(JExpr.refthis("messageLengths"), getMessageLengthParam1));
	}
	
	private void descriptorGetMessageCrcExtraByte(JCodeModel cm, JDefinedClass descriptorClass) {
		// METHOD: getMessageCrcExtraByte
		JMethod getMessageCrcExtraByte = descriptorClass.method(JMod.PUBLIC, cm.INT, "getMessageCrcExtraByte");
		JVar getMessageCrcExtraByteParam1 = getMessageCrcExtraByte.param(cm.INT, "messageType");

		getMessageCrcExtraByte.body()
			._return(JExpr.component(JExpr.refthis("messageCrcExtraBytes"), getMessageCrcExtraByteParam1));
	}
	
	private String getWriteMethodName(String mavlinkElemntType) {
		if (mavlinkElemntType == null)
			throw new IllegalArgumentException("mavlinkElemntType");
		
		if (mavlinkElemntType.equals("float"))
			return "writeFloat";
		if (mavlinkElemntType.equals("double"))
			return "writeDouble";
		if (mavlinkElemntType.equals("char"))
			return "writeChar";
		if (mavlinkElemntType.equals("int8_t"))
			return "writeInt8";
		if (mavlinkElemntType.equals("uint8_t"))
			return "writeUnsignedInt8";
		if (mavlinkElemntType.equals("int16_t"))
			return "writeInt16";
		if (mavlinkElemntType.equals("uint16_t"))
			return "writeUnsignedInt16";
		if (mavlinkElemntType.equals("int32_t"))
			return "writeInt32";
		if (mavlinkElemntType.equals("uint32_t"))
			return "writeUnsignedInt32";
		if (mavlinkElemntType.equals("int64_t"))
			return "writeInt64";
		if (mavlinkElemntType.equals("uint64_t"))
			return "writeUnsignedInt64";
		
		throw new IllegalArgumentException("Unsupported type: " + mavlinkElemntType);
	}
	
	private String getReadMethodName(String mavlinkElemntType) {
		if (mavlinkElemntType == null)
			throw new IllegalArgumentException("mavlinkElemntType");
		
		if (mavlinkElemntType.equals("float"))
			return "readFloat";
		if (mavlinkElemntType.equals("double"))
			return "readDouble";
		if (mavlinkElemntType.equals("char"))
			return "readChar";
		if (mavlinkElemntType.equals("int8_t"))
			return "readInt8";
		if (mavlinkElemntType.equals("uint8_t"))
			return "readUnsignedInt8";
		if (mavlinkElemntType.equals("int16_t"))
			return "readInt16";
		if (mavlinkElemntType.equals("uint16_t"))
			return "readUnsignedInt16";
		if (mavlinkElemntType.equals("int32_t"))
			return "readInt32";
		if (mavlinkElemntType.equals("uint32_t"))
			return "readUnsignedInt32";
		if (mavlinkElemntType.equals("int64_t"))
			return "readInt64";
		if (mavlinkElemntType.equals("uint64_t"))
			return "readUnsignedInt64";
		
		throw new IllegalArgumentException("Unsupported type: " + mavlinkElemntType);
	}
	
	public static String wrapToLines(String str) {
		if (str == null)
			return null;

		int lineLength = 64;
		String lineSeparator = "\n";
		
		StringBuilder sb = new StringBuilder(str.length());
		// keep original line breaks untouched
		String[] lines = str.split("\r?\n|\r");
		for (String line : lines) {
			int start = 0;
			for (int k = 0; k < line.length(); ++k) {
				if (line.charAt(k) == ' ') {
					// spaces at the start are omitted
					if (start == k) {
						start++;
						continue;
					}
					if (k - start + 1 >= lineLength) {
						String substr = line.substring(start, k).trim();
						if (substr.length() > 0) {
							if (sb.length() > 0)
								sb.append(lineSeparator);
							sb.append(substr);
						}
						start = k + 1;
					}
				}
			}
			// the last line (with the possible spaces at the end)
			String substr = line.substring(start).trim();
			if (substr.length() > 0) {
				if (sb.length() > 0)
					sb.append(lineSeparator);
				sb.append(substr);
			}
		}
		return sb.toString();
	}
}
