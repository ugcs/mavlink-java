package com.ugcs.mavlink;

public class MavlinkPacket {
	private int payloadLength;
	private int sequenceNumber;
	private long systemId;
	private int componentId;
	private int messageType;
	private MavlinkMessage payload;
	
	public MavlinkPacket() {
	}
	
	public MavlinkPacket(long systemId, int componentId, MavlinkMessage payload) {
		this.systemId = systemId;
		this.componentId = componentId;
		this.payload = payload;
		if (payload != null)
			messageType = payload.getMavlinkMessageType();
	}
	
	public int getPayloadLength() {
		return payloadLength;
	}
	
	public void setPayloadLength(int payloadLength) {
		this.payloadLength = payloadLength;
	}
	
	public int getSequenceNumber() {
		return sequenceNumber;
	}
	
	public void setSequenceNumber(int sequenceNumber) {
		this.sequenceNumber = sequenceNumber;
	}
	
	public long getSystemId() {
		return systemId;
	}
	
	public void setSystemId(long systemId) {
		this.systemId = systemId;
	}
	
	public int getComponentId() {
		return componentId;
	}
	
	public void setComponentId(int componentId) {
		this.componentId = componentId;
	}
	
	public int getMessageType() {
		return messageType;
	}
	
	public void setMessageType(int messageType) {
		this.messageType = messageType;
	}
	
	public MavlinkMessage getPayload() {
		return payload;
	}
	
	public void setPayload(MavlinkMessage payload) {
		this.payload = payload;
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("<MavlinkPacket> [[");
		sb.append(payload != null ? payload.getClass().getSimpleName() : "UNKNOWN");
		sb.append(", payload length: ");
		sb.append(Integer.toString(payloadLength));
		sb.append(", sequence number: ");
		sb.append(Integer.toString(sequenceNumber));
		sb.append(", system id: ");
		sb.append(Long.toString(systemId));
		sb.append(", component id: ");
		sb.append(Integer.toString(componentId));
		sb.append(", message type: ");
		sb.append(Integer.toString(messageType));
		sb.append(", payload: ");
		sb.append(payload != null ? payload.toString() : "NONE");
		sb.append("]]");
		return sb.toString();
	}
}
