package com.ugcs.mavlink;

import java.io.IOException;
import java.io.InputStream;

public interface MavlinkMessageBuilder {
	MavlinkMessage build();
	MavlinkMessageBuilder readFrom(InputStream in) throws IOException;
}
