package com.ugcs.mavlink;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MavlinkDecoder {
	private final ProtocolDescriptor protocol;
	private final int headerLength;
	private final int checksumLength;
	
	/* decoder state */
	
	private byte[] packetBuffer = new byte[Mavlink.MAX_PACKET_LENGTH];
	private int packetOffset;
	private int packetLength;
	
	private boolean packetStarted;
	private boolean packetComplete;
	
	/* decoder stats */
	
	private long bytesReceived;
	private long bytesDropped;
	private long packetsReceived;
	private long packetsDropped;
	
	public MavlinkDecoder(ProtocolDescriptor protocol) {
		if (protocol == null)
			throw new IllegalArgumentException("protocol");
		
		this.protocol = protocol;
		this.headerLength = protocol.isExpandedSystemId() ? 9 : 6;
		this.checksumLength = 2;
	}
	
	public long getBytesReceived() {
		return bytesReceived;
	}

	public long getBytesDropped() {
		return bytesDropped;
	}

	public long getPacketsReceived() {
		return packetsReceived;
	}

	public long getPacketsDropped() {
		return packetsDropped;
	}

	public List<MavlinkPacket> decode(byte[] b) throws IOException {
		if (b == null)
			throw new NullPointerException();
		
		return decode(b, 0, b.length);
	}
	
	public List<MavlinkPacket> decode(byte[] b, int off, int len) throws IOException {
		if (b == null)
			throw new NullPointerException();
		if (off < 0 || len < 0 || off + len > b.length)
			throw new IndexOutOfBoundsException();

		List<MavlinkPacket> result = new ArrayList<MavlinkPacket>();
		
		int i = off;
		int limit = off + len;
		while (i < limit) {
			if (!packetStarted) {
				// stx
				int mavlinkStx = protocol.getMavlinkStx();
				//int stx = 0;
				boolean stxReceived = false;
				while (i < limit) {
					if ((b[i++] & 0xff) == mavlinkStx) {
						stxReceived = true;
						break;
					}
					bytesDropped++;
				}
				if (stxReceived) {
					// starting new message read
					packetOffset = 0;
					packetBuffer[packetOffset++] = b[i - 1];
					packetStarted = true;
					packetComplete = false;
				}
			}
			if (packetStarted && !packetComplete) {
				// initializing payload length
				if (packetOffset == 1 && i < limit) { // only stx was read
					packetLength = (b[i] & 0xff) + headerLength + checksumLength;
				}
				// continue only if packet length was initialized
				if (packetOffset > 1 || limit - i > 0) {
					int length = Math.min(packetLength - packetOffset, limit - i);
					if (length > 0) {
						System.arraycopy(b, i, packetBuffer, packetOffset, length);
						packetOffset += length;
						i += length;
					}
					if (packetOffset >= packetLength) {
						// finalize packet read
						packetComplete = true;
					}
				}
			}
			if (packetComplete) {
				packetsReceived++;
				try {
					MavlinkPacket packet = decodeSinglePacket(packetBuffer, 0, packetLength);
					result.add(packet);
				} catch (Exception e) {
					// TODO error logging can be helpful
					packetsDropped++;
				}
				// reset packet flags
				packetStarted = false;
				packetComplete = false;
			}
		}
		bytesReceived += len;
		return result;
	}
	
	public MavlinkPacket decodeSinglePacket(byte[] b) throws IOException {
		if (b == null)
			throw new NullPointerException();
		
		return decodeSinglePacket(b, 0, b.length);
	}
	
	public MavlinkPacket decodeSinglePacket(byte[] b, int off, int len) throws IOException {
		if (b == null)
			throw new NullPointerException();
		if (off < 0 || len < 0 || off + len > b.length)
			throw new IndexOutOfBoundsException();
		
		int i = off;
		int limit = off + len;
		
		// check: can read header
		if (limit - i < headerLength)
			throw new IllegalArgumentException("buffer to short");
		
		// reading header
		i++; // skip stx
		//int stx = b[i++] & 0xff;
		int payloadLength = b[i++] & 0xff;
		int sequenceNumber = b[i++] & 0xff;
		long systemId = 0L;
		if (protocol.isExpandedSystemId()) {
			systemId = readUnsignedInt32(b, i);
			i += 4;
		} else {
			systemId = b[i++] & 0xff;
		}
		int componentId = b[i++] & 0xff;
		int messageType = b[i++] & 0xff;
		
		// check: payload length and payload id are consistent
		int estimatedPayloadLength = protocol.getMessageLength(messageType);
		if (payloadLength != estimatedPayloadLength)
			throw new IllegalArgumentException("Incorrect payload length");
		// check: can read payload and checksum
		if (limit - i < payloadLength + checksumLength)
			throw new IllegalArgumentException("buffer to short");
		
		// reading checksum
		int crcLow = b[i + payloadLength] & 0xff;
		int crcHigh = b[i + payloadLength + 1] & 0xff;

		// check: crc match
		MavlinkCrc crc = new MavlinkCrc();
		crc.append(b, off + 1, payloadLength + (headerLength - 1));
		if (protocol.isCrcExtraByte())
			crc.append(protocol.getMessageCrcExtraByte(messageType));
		
		int checksum = crc.getChecksum();
		if ((checksum & 0xffff) != ((crcHigh << 8) | crcLow))
			throw new IllegalArgumentException("Checksum mismatch");
		
		// wrapper over buffer
		ByteArrayInputStream in = new ByteArrayInputStream(b, off + headerLength, payloadLength);
		MavlinkMessageBuilder builder = protocol.newMessageBuilder(messageType);
		MavlinkMessage payload = builder.readFrom(in).build();
		
		// constructing result
		MavlinkPacket packet = new MavlinkPacket();
		packet.setPayloadLength(payloadLength);
		packet.setSequenceNumber(sequenceNumber);
		packet.setSystemId(systemId);
		packet.setComponentId(componentId);
		packet.setMessageType(messageType);
		packet.setPayload(payload);
		
		return packet;
	}
	
	private long readUnsignedInt32(byte[] buffer, int offset) {
		if (buffer == null || buffer.length - offset < 4)
			throw new IllegalArgumentException("buffer");
		
		int b1 = buffer[offset] & 0xff;
		int b2 = buffer[offset + 1] & 0xff;
		int b3 = buffer[offset + 2] & 0xff;
		int b4 = buffer[offset + 3] & 0xff;
		int value = protocol.isLittleEndian() ?
				(b4 << 24) | (b3 << 16) | (b2 << 8) | b1 :
				(b1 << 24) | (b2 << 16) | (b3 << 8) | b4;
		return value & 0xffffffffL;
	}
}
