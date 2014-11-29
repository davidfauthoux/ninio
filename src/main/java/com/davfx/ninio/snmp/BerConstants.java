package com.davfx.ninio.snmp;


public final class BerConstants {

	public static final int ERROR_STATUS_RETRY = -1;
	public static final int ERROR_STATUS_AUTHENTICATION_FAILED = -2;

	public static final int VERSION_2C = 1;
	public static final int VERSION_3 = 3;
	
	public static final int VERSION_3_USM_SECURITY_MODEL = 3;
	public static final int VERSION_3_AUTH_FLAG = 0x01;
	public static final int VERSION_3_PRIV_FLAG = 0x02;
	public static final int VERSION_3_REPORTABLE_FLAG = 0x04;

	public static final int ASN_CONSTRUCTOR = 0x20;
	public static final int ASN_APPLICATION = 0x40;
	public static final int ASN_CONTEXT = 0x80;
	public static final int ASN_BIT8 = 0x80;

	public static final int INTEGER = 0x02;
	// public static final int BITSTRING = 0x03;
	public static final int OCTETSTRING = 0x04;
	public static final int NULL = 0x05;
	public static final int OID = 0x06;
	public static final int IPADDRESS = ASN_APPLICATION | 0x00;
	public static final int COUNTER32 = ASN_APPLICATION | 0x01;
	public static final int GAUGE32 = ASN_APPLICATION | 0x02;
	public static final int TIMETICKS = ASN_APPLICATION | 0x03;
	public static final int OPAQUE = ASN_APPLICATION | 0x04;
	// public static final int NSAPADDRESS = ASN_APPLICATION | 0x05;
	public static final int COUNTER64 = ASN_APPLICATION | 0x06;
	public static final int UNSIGNEDINTEGER32 = ASN_APPLICATION | 0x07;
	public static final int SEQUENCE = ASN_CONSTRUCTOR | 0x10;

	private static final int OPAQUE_TAG = 0x30;
	public static final int OPAQUE_FLOAT = (ASN_APPLICATION | 0x08) + OPAQUE_TAG;
	public static final int OPAQUE_DOUBLE = (ASN_APPLICATION | 0x09) + OPAQUE_TAG;
	public static final int OPAQUE_INTEGER64 = (ASN_APPLICATION | 0x0A) + OPAQUE_TAG;
	public static final int OPAQUE_UNSIGNEDINTEGER64 = (ASN_APPLICATION | 0x0B) + OPAQUE_TAG;

	public static final int GET = (ASN_CONTEXT | ASN_CONSTRUCTOR | 0x0);
	public static final int GETNEXT = (ASN_CONTEXT | ASN_CONSTRUCTOR | 0x1);
	public static final int RESPONSE = (ASN_CONTEXT | ASN_CONSTRUCTOR | 0x2);
	public static final int REPORT = (ASN_CONTEXT | ASN_CONSTRUCTOR | 0x8);
	public static final int GETBULK = (ASN_CONTEXT | ASN_CONSTRUCTOR | 0x5);

	/*%%%%%%%%%%%%
	 * Not used for now public static final int NOSUCHOBJECT = 0x80; public static final int NOSUCHINSTANCE = 0x81; public static final int ENDOFMIBVIEW = 0x82;
	 * public static final int VERSION_2C = 1;
	 * 
	 * //public static final int ASN_BOOLEAN = 0x01; public static final int ASN_INTEGER = 0x02; public static final int ASN_BITSTRING = 0x03; public static final int ASN_OCTETSTRING = 0x04; public static final int ASN_NULL = 0x05; public static final int ASN_OID = 0x06; //public static final int ASN_SEQUENCE = 0x10; //public static final int ASN_SET = 0x11;
	 * 
	 * //public static final int ASN_UNIVERSAL = 0x00; public static final int ASN_APPLICATION = 0x40;
	 * 
	 * public static final int ASN_CONTEXT = 0x80; public static final int ASN_PRIVATE = 0xC0; public static final int ASN_PRIMITIVE = 0x00; public static final int ASN_CONSTRUCTOR = 0x20;
	 * 
	 * //public static final int ASN_LONG_LEN = 0x80; //public static final int ASN_EXTENSION_ID = 0x1F;
	 * 
	 * /* public static final int INTEGER = ASN_UNIVERSAL | 0x02; public static final int INTEGER32 = ASN_UNIVERSAL | 0x02; public static final int BITSTRING = ASN_UNIVERSAL | 0x03; public static final int OCTETSTRING = ASN_UNIVERSAL | 0x04; public static final int NULL = ASN_UNIVERSAL | 0x05; public static final int OID = ASN_UNIVERSAL | 0x06; / public static final int SEQUENCE = ASN_CONSTRUCTOR | 0x10;
	 * 
	 * public static final int IPADDRESS = ASN_APPLICATION | 0x00; //public static final int COUNTER = ASN_APPLICATION | 0x01; public static final int COUNTER32 = ASN_APPLICATION | 0x01; //public static final int GAUGE = ASN_APPLICATION | 0x02; public static final int GAUGE32 = ASN_APPLICATION | 0x02; public static final int TIMETICKS = ASN_APPLICATION | 0x03; public static final int OPAQUE = ASN_APPLICATION | 0x04; public static final int NSAPADDRESS = ASN_APPLICATION | 0x05; public static final int COUNTER64 = ASN_APPLICATION | 0x06; public static final int UNSIGNEDINTEGER32 = ASN_APPLICATION | 0x07;
	 * 
	 * public static final int ASN_BIT8 = 0x80; public static final int NOSUCHOBJECT = 0x80; public static final int NOSUCHINSTANCE = 0x81; public static final int ENDOFMIBVIEW = 0x82;
	 * 
	 * //private static final int LENMASK = 0x0ff; //public static final int MAX_OID_LENGTH = 127;
	 * 
	 * public static final int GET = (ASN_CONTEXT | ASN_CONSTRUCTOR | 0x0); public static final int GETNEXT = (ASN_CONTEXT | ASN_CONSTRUCTOR | 0x1); public static final int RESPONSE = (ASN_CONTEXT | ASN_CONSTRUCTOR | 0x2); public static final int GETBULK = (ASN_CONTEXT | ASN_CONSTRUCTOR | 0x5);
	 */
}
