package com.ugcs.mavlink;

import java.io.IOException;
import java.io.OutputStream;

public class MavlinkEncoder {
	private final ProtocolDescriptor protocol;
	private final int headerLength;
	private final int checksumLength;

	public MavlinkEncoder(ProtocolDescriptor protocol) {
		if (protocol == null)
			throw new IllegalArgumentException("protocol");
		
		this.protocol = protocol;
		this.headerLength = protocol.isExpandedSystemId() ? 9 : 6;
		this.checksumLength = 2;
	}
	
	public byte[] encode(MavlinkPacket packet) throws IOException {
		if (packet == null)
			throw new IllegalArgumentException("packet");
		MavlinkMessage payload = packet.getPayload();
		if (payload == null)
			throw new IllegalArgumentException("Packet payload not set");
		
		int messageType = payload.getMavlinkMessageType();
		int payloadLength = protocol.getMessageLength(messageType);
		
		// result length is payload length + extra bytes 
		// for the packet header and checksum
		int bufferLength = headerLength + payloadLength + checksumLength;
		byte[] buffer = new byte[bufferLength];
		ByteArrayOutputStream out = new ByteArrayOutputStream(buffer);
		
		// packet header
		out.write(protocol.getMavlinkStx());
		out.write(payloadLength);
		out.write(packet.getSequenceNumber());
		if (protocol.isExpandedSystemId())
			writeUnsignedInt32(out, packet.getSystemId());
		else
			out.write((int) packet.getSystemId());
		out.write(packet.getComponentId());
		out.write(messageType);
		// payload data
		// TODO is serialized message length is exactly same
		// as stated by the getMessageLength()?
		if (payloadLength > 0)
			payload.writeTo(out);
		// checksum
		MavlinkCrc rc = new MavlinkCrc();
		rc.append(buffer, 1, buffer.length - (checksumLength + 1));
		if (protocol.isCrcExtraByte())
			rc.append(protocol.getMessageCrcExtraByte(messageType));
		int checksum = rc.getChecksum();
		out.write(checksum);
		out.write(checksum >>> 8);
		
		return buffer;
	}
	
	private void writeUnsignedInt32(OutputStream out, long value) throws IOException {
		if (out == null)
			throw new IllegalArgumentException("out");
		if ((value >>> 32) != 0)
			throw new IllegalArgumentException("Value can't be narrowed without data loss: " + value);
		
		int nrrowed = (int) value;
		if (protocol.isLittleEndian()) {
			out.write(nrrowed & 0xff);
			out.write((nrrowed >>> 8) & 0xff);
			out.write((nrrowed >>> 16) & 0xff);
			out.write((nrrowed >>> 24) & 0xff);
		} else {
			out.write((nrrowed >>> 24) & 0xff);
			out.write((nrrowed >>> 16) & 0xff);
			out.write((nrrowed >>> 8) & 0xff);
			out.write(nrrowed & 0xff);
		}
	}
}
