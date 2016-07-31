package com.ugcs.mavlink;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class CodedOutputStream extends FilterOutputStream {
	private final boolean littleEndian;
	
	public CodedOutputStream(OutputStream out) {
		this(out, false);
	}
	
	public CodedOutputStream(OutputStream out, boolean littleEndian) {
		super(out);
		
		this.littleEndian = littleEndian;
	}
	
	private void checkNarrowingSafety(long value, int n) throws IOException {
		if ((value >>> n) != 0)
			throw new IOException("Value can't be narrowed without data loss: " + value);
	}

	public void writeFloat(float value) throws IOException {
		writeInt32(Float.floatToIntBits(value));
	}
	
	public void writeDouble(double value) throws IOException {
		writeInt64(Double.doubleToLongBits(value));
	}
	
	public void writeChar(char value) throws IOException {
		checkNarrowingSafety(value & 0xffff, 8);
		writeInt8((byte) value);
	}
	
	public void writeInt8(byte value) throws IOException {
		out.write(value & 0xff);
	}
	
	public void writeUnsignedInt8(int value) throws IOException {
		checkNarrowingSafety(value & 0xffff, 8);
		writeInt8((byte) value);
	}
	
	public void writeInt16(short value) throws IOException {
		if (littleEndian) {
			out.write(value & 0xff);
			out.write((value >>> 8) & 0xff);
		} else {
			out.write((value >>> 8) & 0xff);
			out.write(value & 0xff);
		}
	}
	
	public void writeUnsignedInt16(int value) throws IOException {
		checkNarrowingSafety(value, 16);
		writeInt16((short) value);
	}
	
	public void writeInt32(int value) throws IOException {
		if (littleEndian) {
			out.write(value & 0xff);
			out.write((value >>> 8) & 0xff);
			out.write((value >>> 16) & 0xff);
			out.write((value >>> 24) & 0xff);
		} else {
			out.write((value >>> 24) & 0xff);
			out.write((value >>> 16) & 0xff);
			out.write((value >>> 8) & 0xff);
			out.write(value & 0xff);
		}
	}
	
	public void writeUnsignedInt32(long value) throws IOException {
		checkNarrowingSafety(value, 32);
		writeInt32((int) value);
	}
	
	public void writeInt64(long value) throws IOException {
		if (littleEndian) {
			out.write((int) value & 0xff);
			out.write((int) (value >>> 8) & 0xff);
			out.write((int) (value >>> 16) & 0xff);
			out.write((int) (value >>> 24) & 0xff);
			out.write((int) (value >>> 32) & 0xff);
			out.write((int) (value >>> 40) & 0xff);
			out.write((int) (value >>> 48) & 0xff);
			out.write((int) (value >>> 56) & 0xff);
		} else {
			out.write((int) (value >>> 56) & 0xff);
			out.write((int) (value >>> 48) & 0xff);
			out.write((int) (value >>> 40) & 0xff);
			out.write((int) (value >>> 32) & 0xff);
			out.write((int) (value >>> 24) & 0xff);
			out.write((int) (value >>> 16) & 0xff);
			out.write((int) (value >>> 8) & 0xff);
			out.write((int) value & 0xff);
		}
	}
	
	public void writeUnsignedInt64(long value) throws IOException {
		writeInt64(value);
	}
}
