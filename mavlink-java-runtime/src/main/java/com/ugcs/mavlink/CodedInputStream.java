package com.ugcs.mavlink;

import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public class CodedInputStream extends FilterInputStream {
	private final boolean littleEndian;
	
	public CodedInputStream(InputStream in) {
		this(in, false);
	}

	public CodedInputStream(InputStream in, boolean littleEndian) {
		super(in);
		
		this.littleEndian = littleEndian;
	}

	public float readFloat() throws IOException {
		return Float.intBitsToFloat(readInt32());
	}
	
	public double readDouble() throws IOException {
		return Double.longBitsToDouble(readInt64());
	}
	
	public char readChar() throws IOException {
		return (char) (readInt8() & 0xff);
	}
	
	public byte readInt8() throws IOException {
		int b1 = in.read();
		if (b1 < 0)
			throw new EOFException();
		return (byte) b1;
	}
	
	public int readUnsignedInt8() throws IOException {
		return readInt8() & 0xff;
	}
	
	public short readInt16() throws IOException {
		int b1 = in.read();
		int b2 = in.read();
		if ((b1 | b2) < 0)
			throw new EOFException();
		
		return littleEndian ? 
				(short) ((b2 << 8) | b1) :
				(short) ((b1 << 8) | b2);
	}
	
	public int readUnsignedInt16() throws IOException {
		return readInt16() & 0xffff;
	}
	
	public int readInt32() throws IOException {
		int b1 = in.read();
		int b2 = in.read();
		int b3 = in.read();
		int b4 = in.read();
		if ((b1 | b2 | b3 | b4) < 0)
			throw new EOFException();
		
		return littleEndian ?
				(b4 << 24) | (b3 << 16) | (b2 << 8) | b1 :
				(b1 << 24) | (b2 << 16) | (b3 << 8) | b4;
	}
	
	public long readUnsignedInt32() throws IOException {
		return readInt32() & 0xffffffffL;
	}
	
	public long readInt64() throws IOException {
		int b1 = in.read();
		int b2 = in.read();
		int b3 = in.read();
		int b4 = in.read();
		int b5 = in.read();
		int b6 = in.read();
		int b7 = in.read();
		int b8 = in.read();
		if ((b1 | b2 | b3 | b4 | b5 | b6 | b7 | b8) < 0)
			throw new EOFException();
		
		return littleEndian ?
				((long) b8 << 56) | ((long) b7 << 48) | ((long) b6 << 40) | ((long) b5 << 32) | 
				((long) b4 << 24) | (b3 << 16) | (b2 << 8) | b1 :
				((long) b1 << 56) | ((long) b2 << 48) | ((long) b3 << 40) | ((long) b4 << 32) | 
				((long) b5 << 24) | (b6 << 16) | (b7 << 8) | b8;
	}
	
	public long readUnsignedInt64() throws IOException {
		// for CDC compatibility ulong64_t is stored as a plain java long
		return readInt64();
	}
}
