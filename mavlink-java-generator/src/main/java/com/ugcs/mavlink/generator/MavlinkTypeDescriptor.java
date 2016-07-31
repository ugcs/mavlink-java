package com.ugcs.mavlink.generator;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MavlinkTypeDescriptor {
	private final Class<?> type;
	private final String mavlinkType;
	private final String mavlinkElementType;
	private final boolean isArray;
	private final int arrayLength;
	private final int length;
	private final int wireLength;

	private static final Set<String> MAVLINK_TYPES = newMavlinkTypes();
	private static final Map<String, Integer> MAVLINK_TYPE_LENGTHS = newMavlinkTypeLengths();
	private static final Map<String, Class<?>> MAVLINK_JAVA_CLASSES = newMavlinkJavaClasses();
	private static final Map<String, Class<?>> MAVLINK_JAVA_ARRAY_CLASSES = newMavlinkJavaArrayClasses();

	/* static init */

	private static Set<String> newMavlinkTypes() {
		Set<String> mavlinkTypes = new HashSet<String>();
		mavlinkTypes.add("float");
		mavlinkTypes.add("double");
		mavlinkTypes.add("char");
		mavlinkTypes.add("int8_t");
		mavlinkTypes.add("uint8_t");
		//mavlinkTypes.add("uint8_t_mavlink_version");
		mavlinkTypes.add("int16_t");
		mavlinkTypes.add("uint16_t");
		mavlinkTypes.add("int32_t");
		mavlinkTypes.add("uint32_t");
		mavlinkTypes.add("int64_t");
		mavlinkTypes.add("uint64_t");
		//mavlinkTypes.add("array");
		return Collections.unmodifiableSet(mavlinkTypes);
	}

	private static Map<String, Integer> newMavlinkTypeLengths() {
		Map<String, Integer> mavlinkTypeLengths = new HashMap<String, Integer>();
		mavlinkTypeLengths.put("float", 4);
		mavlinkTypeLengths.put("double", 8);
		mavlinkTypeLengths.put("char", 1);
		mavlinkTypeLengths.put("int8_t", 1);
		mavlinkTypeLengths.put("uint8_t", 1);
		//mavlinkTypeLengths.put("uint8_t_mavlink_version", 1);
		mavlinkTypeLengths.put("int16_t", 2);
		mavlinkTypeLengths.put("uint16_t", 2);
		mavlinkTypeLengths.put("int32_t", 4);
		mavlinkTypeLengths.put("uint32_t", 4);
		mavlinkTypeLengths.put("int64_t", 8);
		mavlinkTypeLengths.put("uint64_t", 8);
		//mavlinkTypeLengths.put("array", 1);
		return Collections.unmodifiableMap(mavlinkTypeLengths);
	}

	private static Map<String, Class<?>> newMavlinkJavaClasses() {
		Map<String, Class<?>> mavlinkJavaClasses = new HashMap<String, Class<?>>();
		mavlinkJavaClasses.put("float", float.class);
		mavlinkJavaClasses.put("double", double.class);
		mavlinkJavaClasses.put("char", char.class);
		mavlinkJavaClasses.put("int8_t", byte.class);
		mavlinkJavaClasses.put("uint8_t", int.class);
		//mavlinkJavaClasses.put("uint8_t_mavlink_version", short.class);
		mavlinkJavaClasses.put("int16_t", short.class);
		mavlinkJavaClasses.put("uint16_t", int.class);
		mavlinkJavaClasses.put("int32_t", int.class);
		mavlinkJavaClasses.put("uint32_t", long.class);
		mavlinkJavaClasses.put("int64_t", long.class);
		mavlinkJavaClasses.put("uint64_t", long.class); // for CDC compatibility
		// mavlinkJavaClasses.put("array", byte.class);
		return Collections.unmodifiableMap(mavlinkJavaClasses);
	}

	private static Map<String, Class<?>> newMavlinkJavaArrayClasses() {
		Map<String, Class<?>> mavlinkJavaArrayClasses = new HashMap<String, Class<?>>();
		mavlinkJavaArrayClasses.put("float", float[].class);
		mavlinkJavaArrayClasses.put("double", double[].class);
		mavlinkJavaArrayClasses.put("char", char[].class);
		mavlinkJavaArrayClasses.put("int8_t", byte[].class);
		mavlinkJavaArrayClasses.put("uint8_t", int[].class);
		// mavlinkJavaArrayClasses.put("uint8_t_mavlink_version", short[].class);
		mavlinkJavaArrayClasses.put("int16_t", short[].class);
		mavlinkJavaArrayClasses.put("uint16_t", int[].class);
		mavlinkJavaArrayClasses.put("int32_t", int[].class);
		mavlinkJavaArrayClasses.put("uint32_t", long[].class);
		mavlinkJavaArrayClasses.put("int64_t", long[].class);
		mavlinkJavaArrayClasses.put("uint64_t", long[].class); // for CDC compatibility
		//mavlinkJavaArrayClasses.put("array", byte[].class);
		return Collections.unmodifiableMap(mavlinkJavaArrayClasses);
	}

	/* static init (end) */

	private MavlinkTypeDescriptor(String mavlinkType) {
		if (mavlinkType == null)
			throw new IllegalArgumentException("mavlinkType");

		this.mavlinkType = mavlinkType;

		// array
		int k = mavlinkType.indexOf('[');
		isArray = k != -1;
		if (isArray) {
			int k2 = mavlinkType.indexOf(']', k);
			if (k2 == -1)
				throw new IllegalArgumentException("Malformed type declaration " + mavlinkType);
			arrayLength = Integer.parseInt(mavlinkType.substring(k + 1, k2));
		} else {
			arrayLength = 1;
		}

		// mavlink element type
		String elementType = mavlinkType;
		if (isArray) {
			elementType = mavlinkType.substring(0, k);
		}
		if (elementType.equals("uint8_t_mavlink_version")) {
			// TODO read only flag
			elementType = "uint8_t";
		}
		if (elementType.equals("array")) {
			if (!isArray)
				throw new IllegalArgumentException("Scalar fields of the 'array' type are illegal");
			elementType = "int8_t";
		}
		mavlinkElementType = elementType;

		// java element type
		if (!MAVLINK_TYPES.contains(mavlinkElementType))
			throw new IllegalArgumentException("Unknown type: " + mavlinkElementType);
		type = isArray
				? MAVLINK_JAVA_ARRAY_CLASSES.get(mavlinkElementType)
				: MAVLINK_JAVA_CLASSES.get(mavlinkElementType);
		if (type == null)
			throw new IllegalArgumentException("No Java equivalent for type: " + mavlinkElementType);

		// sizes
		length = MAVLINK_TYPE_LENGTHS.get(mavlinkElementType);
		wireLength = isArray
				? length * arrayLength
				: length;
	}

	public static MavlinkTypeDescriptor of(String mavlinkType) {
		return new MavlinkTypeDescriptor(mavlinkType);
	}

	public Class<?> getType() {
		return type;
	}

	public String getMavlinkType() {
		return mavlinkType;
	}

	public String getMavlinkElementType() {
		return mavlinkElementType;
	}

	public boolean isArray() {
		return isArray;
	}

	public int getArrayLength() {
		return arrayLength;
	}

	public int getLength() {
		return length;
	}

	public int getWireLength() {
		return wireLength;
	}
}