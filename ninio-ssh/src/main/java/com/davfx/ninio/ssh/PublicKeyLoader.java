package com.davfx.ninio.ssh;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.DSAPublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.DSAPublicKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.List;

import com.google.common.base.Splitter;
import com.google.common.io.BaseEncoding;

public final class PublicKeyLoader {
	private PublicKeyLoader() {
	}
	
	private static byte[] loadPrivate(String privateKey) {
		try {
			return Files.readAllBytes(new File("/Users/davidfauthoux/.ssh/private_key.der").toPath());
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
		//return BaseEncoding.base64().decode("MIIEowIBAAKCAQEAo+jgK2r7KbZuEuGZiEM8LtP6E8TSBau0c+eUh3K7gwNYHrlxO/xKykg2bNaerGI6hWbqEl2na2sls5IM0d/b0WbpGPrcQ7GEaHe8SWjekXOvktsZQ7fZh7TCCGybx2dRty/GuVJLCeb3j/Ysf7Ue3Ds7saI1BlrVeeYu6K8qKC+8OLzHraJdiTNGtFfgN+N+o9b1zrJxujrMqS3wwRSvmj0AOdhMHNW6NHeJLnE+gHmEzTPBEaNPblThsy24I6cD4SsTkDndXw2p7+MlVv/MMHMjJdlrrz00PPN/Z0Ik+bh07t3RN5NJqMPc0IPuWd8yPOs/MYf3zF61Qn0HJB4f1wIDAQABAoIBAAvxQLwJHGmqoBSCGXSjKXRj/3mHQqIlI426VskTo/Pkv3vlmQl81VotrsF5VbBLq+XiXLWf2z3pXaLaxlJYVCTKsbsfNAM+oN4Xs0FKAcVpP2aczhdwAspvch+5AhSiQ4LsWTdRdqQvDuSPVCit4qS7MBs+LUzcO2eZTNupP5IT4mdztqDl+Ny8Wj5cTBMgC6P2Y+I0GKqQrjzmgwh3Ctut8Jta7p33itn5p1vgTvEK5NfndV8d5LiALJF+wk1/KgVgrh8I/PqeQvK3ILAhiljagEpBjbbyn3wYmysFUhl7xu39drzas9q3cV6OaWjM/m4R6kIhu8QYz69+WorhMGkCgYEA2Aj2cMf0MATHl3C+O6851UjzFs1WBB6KS5+2IDGktpYANNO7MWpzWpYUkZX1Iq2OPZWtPFmYb2QKNKlh2B6p54efyEnUSG2+M7IOPl2CXYk+sqwphUETzIE5YPN6yqDrQwPS+VCyEwIFm1C1b+Psb+OPNFq/xBWML0yLqIbPoHMCgYEAwjtZSYPOJMtI8d3CyzgvD+pbD2K4+G3iv+S0BnEyI94u8nd0jEDueQ6SEx/EY2C7rSUsfPfGC5sokxuGS/tCguGutIgetIqhIxb1Xq10gcWOxNIn/mv/x1buN1k23FBx0Sjxmz++6sRgABeKusNzT0sGx9/XzwnxyoPMslhEng0CgYA32cFUkO0ZSPMMvNIBfiCWemCWIYm+UOLyAB1Gn2704Ty2a5K2iM2/upMgvEL+rx3Z4AtEUeltytA0oyGvjOXh/JcbYbLm8rA8jyjNiME+S1ARQx8M2zEyKqpZ3Th+kDGiRqfRWsJe1aP7blcp0SP8HTmVkGyJVgTC6aglbozY7wKBgQCvhaJyZMHtTidlKtnVe7hL0aToinZNSkAW2T42dCSzdR1Hz9tqw2K90wT+cz6t78Sp+2XwqJg39Mb96Wm7UBwS2o9eZYQZ8w0bAKxMGsOmYjlac+/gYwiJw20SZ3TEM44nTbDIcxq8XSnD64JatDWH+mzuQsJrPrlaITDiGhIoIQKBgA/pPPac3DwK1p+ZGIHFfpNmjc//XP/sJVhkFqJgoHC3aRDL6z9SsU2m3d4vjC77tlemY/DlqC4y4i1JCDY0Lz1kBeJ9i7tfQ2/+NXJRPRU5i6Avh7D2xz4Tt4H+2xeN6Huc0UYWnayYR7Ml4fdfVxc5Ed3pfIWOFjLjlQQ9lKml");
		/*
		List<String> l = Splitter.on('\n').splitToList(privateKey);
		if (l..equals(publicKeyAlgorithm)) {
			throw new IOException("Invalid algorithm: " + s);
		}
		*/
		
	}

	public static SshPublicKey load(String privateKey, String publicKey) throws IOException {
		List<String> l = Splitter.on(' ').splitToList(publicKey);
		String publicKeyAlgorithm = l.get(0);
		byte[] b = BaseEncoding.base64().decode(l.get(1));
		SshPacket k = new SshPacket(ByteBuffer.wrap(b));
		String s = k.readString();
		if (!s.equals(publicKeyAlgorithm)) {
			throw new IOException("Invalid algorithm: " + s);
		}
		if (publicKeyAlgorithm.equals("ssh-rsa")) {
			byte[] e = k.readBlob();
			byte[] n = k.readBlob();
			try {
				KeyFactory keyFactory = KeyFactory.getInstance("RSA");
				SshPacket p = new SshPacket(ByteBuffer.wrap(loadPrivate(privateKey)));
				return new RsaSshPublicKey((RSAPrivateKey) keyFactory.generatePrivate(new PKCS8EncodedKeySpec(loadPrivate(privateKey))), (RSAPublicKey) keyFactory.generatePublic(new RSAPublicKeySpec(new BigInteger(n), new BigInteger(e))));
			} catch (GeneralSecurityException se) {
				throw new IOException(se);
			}
		} else if (publicKeyAlgorithm.equals("ssh-dss")) {
			byte[] p = k.readBlob();
			byte[] q = k.readBlob();
			byte[] g = k.readBlob();
			byte[] y = k.readBlob();
			try {
				KeyFactory keyFactory = KeyFactory.getInstance("DSA");
				return new DssSshPublicKey((DSAPublicKey) keyFactory.generatePublic(new DSAPublicKeySpec(new BigInteger(y), new BigInteger(p), new BigInteger(q), new BigInteger(g))));
			} catch (GeneralSecurityException se) {
				throw new IOException(se);
			}
		} else {
			throw new IOException("Unknown algorithm: " + publicKeyAlgorithm);
		}
		// l.get(2) ignored (comment)
	}
}
