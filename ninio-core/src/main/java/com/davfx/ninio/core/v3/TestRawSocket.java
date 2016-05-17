package com.davfx.ninio.core.v3;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

import com.davfx.ninio.core.Address;
import com.google.common.io.BaseEncoding;


public class TestRawSocket {
	
	static final class OctetConverter {

		  private OctetConverter() { }

		  /**
		   * Converts a set of IPv4 octets to a 32-bit word.
		   *
		   * @param octets A byte array containing the IPv4 octets.
		   * @param offset The offset into the array where the octets start.
		   * @return The 32-bit word representation of the IPv4 address.
		   */
		  public static final int octetsToInt(byte[] octets, int offset) {
		    return (((octets[offset] & 0xff) << 24) |
		            ((octets[offset + 1] & 0xff) << 16) |
		            ((octets[offset + 2] & 0xff) << 8) |
		            (octets[offset + 3] & 0xff));
		  }


		  /**
		   * Same as <code>octetsToInt(octets, 0);</code>
		   */
		  public static final int octetsToInt(byte[] octets) {
		    return octetsToInt(octets, 0);
		  }


		  /**
		   * Converts a set of octets to a 64-bit word.
		   *
		   * @param octets A byte array containing the octets.
		   * @param offset The offset into the array where the octets start.
		   * @return The 64-bit word representation of the octets.
		   */
		  public static final long octetsToLong(byte[] octets, int offset) {
		    return (((octets[offset] & 0xffffL) << 56) |
		            ((octets[offset + 1] & 0xffL) << 48) |
		            ((octets[offset + 2] & 0xffL) << 40) |
		            ((octets[offset + 3] & 0xffL) << 32) |
		            ((octets[offset + 4] & 0xffL) << 24) |
		            ((octets[offset + 5] & 0xffL) << 16) |
		            ((octets[offset + 6] & 0xffL) << 8) |
		            (octets[offset + 7] & 0xffL));
		  }


		  /**
		   * Same as <code>octetsToLong(octets, 0);</code>
		   */
		  public static final long octetsToLong(byte[] octets) {
		    return octetsToLong(octets, 0);
		  }


		  /**
		   * Converts a set of IPv4 octets to a string representation.
		   *
		   * @param buffer The StringBuffer to which to append the string.
		   * @param octets A byte array containing the IPv4 octets.
		   * @param offset The offset into the array where the octets start.
		   */
		  public static final void octetsToString(StringBuffer buffer, byte[] octets,
		                                          int offset)
		  {
		    buffer.append(octets[offset++] & 0xff);
		    buffer.append(".");
		    buffer.append(octets[offset++] & 0xff);
		    buffer.append(".");
		    buffer.append(octets[offset++] & 0xff);
		    buffer.append(".");
		    buffer.append(octets[offset++] & 0xff);
		  }


		  /**
		   * Same as <code>octetsToString(buffer, octets, 0);</code>
		   */
		  public static final void octetsToString(StringBuffer buffer, byte[] octets) {
		    octetsToString(buffer, octets, 0);
		  }


		  /**
		   * Converts a 32-bit word representation of an IPv4 address to a
		   * string representation.
		   *
		   * @param buffer The StringBuffer to which to append the string.
		   * @param address The 32-bit word representation of the address.
		   */
		  public static final void intToString(StringBuffer buffer, int address) {
		    buffer.append(0xff & (address >>> 24));
		    buffer.append(".");
		    buffer.append(0xff & (address >>> 16));
		    buffer.append(".");
		    buffer.append(0xff & (address >>> 8));
		    buffer.append(".");
		    buffer.append(0xff & address);
		  }


		  /**
		   * Converts a 32-bit word representation of an IPv4 address to a
		   * byte array of octets.
		   *
		   * @param address The 32-bit word representation of the IPv4 address.
		   * @param octets The byte array in which to store the IPv4 octets.
		   * @param offset The offset into the array where the octets start.
		   */
		  public static final void intToOctets(int address, byte[] octets,
		                                       int offset)
		  {
		    octets[offset]     = (byte)(0xff & (address >>> 24));
		    octets[offset + 1] = (byte)(0xff & (address >>> 16));
		    octets[offset + 2] = (byte)(0xff & (address >>> 8));
		    octets[offset + 3] = (byte)(0xff & address);
		  }


		  /**
		   * Same as <code>intToOctets(address, octets, 0);</code>
		   */
		  public static final void intToOctets(int address, byte[] octets) {
		    intToOctets(address, octets, 0);
		  }


		  /**
		   * Converts a 64-bit word to a byte array of octets.
		   *
		   * @param address The 64-bit word.
		   * @param octets The byte array in which to store octets.
		   * @param offset The offset into the array where the octets start.
		   */
		  public static final void longToOctets(long address, byte[] octets,
		                                        int offset)
		  {
		    octets[offset]     = (byte)(0xffL & (address >>> 56));
		    octets[offset + 1] = (byte)(0xffL & (address >>> 48));
		    octets[offset + 2] = (byte)(0xffL & (address >>> 40));
		    octets[offset + 3] = (byte)(0xffL & (address >>> 32));
		    octets[offset + 4] = (byte)(0xffL & (address >>> 24));
		    octets[offset + 5] = (byte)(0xffL & (address >>> 16));
		    octets[offset + 6] = (byte)(0xffL & (address >>> 8));
		    octets[offset + 7] = (byte)(0xffL & address);
		  }


		  /**
		   * Same as <code>longToOctets(address, octets, 0);</code>
		   */
		  public static final void longToOctets(long address, byte[] octets) {
		    longToOctets(address, octets, 0);
		  }

		}

	/**
	 * IPPacket wraps the raw bytes comprising an IPv4 packet and exposes
	 * its content via setter and getter methods.  After you alter the
	 * header of an IP packet you have to recompute the checksum with
	 * {@link #computeIPChecksum computeIPChecksum()}.  The structure of
	 * IP packets is described in
	 * <a href="http://www.ietf.org/rfc/rfc0760.txt?number=760">RFC 760</a>.
	 *
	 * @author <a href="http://www.savarese.org/">Daniel F. Savarese</a>
	 */

	static class IPPacket {

	  /** Offset into byte array of the type of service header value. */
	  public static final int OFFSET_TYPE_OF_SERVICE = 1;

	  /** Offset into byte array of total packet length header value. */
	  public static final int OFFSET_TOTAL_LENGTH = 2;

	  /** Offset into byte array of the identification header value. */
	  public static final int OFFSET_IDENTIFICATION = 4;

	  /** Offset into byte array of the flags header value. */
	  public static final int OFFSET_FLAGS = 6;

	  /** Offset into byte array of source address header value. */
	  public static final int OFFSET_SOURCE_ADDRESS      = 12;

	  /** Number of bytes in source address. */
	  public static final int LENGTH_SOURCE_ADDRESS      = 4;

	  /** Offset into byte array of destination address header value. */
	  public static final int OFFSET_DESTINATION_ADDRESS = 16;

	  /** Number of bytes in destination address. */
	  public static final int LENGTH_DESTINATION_ADDRESS = 4;

	  /** Offset into byte array of time to live header value. */
	  public static final int OFFSET_TTL      = 8;

	  /** Offset into byte array of protocol number header value. */
	  public static final int OFFSET_PROTOCOL = 9;

	  /** Offset into byte array of header checksum header value. */
	  public static final int OFFSET_IP_CHECKSUM = 10;

	  /** Protocol constant for IPv4. */
	  public static final int PROTOCOL_IP   = 0;

	  /** Protocol constant for ICMP. */
	  public static final int PROTOCOL_ICMP = 1;

	  /** Protocol constant for TCP. */
	  public static final int PROTOCOL_TCP  = 6;

	  /** Protocol constant for UDP. */
	  public static final int PROTOCOL_UDP  = 17;

	  /** Raw packet data. */
	  protected byte[] _data_;


	  /**
	   * Creates a new IPPacket of a given size.
	   *
	   * @param size The number of bytes in the packet.
	   */
	  public IPPacket(int size) {
	    setData(new byte[size]);
	  }


	  /**
	   * @return The size of the packet.
	   */
	  public int size() {
	    return _data_.length;
	  }


	  /**
	   * Sets the raw packet byte array.  Although this method would
	   * appear to violate object-oriented principles, it is necessary to
	   * implement efficient packet processing.  You don't necessarily
	   * want to allocate a new IPPacket and data buffer every time a
	   * packet arrives and you need to be able to wrap packets from
	   * APIs that supply them as byte arrays.
	   *
	   * @param data The raw packet byte array to wrap.
	   */
	  public void setData(byte[] data) {
	    _data_ = data;
	  }


	  /**
	   * Copies the raw packet data into a byte array.  If the array
	   * is too small to hold the data, the data is truncated.
	   *
	   * @param data The raw packet byte array to wrap.
	   */
	  public void getData(byte[] data) {
	    System.arraycopy(_data_, 0, data, 0, data.length);
	  }


	  /**
	   * Copies the contents of an IPPacket to the calling instance.  If
	   * the two packets are of different lengths, a new byte array is
	   * allocated equal to the length of the packet parameter.
	   *
	   * @param packet  The packet to copy from.
	   */
	  public final void copy(IPPacket packet) {
	    if(_data_.length != packet.size())
	      setData(new byte[packet.size()]);
	    System.arraycopy(packet._data_, 0, _data_, 0, _data_.length);
	  }


	  /**
	   * Sets the IP version header value.
	   *
	   * @param version A 4-bit unsigned integer.
	   */
	  public final void setIPVersion(int version) {
	    _data_[0] &= 0x0f;
	    _data_[0] |= ((version << 4) & 0xf0);
	  }


	  /**
	   * Returns the IP version header value.
	   *
	   * @return The IP version header value.
	   */
	  public final int getIPVersion() {
	    return ((_data_[0] & 0xf0) >> 4);
	  }


	  /**
	   * Sets the IP header length field.  At most, this can be a 
	   * four-bit value.  The high order bits beyond the fourth bit
	   * will be ignored.
	   *
	   * @param length The length of the IP header in 32-bit words.
	   */
	  public void setIPHeaderLength(int length) {
	    // Clear low order bits and then set
	    _data_[0] &= 0xf0;
	    _data_[0] |= (length & 0x0f);
	  }


	  /**
	   * @return The length of the IP header in 32-bit words.
	   */
	  public final int getIPHeaderLength() {
	    return (_data_[0] & 0x0f);
	  }


	  /**
	   * @return The length of the IP header in bytes.
	   */
	  public final int getIPHeaderByteLength() {
	    return getIPHeaderLength() << 2;
	  }


	  /**
	   * Sets the IP type of service header value.  You have to set the individual
	   * service bits yourself.  Convenience methods for setting the service
	   * bit fields directly may be added in a future version.
	   *
	   * @param service An 8-bit unsigned integer.
	   */
	  public final void setTypeOfService(int service) {
	    _data_[OFFSET_TYPE_OF_SERVICE] = (byte)(service & 0xff);
	  }


	  /**
	   * Returns the IP type of service header value.
	   *
	   * @return The IP type of service header value.
	   */
	  public final int getTypeOfService() {
	    return (_data_[OFFSET_TYPE_OF_SERVICE] & 0xff);
	  }


	  /**
	   * Sets the IP packet total length header value.
	   *
	   * @param length The total IP packet length in bytes.
	   */
	  public final void setIPPacketLength(int length) {
	    _data_[OFFSET_TOTAL_LENGTH]     = (byte)((length >> 8) & 0xff);
	    _data_[OFFSET_TOTAL_LENGTH + 1] = (byte)(length & 0xff);
	  }


	  /**
	   * @return The IP packet total length header value.
	   */
	  public final int getIPPacketLength() {
	    return (((_data_[OFFSET_TOTAL_LENGTH] & 0xff) << 8) |
	            (_data_[OFFSET_TOTAL_LENGTH + 1] & 0xff)); 
	  }


	  /**
	   * Sets the IP identification header value.
	   *
	   * @param id A 16-bit unsigned integer.
	   */
	  public void setIdentification(int id) {
	    _data_[OFFSET_IDENTIFICATION]     = (byte)((id >> 8) & 0xff);
	    _data_[OFFSET_IDENTIFICATION + 1] = (byte)(id & 0xff);
	  }


	  /**
	   * Returns the IP identification header value.
	   *
	   * @return The IP identification header value.
	   */
	  public final int getIdentification() {
	    return (((_data_[OFFSET_IDENTIFICATION] & 0xff) << 8) |
	            (_data_[OFFSET_IDENTIFICATION + 1] & 0xff)); 
	  }


	  /**
	   * Sets the IP flags header value.  You have to set the individual
	   * flag bits yourself.  Convenience methods for setting the flag
	   * bit fields directly may be added in a future version.
	   *
	   * @param flags A 3-bit unsigned integer.
	   */
	  public final void setIPFlags(int flags) {
	    _data_[OFFSET_FLAGS] &= 0x1f;
	    _data_[OFFSET_FLAGS] |= ((flags << 5) & 0xe0);
	  }


	  /**
	   * Returns the IP flags header value.
	   *
	   * @return The IP flags header value.
	   */
	  public final int getIPFlags() {
	    return ((_data_[OFFSET_FLAGS] & 0xe0) >> 5);
	  }


	  /**
	   * Sets the fragment offset header value.  The offset specifies a
	   * number of octets (i.e., bytes).
	   *
	   * @param offset A 13-bit unsigned integer.
	   */
	  public void setFragmentOffset(int offset) {
	    _data_[OFFSET_FLAGS] &= 0xe0;
	    _data_[OFFSET_FLAGS] |= ((offset >> 8) & 0x1f);
	    _data_[OFFSET_FLAGS + 1] = (byte)(offset & 0xff);
	  }


	  /**
	   * Returns the fragment offset header value.
	   *
	   * @return The fragment offset header value.
	   */
	  public final int getFragmentOffset() {
	    return (((_data_[OFFSET_FLAGS] & 0x1f) << 8) |
	            (_data_[OFFSET_FLAGS + 1] & 0xff)); 
	  }


	  /**
	   * Sets the protocol number.
	   *
	   * @param protocol The protocol number.
	   */
	  public final void setProtocol(int protocol) {
	    _data_[OFFSET_PROTOCOL] = (byte)protocol;
	  }


	  /**
	   * @return The protocol number.
	   */
	  public final int getProtocol() {
	    return _data_[OFFSET_PROTOCOL];
	  }


	  /**
	   * Sets the time to live value in seconds.
	   *
	   * @param ttl The time to live value in seconds.
	   */
	  public final void setTTL(int ttl) {
	    _data_[OFFSET_TTL] = (byte)(ttl & 0xff);
	  }


	  /**
	   * @return The time to live value in seconds.
	   */
	  public final int getTTL() {
	    return (_data_[OFFSET_TTL] & 0xff);
	  }


	  /**
	   * Calculates checksums assuming the checksum is a 16-bit header field.
	   * This method is generalized to work for IP, ICMP, UDP, and TCP packets
	   * given the proper parameters.
	   */
	  protected int _computeChecksum_(int startOffset,
	                                  int checksumOffset,
	                                  int length,
	                                  int virtualHeaderTotal,
	                                  boolean update)
	  {
	    int total = 0;
	    int i     = startOffset;
	    int imax  = checksumOffset;

	    while(i < imax)
	      total+=(((_data_[i++] & 0xff) << 8) | (_data_[i++] & 0xff));

	    // Skip existing checksum.
	    i = checksumOffset + 2;

	    imax = length - (length % 2);

	    while(i < imax)
	      total+=(((_data_[i++] & 0xff) << 8) | (_data_[i++] & 0xff));

	    if(i < length)
	      total+=((_data_[i] & 0xff) << 8);

	    total+=virtualHeaderTotal;

	    // Fold to 16 bits
	    while((total & 0xffff0000) != 0)
	      total = (total & 0xffff) + (total >>> 16);

	    total = (~total & 0xffff);

	    if(update) {
	      _data_[checksumOffset]     = (byte)(total >> 8);
	      _data_[checksumOffset + 1] = (byte)(total & 0xff);
	    }

	    return total;
	  } 


	  /**
	   * Computes the IP checksum, optionally updating the IP checksum header.
	   *
	   * @param update Specifies whether or not to update the IP checksum
	   * header after computing the checksum.  A value of true indicates
	   * the header should be updated, a value of false indicates it
	   * should not be updated.
	   * @return The computed IP checksum.
	   */
	  public final int computeIPChecksum(boolean update) {
	    return _computeChecksum_(0, OFFSET_IP_CHECKSUM, getIPHeaderByteLength(),
	                             0, update);
	  }


	  /**
	   * Same as <code>computeIPChecksum(true);</code>
	   *
	   * @return The computed IP checksum value.
	   */
	  public final int computeIPChecksum() {
	    return computeIPChecksum(true);
	  }


	  /**
	   * @return The IP checksum header value.
	   */
	  public final int getIPChecksum() {
	    return (((_data_[OFFSET_IP_CHECKSUM] & 0xff) << 8) |
	            (_data_[OFFSET_IP_CHECKSUM + 1] & 0xff)); 
	  }


	  /**
	   * Retrieves the source IP address into a byte array.  The array
	   * should be {@link #LENGTH_SOURCE_ADDRESS} bytes long.
	   *
	   * @param address The array in which to store the address.
	   */
	  public final void getSource(byte[] address) {
	    System.arraycopy(_data_, OFFSET_SOURCE_ADDRESS, address,
	                     0, (address.length < LENGTH_SOURCE_ADDRESS ?
	                         address.length : LENGTH_SOURCE_ADDRESS));
	  }


	  /**
	   * Retrieves the destionation IP address into a byte array.  The array
	   * should be {@link #LENGTH_DESTINATION_ADDRESS} bytes long.
	   *
	   * @param address The array in which to store the address.
	   */
	  public final void getDestination(byte[] address) {
	    System.arraycopy(_data_, OFFSET_DESTINATION_ADDRESS, address,
	                     0, (address.length < LENGTH_DESTINATION_ADDRESS ?
	                         address.length : LENGTH_DESTINATION_ADDRESS));
	  }


	  /**
	   * Retrieves the source IP address as a string into a StringBuffer.
	   *
	   * @param buffer The StringBuffer in which to store the address.
	   */
	  public final void getSource(StringBuffer buffer) {
	    OctetConverter.octetsToString(buffer, _data_, OFFSET_SOURCE_ADDRESS);
	  }


	  /**
	   * Retrieves the destination IP address as a string into a StringBuffer.
	   *
	   * @param buffer The StringBuffer in which to store the address.
	   */
	  public final void getDestination(StringBuffer buffer) {
	    OctetConverter.octetsToString(buffer, _data_, OFFSET_DESTINATION_ADDRESS);
	  }


	  /**
	   * Sets the source IP address using a word representation.
	   *
	   * @param src The source IP address as a 32-bit word.
	   */
	  public final void setSourceAsWord(int src) {
	    OctetConverter.intToOctets(src, _data_, OFFSET_SOURCE_ADDRESS);
	  }


	  /**
	   * Sets the destination IP address using a word representation.
	   *
	   * @param dest The source IP address as a 32-bit word.
	   */
	  public final void setDestinationAsWord(int dest) {
	    OctetConverter.intToOctets(dest, _data_, OFFSET_DESTINATION_ADDRESS);
	  }


	  /**
	   * @return The source IP address as a 32-bit word.
	   */
	  public final int getSourceAsWord() {
	    return OctetConverter.octetsToInt(_data_, OFFSET_SOURCE_ADDRESS);
	  }


	  /**
	   * @return The destination IP address as a 32-bit word.
	   */
	  public final int getDestinationAsWord() {
	    return OctetConverter.octetsToInt(_data_, OFFSET_DESTINATION_ADDRESS);
	  }


	  /**
	   * @return The source IP address as a java.net.InetAddress instance.
	   */
	  public final InetAddress getSourceAsInetAddress()
	    throws UnknownHostException
	  {
	    StringBuffer buf = new StringBuffer();
	    getSource(buf);
	    return InetAddress.getByName(buf.toString());
	    // This works only with JDK 1.4 and up
	    /*
	    byte[] octets = new byte[4];
	    getSource(octets);
	    return InetAddress.getByAddress(octets);
	    */
	  }


	  /**
	   * @return The destination IP address as a java.net.InetAddress instance.
	   */
	  public final InetAddress getDestinationAsInetAddress()
	    throws UnknownHostException
	  {
	    StringBuffer buf = new StringBuffer();
	    getDestination(buf);
	    return InetAddress.getByName(buf.toString());
	    // This works only with JDK 1.4 and up
	    /*
	    byte[] octets = new byte[4];
	    getDestination(octets);
	    return InetAddress.getByAddress(octets);
	    */
	  }
	}
	static abstract class ICMPPacket extends IPPacket {

		  /** Offset into the ICMP packet of the type header value. */
		  public static final int OFFSET_TYPE         = 0;

		  /** Offset into the ICMP packet of the code header value. */
		  public static final int OFFSET_CODE         = 1;

		  /** Offset into the ICMP packet of the ICMP checksum. */
		  public static final int OFFSET_ICMP_CHECKSUM = 2;

		  /** Offset into the ICMP packet of the identifier header value. */
		  public static final int OFFSET_IDENTIFIER   = 4;

		  /** Offset into the ICMP packet of the sequence number header value. */
		  public static final int OFFSET_SEQUENCE     = 6;

		  /** The ICMP type number for an echo request. */
		  public static final int TYPE_ECHO_REQUEST   = 8;

		  /** The ICMP type number for an echo reply. */
		  public static final int TYPE_ECHO_REPLY     = 0;

		  /** The byte offset into the IP packet where the ICMP packet begins. */
		  int _offset;


		  /**
		   * Creates a new ICMP packet of a given size.
		   *
		   * @param size The number of bytes in the packet.
		   */
		  public ICMPPacket(int size) {
		    super(size);
		    _offset = 0;
		  }


		  /**
		   * Creates a new ICMP packet that is a copy of a given packet.
		   *
		   * @param packet The packet to replicate.
		   */
		  public ICMPPacket(ICMPPacket packet) {
		    super(packet.size());
		    copy(packet);
		    _offset = packet._offset;
		  }


		  /** @return The number of bytes in the ICMP packet header. */
		  public abstract int getICMPHeaderByteLength();


		  public void setIPHeaderLength(int length) {
		    super.setIPHeaderLength(length);
		    _offset = getIPHeaderByteLength();
		  }


		  /**
		   * @return The total number of bytes in the IP and ICMP headers.
		   */
		  public final int getCombinedHeaderByteLength() {
		    return _offset + getICMPHeaderByteLength();
		  }


		  /**
		   * Sets the length of the ICMP data payload.
		   *
		   * @param length The length of the ICMP data payload in bytes.
		   */
		  public final void setICMPDataByteLength(int length) {
		    if(length < 0)
		      length = 0;

		    setIPPacketLength(getCombinedHeaderByteLength() + length);
		  }


		  /**
		   * @return The number of bytes in the ICMP data payload.
		   */
		  public final int getICMPDataByteLength() {
		    return getIPPacketLength() - getCombinedHeaderByteLength();
		  }


		  /**
		   * @return The ICMP packet length.  This is the size of the IP packet
		   * minus the size of the IP header.
		   */
		  public final int getICMPPacketByteLength() {
		    return getIPPacketLength() - _offset;
		  }


		  /**
		   * Copies the contents of an ICMPPacket.  If the current data array is
		   * of insufficient length to store the contents, a new array is
		   * allocated.
		   *
		   * @param packet The TCPPacket to copy.
		   */
		  public final void copyData(ICMPPacket packet) {
		    if(_data_.length < packet._data_.length) {
		      byte[] data = new byte[packet._data_.length];
		      System.arraycopy(_data_, 0, data, 0, getCombinedHeaderByteLength());
		      _data_ = data;
		    }
		    int length = packet.getICMPDataByteLength();
		    System.arraycopy(packet._data_, packet.getCombinedHeaderByteLength(),
		                     _data_, getCombinedHeaderByteLength(), length);
		    setICMPDataByteLength(length);
		  }


		  public void setData(byte[] data) {
		    super.setData(data);
		    _offset = getIPHeaderByteLength();
		  }


		  /**
		   * Sets the ICMP type header field.
		   *
		   * @param type The new type.
		   */
		  public final void setType(int type) {
		    _data_[_offset + OFFSET_TYPE] = (byte)(type & 0xff);
		  }


		  /**
		   * @return The ICMP type header field.
		   */
		  public final int getType() {
		    return (_data_[_offset + OFFSET_TYPE] & 0xff);
		  }


		  /**
		   * Sets the ICMP code header field.
		   *
		   * @param code The new type.
		   */
		  public final void setCode(int code) {
		    _data_[_offset + OFFSET_CODE] = (byte)(code & 0xff);
		  }


		  /**
		   * @return The ICMP code header field.
		   */
		  public final int getCode() {
		    return (_data_[_offset + OFFSET_CODE] & 0xff);
		  }


		  /**
		   * @return The ICMP checksum.
		   */
		  public final int getICMPChecksum() {
		    return (((_data_[_offset + OFFSET_ICMP_CHECKSUM] & 0xff) << 8) |
		            (_data_[_offset + OFFSET_ICMP_CHECKSUM + 1] & 0xff)); 
		  }


		  /**
		   * Computes the ICMP checksum, optionally updating the ICMP checksum header.
		   *
		   * @param update Specifies whether or not to update the ICMP checksum
		   * header after computing the checksum.  A value of true indicates
		   * the header should be updated, a value of false indicates it
		   * should not be updated.
		   * @return The computed ICMP checksum.
		   */
		  public final int computeICMPChecksum(boolean update) {
		    return _computeChecksum_(_offset, _offset + OFFSET_ICMP_CHECKSUM,
		                             getIPPacketLength(), 0, update);
		  }


		  /**
		   * Same as <code>computeICMPChecksum(true);</code>
		   *
		   * @return The computed ICMP checksum value.
		   */
		  public final int computeICMPChecksum() {
		    return computeICMPChecksum(true);
		  }

		}
	static class ICMPEchoPacket extends ICMPPacket {

		  /** Offset into the ICMP packet of the identifier header value. */
		  public static final int OFFSET_IDENTIFIER   = 4;

		  /** Offset into the ICMP packet of the sequence number header value. */
		  public static final int OFFSET_SEQUENCE     = 6;


		  /**
		   * Creates a new ICMP echo packet of a given size.
		   *
		   * @param size The number of bytes in the packet.
		   */
		  public ICMPEchoPacket(int size) {
		    super(size);
		  }


		  /**
		   * Creates a new ICMP echo packet that is a copy of a given packet.
		   *
		   * @param packet The packet to replicate.
		   */
		  public ICMPEchoPacket(ICMPEchoPacket packet) {
		    super(packet);
		  }


		  public int getICMPHeaderByteLength() {
		    return 8;
		  }


		  /**
		   * Sets the identifier header field.
		   *
		   * @param id The new identifier.
		   */
		  public final void setIdentifier(int id) {
		    _data_[_offset + OFFSET_IDENTIFIER]     = (byte)((id >> 8) & 0xff);
		    _data_[_offset + OFFSET_IDENTIFIER + 1] = (byte)(id & 0xff);
		  }


		  /**
		   * @return The identifier header field.
		   */
		  public final int getIdentifier() {
		    return (((_data_[_offset + OFFSET_IDENTIFIER] & 0xff) << 8) |
		             (_data_[_offset + OFFSET_IDENTIFIER + 1] & 0xff));
		  }


		  /**
		   * Sets the sequence number.
		   *
		   * @param seq The new sequence number.
		   */
		  public final void setSequenceNumber(int seq) {
		    _data_[_offset + OFFSET_SEQUENCE]     = (byte)((seq >> 8) & 0xff);
		    _data_[_offset + OFFSET_SEQUENCE + 1] = (byte)(seq & 0xff);
		  }


		  /**
		   * @return The sequence number.
		   */
		  public final int getSequenceNumber() {
		    return (((_data_[_offset + OFFSET_SEQUENCE] & 0xff) << 8) |
		             (_data_[_offset + OFFSET_SEQUENCE + 1] & 0xff));
		  }

		}
	interface EchoReplyListener {
		void notifyEchoReply(ICMPEchoPacket packet, byte[] data, int dataOffset, byte[] srcAddress) throws IOException;
	}

	static class Pinger4 {
		private static final int TIMEOUT = 0;

		protected Connector socket;
		protected ICMPEchoPacket recvPacket;
		protected int offset, length, dataOffset;
		protected int requestType, replyType;
		protected byte[] recvData, srcAddress;
		protected int sequence, identifier;
		protected Receiver listener;

		public Pinger4(int id, int protocolFamily, int protocol) throws IOException {
			sequence = 0;
			identifier = id;
			setEchoReplyListener(null);

			recvPacket = new ICMPEchoPacket(1);
			recvData = new byte[84];

			recvPacket.setData(recvData);
			recvPacket.setIPHeaderLength(5);
			recvPacket.setICMPDataByteLength(56);


			socket = RawSocket.builder().receiving(new Receiver() {
				@Override
				public void received(Connector connector, Address address, ByteBuffer buffer) {
					listener.received(connector, address, buffer);
				}
			}).protocol(protocol).create(null);
			/*socket.open(protocolFamily, protocol);

			try {
				socket.setSendTimeout(TIMEOUT);
				socket.setReceiveTimeout(TIMEOUT);
			} catch (java.net.SocketException se) {
				socket.setUseSelectTimeout(true);
				socket.setSendTimeout(TIMEOUT);
				socket.setReceiveTimeout(TIMEOUT);
			}*/
		}

		public Pinger4(int id) throws IOException {
			this(id, NativeRawSocket.PF_INET, NativeRawSocket.getProtocolByName("icmp"));

			srcAddress = new byte[4];
			requestType = ICMPPacket.TYPE_ECHO_REQUEST;
			replyType = ICMPPacket.TYPE_ECHO_REPLY;
		}

		public void setEchoReplyListener(Receiver l) {
			listener = l;
		}

		/**
		 * Closes the raw socket opened by the constructor. After calling this
		 * method, the object cannot be used.
		 */
		public void close() throws IOException {
			socket.close();
		}

		public void sendEchoRequest(InetAddress host) throws IOException {
			byte[] sendData = new byte[16];

			ByteBuffer bb = ByteBuffer.wrap(sendData);
			bb.put((byte) 8); //requestType);
			bb.put((byte) 0); //code
			int checksumPosition = bb.position();
			System.out.println("checksumPosition="+checksumPosition);
			bb.putShort((short) 0); //checksum
			bb.putShort((short) (65535)); // identifier
			bb.putShort((short) (10)); // sequence
			long nt = System.nanoTime();
			bb.putLong(nt);
			int endPosition = bb.position();
			System.out.println("end="+endPosition);

			bb.position(0);
			int checksum = 0;
			while (bb.position() < endPosition) {
				checksum += bb.getShort() & 0xFFFF;
			}
		    while((checksum & 0xFFFF0000) != 0) {
		    	checksum = (checksum & 0xFFFF) + (checksum >>> 16);
		    }

		    checksum = (~checksum & 0xffff);
			bb.position(checksumPosition);
			System.out.println("total="+checksum);
			bb.putShort((short) (checksum & 0xFFFF));
			bb.position(endPosition);
			bb.flip();
			
			socket.send(new Address(host.getHostName(), 0), bb);

			System.out.println(BaseEncoding.base16().encode(sendData));
			
			sendData = new byte[84];
			ICMPEchoPacket sendPacket = new ICMPEchoPacket(1);
			sendPacket.setData(sendData);
			sendPacket.setIPHeaderLength(5);
			sendPacket.setICMPDataByteLength(56);
			offset = sendPacket.getIPHeaderByteLength();
			System.out.println("getIPHeaderByteLength=" + sendPacket.getIPHeaderByteLength());
			dataOffset = offset + sendPacket.getICMPHeaderByteLength();
			length = sendPacket.getICMPPacketByteLength();
			System.out.println("length=" + length);

			sendPacket.setType(requestType);
			sendPacket.setCode(0);
			sendPacket.setIdentifier(identifier);
			sendPacket.setSequenceNumber(sequence++);

			OctetConverter.longToOctets(nt, sendData, dataOffset);

			sendPacket.computeICMPChecksum();

			//System.out.println(BaseEncoding.base16().encode(sendData));

			//socket.send(new Address(host.getHostName(), 0), ByteBuffer.wrap(sendData, offset, length));
			
			//socket.write(host, sendData, offset, length);
		}
/*
		public void receive() throws IOException {
			socket.read(recvData, srcAddress);
		}

		public void receiveEchoReply() throws IOException {
			do {
				receive();
			} while (recvPacket.getType() != replyType || recvPacket.getIdentifier() != identifier);

			if (listener != null)
				listener.notifyEchoReply(recvPacket, recvData, dataOffset, srcAddress);
		}*/

		/**
		 * Issues a synchronous ping.
		 *
		 * @param host
		 *            The host to ping.
		 * @return The round trip time in nanoseconds.
		 */
		public long ping(InetAddress host) throws IOException {
			sendEchoRequest(host);
			//receiveEchoReply();

			long end = System.nanoTime();
			long start = OctetConverter.octetsToLong(recvData, dataOffset);

			return (end - start);
		}

	}

	public static void main(String[] args) throws Exception {
		Pinger4 p = new Pinger4(65535);
		p.setEchoReplyListener(new Receiver() {
			@Override
			public void received(Connector connector, Address address, ByteBuffer buffer) {
				//buffer.position(buffer.position() + 20);
				System.out.println(BaseEncoding.base16().encode(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining()));
				int type = buffer.get();
				int code = buffer.get();
				int checksum = buffer.getShort();
				int identifier = buffer.getShort();
				int sequence = buffer.getShort();
				long time = buffer.getLong();
				System.out.println("type="+type);
				System.out.println("code="+code);
				System.out.println("identifier="+identifier);
				System.out.println("sequence="+sequence);
				System.out.println("time =" + time + " now = " + System.nanoTime() +  "     " + (System.nanoTime() - time));
				/*
				long end = System.nanoTime();
				long start = OctetConverter.octetsToLong(buffer.array(), buffer.arrayOffset() + buffer.position()); //data, dataOffset);
				// Note: Java and JNI overhead will be noticeable (100-200
				// microseconds) for sub-millisecond transmission times.
				// The first ping may even show several seconds of delay
				// because of initial JIT compilation overhead.
				double rtt = (double) ((end - start) / 1_000_000_000d);
				System.out.println(rtt);
				*/
			}
		});
		p.sendEchoRequest(InetAddress.getByName("8.8.8.8"));
		//p.receiveEchoReply();
	}
}
