package com.ugcs.mavlink.generator;

import java.util.Comparator;

public class MavlinkFieldComparator implements Comparator<MavlinkField>{
	public int compare(MavlinkField field1, MavlinkField field2) {
		if (field1.equals(field2))
			return 0;

		int length1 = field1.getTypeDescriptor().getLength();
		int length2 = field2.getTypeDescriptor().getLength();
		if (length1 > length2)
			return -1;
		if (length1 < length2)
			return 1;
		return 0;
	}
}
