package net.razorvine.pickle;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;

/**
 * Utility stuff for dealing with pickle data streams.
 * 
 * @author Irmen de Jong (irmen@razorvine.net)
 */
public class PickleUtils {

	private InputStream input;
	
	/**
	 * Create a pickle utils instance and remember the given input stream. 
	 */
	public PickleUtils(InputStream stream) {
		this.input = stream;
	}
	
	/**
	 * read a line of text, excluding the terminating LF char
	 */
	public String readline() throws IOException {
		return readline(false);
	}

	/**
	 * read a line of text, possibly including the terminating LF char
	 */
	public String readline(boolean includeLF) throws IOException {
		StringBuilder sb = new StringBuilder();
		while (true) {
			int c = input.read();
			if (c == -1) {
				if (sb.length() == 0)
					throw new IOException("premature end of file");
				break;
			}
			if (c != '\n' || includeLF)
				sb.append((char) c);
			if (c == '\n')
				break;
		}
		return sb.toString();
	}

	/**
	 * read a single unsigned byte
	 */
	public short readbyte() throws IOException {
		int b = input.read();
		return (short) b;
	}

	/**
	 * read a number of signed bytes
	 */
	public byte[] readbytes(int n) throws IOException {
		byte[] buffer = new byte[n];
		readbytes_into(buffer, 0, n);
		return buffer;
	}

	/**
	 * read a number of signed bytes into the specified location in an existing byte array
	 */
	public void readbytes_into(byte[] buffer, int offset, int length) throws IOException {
		while (length > 0) {
			int read = this.input.read(buffer, offset, length);
			if (read == -1)
				throw new IOException("expected more bytes in input stream");
			offset += read;
			length -= read;
		}
	}

	/**
	 * Convert a couple of bytes into the corresponding integer number.
	 * Can deal with 2-bytes unsigned int and 4-bytes signed int.
	 */
	public int bytes_to_integer(byte[] bytes) {
		// this operates on little-endian bytes
		if (bytes.length == 2) {
			// 2-bytes unsigned int
			int i = bytes[1] & 0xff;
			i <<= 8;
			i |= bytes[0] & 0xff;
			return i;
		} else if (bytes.length == 4) {
			// 4-bytes signed int
			int i = bytes[3];
			i <<= 8;
			i |= bytes[2] & 0xff;
			i <<= 8;
			i |= bytes[1] & 0xff;
			i <<= 8;
			i |= bytes[0] & 0xff;
			return i;
		} else
			throw new PickleException("invalid amount of bytes to convert to int: " + bytes.length);
	}

	/**
	 * Convert a signed integer to its 4-byte representation. (little endian)
	 */
	public byte[] integer_to_bytes(int i) {
		final byte[] b = new byte[4];
		b[0] = (byte) (i & 0xff);
		i >>= 8;
		b[1] = (byte) (i & 0xff);
		i >>= 8;
		b[2] = (byte) (i & 0xff);
		i >>= 8;
		b[3] = (byte) (i & 0xff);
		return b;
	}

	/**
	 * Convert a double to its 8-byte representation (big endian).
	 */
	public byte[] double_to_bytes(double d) {
		long bits = Double.doubleToRawLongBits(d);
		final byte[] b = new byte[8];
		b[7] = (byte) (bits & 0xff);
		bits >>= 8;
		b[6] = (byte) (bits & 0xff);
		bits >>= 8;
		b[5] = (byte) (bits & 0xff);
		bits >>= 8;
		b[4] = (byte) (bits & 0xff);
		bits >>= 8;
		b[3] = (byte) (bits & 0xff);
		bits >>= 8;
		b[2] = (byte) (bits & 0xff);
		bits >>= 8;
		b[1] = (byte) (bits & 0xff);
		bits >>= 8;
		b[0] = (byte) (bits & 0xff);
		return b;
	}

	/**
	 * Convert a big endian 8-byte to a double. 
	 */
	public double bytes_to_double(byte[] bytes) {
		if (bytes.length == 8) {
			long result = bytes[0] & 0xff;
			result <<= 8;
			result |= bytes[1] & 0xff;
			result <<= 8;
			result |= bytes[2] & 0xff;
			result <<= 8;
			result |= bytes[3] & 0xff;
			result <<= 8;
			result |= bytes[4] & 0xff;
			result <<= 8;
			result |= bytes[5] & 0xff;
			result <<= 8;
			result |= bytes[6] & 0xff;
			result <<= 8;
			result |= bytes[7] & 0xff;
			return Double.longBitsToDouble(result);
		} else
			throw new PickleException("invalid amount of bytes to convert to double: " + bytes.length);
	}

	/**
	 * read an arbitrary 'long' number. Returns an int/long/BigInteger as appropriate to hold the number.
	 */
	public Number decode_long(byte[] data) {
		if (data.length == 0)
			return 0L;
		// first reverse the byte array because pickle stores it little-endian
		byte[] data2 = new byte[data.length];
		for (int i = 0; i < data.length; ++i)
			data2[data.length - i - 1] = data[i];
		BigInteger bigint = new BigInteger(data2);
		return optimizeBigint(bigint);
	}
	
	/**
	 * encode an arbitrary long number into a byte array (little endian).
	 */
	public byte[] encode_long(BigInteger big) {
		byte[] data=big.toByteArray();
		// reverse the byte array because pickle uses little endian
		byte[] data2=new byte[data.length];
		for (int i = 0; i < data.length; ++i)
			data2[data.length - i - 1] = data[i];
		return data2;
	}
	

	/**
	 * Optimize a biginteger, if possible return a long primitive datatype.
	 */
	public Number optimizeBigint(BigInteger bigint) {
		// final BigInteger MAXINT=BigInteger.valueOf(Integer.MAX_VALUE);
		// final BigInteger MININT=BigInteger.valueOf(Integer.MIN_VALUE);
		final BigInteger MAXLONG = BigInteger.valueOf(Long.MAX_VALUE);
		final BigInteger MINLONG = BigInteger.valueOf(Long.MIN_VALUE);
		switch (bigint.signum()) {
		case 0:
			return 0L;
		case 1: // >0
			// if(bigint.compareTo(MAXINT)<=0) return bigint.intValue();
			if (bigint.compareTo(MAXLONG) <= 0)
				return bigint.longValue();
			break;
		case -1: // <0
			// if(bigint.compareTo(MININT)>=0) return bigint.intValue();
			if (bigint.compareTo(MINLONG) >= 0)
				return bigint.longValue();
			break;
		}
		return bigint;
	}

	/**
	 * Construct a String from the given bytes where these are directly
	 * converted to the corresponding chars, without using a given character
	 * encoding
	 */
	public static String rawStringFromBytes(byte[] data) {
		StringBuilder str = new StringBuilder(data.length);
		for (byte b : data) {
			str.append((char) (b & 0xff));
		}
		return str.toString();
	}

	/**
	 * Convert a string to a byte array, no encoding is used. String must only contain characters <256.
	 */
	public static byte[] str2bytes(String str) throws IOException {
		byte[] b=new byte[str.length()];
		for(int i=0; i<str.length(); ++i) {
			char c=str.charAt(i);	
			if(c>255) throw new UnsupportedEncodingException("string contained a char > 255, cannot convert to bytes");
			b[i]=(byte)c;
		}
		return b;
	}

	/**
	 * Decode a string with possible escaped char sequences in it (\x??).
	 */
	public String decode_escaped(String str) {
		if(str.indexOf('\\')==-1)
			return str;
		StringBuilder sb=new StringBuilder(str.length());
		for(int i=0; i<str.length(); ++i) {
			char c=str.charAt(i);
			if(c=='\\') {
				// possible escape sequence
				char c2=str.charAt(++i);
				switch(c2) {
					case '\\':
						// double-escaped '\\'--> '\'
						sb.append(c);
						break;
					case 'x':
						// hex escaped "\x??"
						char h1=str.charAt(++i);
						char h2=str.charAt(++i);
						c2=(char)Integer.parseInt(""+h1+h2, 16);
						sb.append(c2);
						break;
					default:
						throw new PickleException("invalid escape sequence in string");
				}
			} else {
				sb.append(str.charAt(i));
			}
		}
        return sb.toString();
	}

	/**
	 * Decode a string with possible escaped unicode in it (\u20ac)
	 */
	public String decode_unicode_escaped(String str) {
		if(str.indexOf('\\')==-1)
			return str;
		StringBuilder sb=new StringBuilder(str.length());
		for(int i=0; i<str.length(); ++i) {
			char c=str.charAt(i);
			if(c=='\\') {
				// possible escape sequence
				char c2=str.charAt(++i);
				switch(c2) {
					case '\\':
						// double-escaped '\\'--> '\'
						sb.append(c);
						break;
					case 'u':
						// hex escaped unicode "\u20ac"
						char h1=str.charAt(++i);
						char h2=str.charAt(++i);
						char h3=str.charAt(++i);
						char h4=str.charAt(++i);
						c2=(char)Integer.parseInt(""+h1+h2+h3+h4, 16);
						sb.append(c2);
						break;
					default:
						throw new PickleException("invalid escape sequence in string");
				}
			} else {
				sb.append(str.charAt(i));
			}
		}
        return sb.toString();
	}
}
