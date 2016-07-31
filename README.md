# MAVLink Java generator and runtime

MAVLink Java tool used by the [UgCS ground control software](https://www.ugcs.com).

* Runtime provides a stream-based little-endian and big-endian encoder and decoder.
* All generated payload classes are immutable and thus are safe for use in a concurrent environment.
* Can be used for Android applications development.

## Field types

Unsigned integers are represented by the wider signed integer types (except ulong64_t).

MAVLink type | Java type
-------------|----------
uint8_t  | short
uint16_t | int
uint32_t | long
uint64_t | long (not a BigInteger for the CDC compatibility)

## MAVLink packet structure

See http://qgroundcontrol.org/mavlink/start (Packet Anatomy section)

[0]
Packet start sign
v1.0: 0xFE (v0.9: 0x55)
Indicates the start of a new packet.

[1]
Payload length (0-255)
Indicates length of the following payload.

[2]
Packet sequence (0-255)
Each component counts up his send sequence. Allows to detect packet loss

[3]
System ID (1-255)
ID of the SENDING system. Allows to differentiate different MAVs on the same network.

[4]
Component ID (0-255)
ID of the SENDING component. Allows to differentiate different components of the same system, e.g. the IMU and the autopilot.

[5]
Message ID (0-255)
ID of the message - the id defines what the payload “means” and how it should be correctly decoded.

[6] .. [n + 6]
Data (0-255) bytes
Data of the message, depends on the message id.

[n + 7] .. [n + 8]
Checksum (low byte, high byte)
ITU X.25/SAE AS-4 hash, excluding packet start sign, so bytes 1..(n+6) 
Note: The checksum also includes MAVLINK_CRC_EXTRA (Number computed from message fields. 
Protects the packet from decoding a different version of the same packet but with different variables).
