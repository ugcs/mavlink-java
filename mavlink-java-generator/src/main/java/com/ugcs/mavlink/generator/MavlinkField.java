package com.ugcs.mavlink.generator;

import com.ugcs.mavlink.xmlschema.Field;

public class MavlinkField {
	private final Field field;
	private final MavlinkTypeDescriptor typeDescriptor;
	
	public MavlinkField(Field field) {
		if (field == null)
			throw new IllegalArgumentException("field");
		
		this.field = field;
		typeDescriptor = MavlinkTypeDescriptor.of(field.getType());
	}
	
	public Field getField() {
		return field;
	}

	public MavlinkTypeDescriptor getTypeDescriptor() {
		return typeDescriptor;
	}
}
