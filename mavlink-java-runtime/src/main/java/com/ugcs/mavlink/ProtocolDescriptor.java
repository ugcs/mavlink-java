package com.ugcs.mavlink;

public interface ProtocolDescriptor {
	String getMavlinkVersion();
	int getMavlinkStx();

	boolean isCrcExtraByte();
	boolean isFieldsReordering();
	boolean isLittleEndian();
	boolean isExpandedSystemId();

	int getMessageLength(int messageType);
	int getMessageCrcExtraByte(int messageType);
	MavlinkMessageBuilder newMessageBuilder(int messageType);
}
