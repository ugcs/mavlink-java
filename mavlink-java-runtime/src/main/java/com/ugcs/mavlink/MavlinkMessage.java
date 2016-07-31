package com.ugcs.mavlink;

import java.io.IOException;
import java.io.OutputStream;

public interface MavlinkMessage {
	int getMavlinkMessageType();
	void writeTo(OutputStream out) throws IOException;
}
