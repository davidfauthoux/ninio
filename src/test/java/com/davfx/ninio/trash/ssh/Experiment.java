package com.davfx.ninio.trash.ssh;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import com.google.common.base.Charsets;

public class Experiment {
	private static final Random random = new Random();
	public static void main(String[] args) throws Exception {
		Socket socket = new Socket("172.17.10.31", 22);
		OutputStream out = socket.getOutputStream();
		out.write("SSH-2.0-JSCH-0.1.51\n".getBytes(Charsets.UTF_8));
		out.flush();
		DataInputStream in = new DataInputStream(socket.getInputStream());
		while (true) {
			int r = in.read();
			if (r < 0) {
				throw new IOException();
			}
			System.out.print((char) r);
			if (r == '\n') {
				break;
			}
		}

		DH dh = new DH();
		dh.init();
		byte[] e;

	      AES128CTR s2ccipher= new AES128CTR();
	      AES128CTR c2scipher= new AES128CTR();
	      HMACMD5 s2cmac= new HMACMD5();
	      HMACMD5 c2smac= new HMACMD5();
	      int s2ccipher_size;
	      int c2scipher_size;
		     int s2cmac_size = s2cmac.getBlockSize();
		     int c2smac_size = c2smac.getBlockSize();

				byte[] I_C;

			    List<String> I_S = new LinkedList<>();
			    byte[] serverCookie = new byte[16];

		byte[] cookie = new byte[16];
		random.nextBytes(cookie);
		//%% try (BufferedReader r = new BufferedReader(new InputStreamReader(socket.getInputStream(), Charsets.UTF_8))) { //TODO charset?
			while (true) {
				/*%%%
				String line = r.readLine();
				if (line == null) {
					break;
				}
				System.out.println("line="+line);// SSH-2.0-OpenSSH_5.9p1 Debian-5ubuntu1.4
				*/
				
				{
			    // byte      SSH_MSG_KEXINIT(20)
			    // byte[16]  cookie (random bytes)
			    // string    kex_algorithms //diffie-hellman-group1-sha1,diffie-hellman-group14-sha1,diffie-hellman-group-exchange-sha1
			    // string    server_host_key_algorithms //ssh-rsa,ssh-dss
			    // string    encryption_algorithms_client_to_server //aes128-ctr,aes128-cbc,3des-ctr,3des-cbc,blowfish-cbc
			    // string    encryption_algorithms_server_to_client 
			    // string    mac_algorithms_client_to_server //hmac-md5,hmac-sha1,hmac-sha2-256,hmac-sha1-96,hmac-md5-96
			    // string    mac_algorithms_server_to_client
			    // string    compression_algorithms_client_to_server // none
			    // string    compression_algorithms_server_to_client
			    // string    languages_client_to_server // string vide
			    // string    languages_server_to_client
				ByteBuffer b = prepare();
				
				/*SERVER
				ecdh-sha2-nistp256,ecdh-sha2-nistp384,ecdh-sha2-nistp521,diffie-hellman-group-exchange-sha256,diffie-hellman-group-exchange-sha1,diffie-hellman-group14-sha1,diffie-hellman-group1-sha1
				ssh-rsa,ssh-dss,ecdsa-sha2-nistp256
				aes128-ctr,aes192-ctr,aes256-ctr,arcfour256,arcfour128,aes128-cbc,3des-cbc,blowfish-cbc,cast128-cbc,aes192-cbc,aes256-cbc,arcfour,rijndael-cbc@lysator.liu.se
				aes128-ctr,aes192-ctr,aes256-ctr,arcfour256,arcfour128,aes128-cbc,3des-cbc,blowfish-cbc,cast128-cbc,aes192-cbc,aes256-cbc,arcfour,rijndael-cbc@lysator.liu.se
				hmac-md5,hmac-sha1,umac-64@openssh.com,hmac-sha2-256,hmac-sha2-256-96,hmac-sha2-512,hmac-sha2-512-96,hmac-ripemd160,hmac-ripemd160@openssh.com,hmac-sha1-96,hmac-md5-96
				hmac-md5,hmac-sha1,umac-64@openssh.com,hmac-sha2-256,hmac-sha2-256-96,hmac-sha2-512,hmac-sha2-512-96,hmac-ripemd160,hmac-ripemd160@openssh.com,hmac-sha1-96,hmac-md5-96
				none,zlib@openssh.com
				none,zlib@openssh.com

				 */
				
				int pp = b.position();
				b.put((byte) 20);
				b.put(cookie);
				putString(b, "diffie-hellman-group1-sha1,diffie-hellman-group14-sha1,diffie-hellman-group-exchange-sha1");
				putString(b, "ssh-rsa,ssh-dss");
				putString(b, "aes128-ctr,aes128-cbc,3des-ctr,3des-cbc,blowfish-cbc");
				putString(b, "aes128-ctr,aes128-cbc,3des-ctr,3des-cbc,blowfish-cbc");
				putString(b, "hmac-md5,hmac-sha1,hmac-sha2-256,hmac-sha1-96,hmac-md5-96");
				putString(b, "hmac-md5,hmac-sha1,hmac-sha2-256,hmac-sha1-96,hmac-md5-96");
				putString(b, "none");
				putString(b, "none");
				putString(b, "");
				putString(b, "");
			    b.put((byte) 0);
			    b.putInt(0);
				I_C = new byte[b.position() - pp];
				System.arraycopy(b.array(), pp, I_C, 0, b.position() - pp);

			    /*
			    buf.setOffSet(5);
			    I_C=new byte[buf.getLength()];
			    buf.getByte(I_C);
			    */
			    
			    finish(b);
			    out.write(b.array(), 0, b.remaining());
				  for (int i = 0; i < b.remaining(); i++) {
						System.out.print(b.array()[i] + ",");
						if ((i % 50) == 0) {
							System.out.println();
						}
					}
				  System.out.println();
				  System.out.println();
			    out.flush();
			}
			  {
				    ByteBuffer bb = read(in);
				    System.out.println("----------------------- " + bb.remaining());
				    int command = bb.get();
				    System.out.println("command=" + command + " len=" + bb.remaining());
				    bb.get(serverCookie);
				    
				    I_S.add(getString(bb));
				    I_S.add(getString(bb));
				    I_S.add(getString(bb));
				    I_S.add(getString(bb));
				    I_S.add(getString(bb));
				    I_S.add(getString(bb));
				    I_S.add(getString(bb));
				    I_S.add(getString(bb));
				    I_S.add(getString(bb));
				    I_S.add(getString(bb));
				    
				    byte[] more = new byte[14];
				    //System.out.println("----------");
				    //in.readFully(more);
				    bb.get(more); //TODO check all zero??
				    System.out.println("---------- " + bb.remaining());
			    }
			  {
					ByteBuffer b = prepare();

					b.put((byte) 30);

					//DHG1
					  byte[] g={ 2 };
					  byte[] p={
					(byte)0x00,
					(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF, 
					(byte)0xC9,(byte)0x0F,(byte)0xDA,(byte)0xA2,(byte)0x21,(byte)0x68,(byte)0xC2,(byte)0x34,
					(byte)0xC4,(byte)0xC6,(byte)0x62,(byte)0x8B,(byte)0x80,(byte)0xDC,(byte)0x1C,(byte)0xD1,
					(byte)0x29,(byte)0x02,(byte)0x4E,(byte)0x08,(byte)0x8A,(byte)0x67,(byte)0xCC,(byte)0x74,
					(byte)0x02,(byte)0x0B,(byte)0xBE,(byte)0xA6,(byte)0x3B,(byte)0x13,(byte)0x9B,(byte)0x22,
					(byte)0x51,(byte)0x4A,(byte)0x08,(byte)0x79,(byte)0x8E,(byte)0x34,(byte)0x04,(byte)0xDD,
					(byte)0xEF,(byte)0x95,(byte)0x19,(byte)0xB3,(byte)0xCD,(byte)0x3A,(byte)0x43,(byte)0x1B,
					(byte)0x30,(byte)0x2B,(byte)0x0A,(byte)0x6D,(byte)0xF2,(byte)0x5F,(byte)0x14,(byte)0x37,
					(byte)0x4F,(byte)0xE1,(byte)0x35,(byte)0x6D,(byte)0x6D,(byte)0x51,(byte)0xC2,(byte)0x45,
					(byte)0xE4,(byte)0x85,(byte)0xB5,(byte)0x76,(byte)0x62,(byte)0x5E,(byte)0x7E,(byte)0xC6,
					(byte)0xF4,(byte)0x4C,(byte)0x42,(byte)0xE9,(byte)0xA6,(byte)0x37,(byte)0xED,(byte)0x6B,
					(byte)0x0B,(byte)0xFF,(byte)0x5C,(byte)0xB6,(byte)0xF4,(byte)0x06,(byte)0xB7,(byte)0xED,
					(byte)0xEE,(byte)0x38,(byte)0x6B,(byte)0xFB,(byte)0x5A,(byte)0x89,(byte)0x9F,(byte)0xA5,
					(byte)0xAE,(byte)0x9F,(byte)0x24,(byte)0x11,(byte)0x7C,(byte)0x4B,(byte)0x1F,(byte)0xE6,
					(byte)0x49,(byte)0x28,(byte)0x66,(byte)0x51,(byte)0xEC,(byte)0xE6,(byte)0x53,(byte)0x81,
					(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF
					};

				    dh.setP(p);
				    dh.setG(g);

				    // The client responds with:
				    // byte  SSH_MSG_KEXDH_INIT(30)
				    // mpint e <- g^x mod p
				    //         x is a random number (1 < x < (p-1)/2)

				    e=dh.getE();
				    putMPInt(b, e);

					finish(b);

				    out.write(b.array(), 0, b.remaining());
					  for (int i = 0; i < b.remaining(); i++) {
							System.out.print(b.array()[i] + ",");
							if ((i % 50) == 0) {
								System.out.println();
							}
						}
					  System.out.println();
					  System.out.println();
				    out.flush();

			  }
			  {
				    ByteBuffer bb = read(in);
				    int command = bb.get();
				    System.out.println("command=" + command + "[31] len=" + bb.remaining());
				    
				    
				      // The server responds with:
				      // byte      SSH_MSG_KEXDH_REPLY(31)
				      // string    server public host key and certificates (K_S)
				      // mpint     f
				      // string    signature of H
				     //%%%%%%%% j=_buf.getInt();
				    //  int j=bb.get();
				     // j=bb.get();

				      byte[] K_S=getBlob(bb);
				      // K_S is server_key_blob, which includes ....
				      // string ssh-dss
				      // impint p of dsa
				      // impint q of dsa
				      // impint g of dsa
				      // impint pub_key of dsa
				     // System.out.print("K_S: " + K_S); //dump(K_S, 0, K_S.length);
				      byte[] f=getMPInt(bb);
				      byte[] sig_of_H=getBlob(bb);
				      /*
				for(int ii=0; ii<sig_of_H.length;ii++){
				  System.err.print(Integer.toHexString(sig_of_H[ii]&0xff));
				  System.err.print(": ");
				}
				System.err.println("");
				      */

				      dh.setF(f);
				      byte[] K=normalize(dh.getK());

				      ByteBuffer h = ByteBuffer.wrap(new byte[100_000]);
				      putString(h, "SSH-2.0-JSCH-0.1.51");
				      putString(h, "SSH-2.0-OpenSSH_5.9p1 Debian-5ubuntu1.4");

				      h.putInt(392);
					    int ppp = h.position();
						h.put((byte) 20);
						h.put(cookie);
						putString(h, "diffie-hellman-group1-sha1,diffie-hellman-group14-sha1,diffie-hellman-group-exchange-sha1");
						putString(h, "ssh-rsa,ssh-dss");
						putString(h, "aes128-ctr,aes128-cbc,3des-ctr,3des-cbc,blowfish-cbc");
						putString(h, "aes128-ctr,aes128-cbc,3des-ctr,3des-cbc,blowfish-cbc");
						putString(h, "hmac-md5,hmac-sha1,hmac-sha2-256,hmac-sha1-96,hmac-md5-96");
						putString(h, "hmac-md5,hmac-sha1,hmac-sha2-256,hmac-sha1-96,hmac-md5-96");
						putString(h, "none");
						putString(h, "none");
						putString(h, "");
						putString(h, "");
					    h.put((byte) 0);
					    h.putInt(0);
					    ppp = h.position() - ppp;
						
					      h.putInt(970);
					    ppp = h.position();
						h.put((byte) 20);
						h.put(serverCookie);
						for (String s : I_S) {
							putString(h, s);
						}
						h.put(new byte[5]);
					    ppp = h.position() - ppp;

					h.putInt(K_S.length);
					h.put(K_S);
					putMPInt(h, e);
					putMPInt(h, f);
					putMPInt(h, K);

					SHA1 sha = new SHA1();
					sha.init();
				      sha.update(h.array(), 0, h.position());
				      byte[] H=sha.digest();
					
				      ByteBuffer ksb = ByteBuffer.wrap(K_S);
				      String alg = getString(ksb);
				      System.out.println("alg="+alg);
				      
						byte[] ee = getBlob(ksb);
						byte[] n = getBlob(ksb);

						/*TODO
						sig.setPubKey(ee, n);   
						sig.update(H);
						result=sig.verify(sig_of_H);
						*/
						
						Signature signature=java.security.Signature.getInstance("SHA1withRSA");
						KeyFactory keyFactory=KeyFactory.getInstance("RSA");
					    RSAPublicKeySpec rsaPubKeySpec = 
					    		new RSAPublicKeySpec(new BigInteger(n),
					    				     new BigInteger(ee));
					    	    PublicKey pubKey=keyFactory.generatePublic(rsaPubKeySpec);
					    	    signature.initVerify(pubKey);
					    	    signature.update(H);
					    	    
					    	    int i=0;
					    	    int j=0;
					    	    byte[] tmp;

					    	    byte[] sig = sig_of_H;
					    	    if(sig[0]==0 && sig[1]==0 && sig[2]==0){
					    	    j=((sig[i++]<<24)&0xff000000)|((sig[i++]<<16)&0x00ff0000)|
					    		((sig[i++]<<8)&0x0000ff00)|((sig[i++])&0x000000ff);
					    	    i+=j;
					    	    j=((sig[i++]<<24)&0xff000000)|((sig[i++]<<16)&0x00ff0000)|
					    		((sig[i++]<<8)&0x0000ff00)|((sig[i++])&0x000000ff);
					    	    tmp=new byte[j]; 
					    	    System.arraycopy(sig, i, tmp, 0, j); sig=tmp;
					    	    }

					    	    System.out.println("VERIF = "+ signature.verify(sig));


						//ICIICI
						
					      byte[] session_id=new byte[H.length];
					      System.arraycopy(H, 0, session_id, 0, H.length);

					    /*
					      Initial IV client to server:     HASH (K || H || "A" || session_id)
					      Initial IV server to client:     HASH (K || H || "B" || session_id)
					      Encryption key client to server: HASH (K || H || "C" || session_id)
					      Encryption key server to client: HASH (K || H || "D" || session_id)
					      Integrity key client to server:  HASH (K || H || "E" || session_id)
					      Integrity key server to client:  HASH (K || H || "F" || session_id)
					    */

					      ByteBuffer buf = ByteBuffer.allocate(100_000);
					   putMPInt(buf, K);
					    buf.put(H);
					    buf.put((byte)0x41);
					    buf.put(session_id);
					    sha.update(buf.array(), 0, buf.position());
					    byte[] IVc2s=sha.digest();
					    //random.nextBytes(IVc2s);

					      buf = ByteBuffer.allocate(100_000);
					   putMPInt(buf, K);
					    buf.put(H);
					    buf.put((byte)0x42);
					    buf.put(session_id);
					    sha.update(buf.array(), 0, buf.position());
					    byte[] IVs2c=sha.digest();

					      buf = ByteBuffer.allocate(100_000);
					   putMPInt(buf, K);
					    buf.put(H);
					    buf.put((byte)0x43);
					    buf.put(session_id);
					    sha.update(buf.array(), 0, buf.position());
					    byte[] Ec2s=sha.digest();

					      buf = ByteBuffer.allocate(100_000);
					   putMPInt(buf, K);
					    buf.put(H);
					    buf.put((byte)0x44);
					    buf.put(session_id);
					    sha.update(buf.array(), 0, buf.position());
					    byte[] Es2c=sha.digest();

					      buf = ByteBuffer.allocate(100_000);
					   putMPInt(buf, K);
					    buf.put(H);
					    buf.put((byte)0x45);
					    buf.put(session_id);
					    sha.update(buf.array(), 0, buf.position());
					    byte[] MACc2s=sha.digest();

					      buf = ByteBuffer.allocate(100_000);
					   putMPInt(buf, K);
					    buf.put(H);
					    buf.put((byte)0x46);
					    buf.put(session_id);
					    sha.update(buf.array(), 0, buf.position());
					    byte[] MACs2c=sha.digest();

					      while(s2ccipher.getBlockSize()>Es2c.length){
						      buf = ByteBuffer.allocate(100_000);
							   putMPInt(buf, K);
							    buf.put(H);
							    buf.put(Es2c);
							    sha.update(buf.array(), 0, buf.position());
							    byte[] foo=sha.digest();

					        byte[] bar=new byte[Es2c.length+foo.length];
						System.arraycopy(Es2c, 0, bar, 0, Es2c.length);
						System.arraycopy(foo, 0, bar, Es2c.length, foo.length);
						Es2c=bar;
					      }
					      
					      s2ccipher.init(1/*DECRYPT_MODE*/, Es2c, IVs2c);
					      s2ccipher_size=s2ccipher.getIVSize();

					      MACs2c = expandKey(K, H, MACs2c, sha, s2cmac.getBlockSize());
					      s2cmac.init(MACs2c);
					      //mac_buf=new byte[s2cmac.getBlockSize()];
					      byte[] s2cmac_result1=new byte[s2cmac.getBlockSize()];
					      byte[] s2cmac_result2=new byte[s2cmac.getBlockSize()];

					      while(c2scipher.getBlockSize()>Ec2s.length){
						      buf = ByteBuffer.allocate(100_000);
							   putMPInt(buf, K);
							    buf.put(H);
							    buf.put(Ec2s);
							    sha.update(buf.array(), 0, buf.position());
							    byte[] foo=sha.digest();

					        byte[] bar=new byte[Ec2s.length+foo.length];
						System.arraycopy(Ec2s, 0, bar, 0, Ec2s.length);
						System.arraycopy(foo, 0, bar, Ec2s.length, foo.length);
						Ec2s=bar;
					      }
					      
					      c2scipher.init(0/*ENCRYPT_MODE*/, Ec2s, IVc2s);
					      c2scipher_size=c2scipher.getIVSize();

					      MACc2s = expandKey(K, H, MACc2s, sha, c2smac.getBlockSize());
					      c2smac.init(MACc2s);

					      /*
					      method=guess[KeyExchange.PROPOSAL_COMP_ALGS_CTOS];
					      initDeflater(method);

					      method=guess[KeyExchange.PROPOSAL_COMP_ALGS_STOC];
					      initInflater(method);
					      */
				      /*
				      //The hash H is computed as the HASH hash of the concatenation of the
				      //following:
				      // string    V_C, the client's version string (CR and NL excluded)
				      // string    V_S, the server's version string (CR and NL excluded)
				      // string    I_C, the payload of the client's SSH_MSG_KEXINIT
				      // string    I_S, the payload of the server's SSH_MSG_KEXINIT
				      // string    K_S, the host key
				      // mpint     e, exchange value sent by the client
				      // mpint     f, exchange value sent by the server
				      // mpint     K, the shared secret
				      // This value is called the exchange hash, and it is used to authenti-
				      // cate the key exchange.
				      buf.reset();
				      buf.putString(V_C); buf.putString(V_S);
				      buf.putString(I_C); buf.putString(I_S);
				      buf.putString(K_S);
				      buf.putMPInt(e); buf.putMPInt(f);
				      buf.putMPInt(K);
				      byte[] foo=new byte[buf.getLength()];
				      buf.getByte(foo);
				      sha.update(foo, 0, foo.length);
				      H=sha.digest();
				      //System.err.print("H -> "); //dump(H, 0, H.length);

				      if(alg.equals("ssh-rsa")){
					byte[] tmp;
					byte[] ee;
					byte[] n;

					type=RSA;

					j=((K_S[i++]<<24)&0xff000000)|((K_S[i++]<<16)&0x00ff0000)|
					  ((K_S[i++]<<8)&0x0000ff00)|((K_S[i++])&0x000000ff);
					tmp=new byte[j]; System.arraycopy(K_S, i, tmp, 0, j); i+=j;
					ee=tmp;
					j=((K_S[i++]<<24)&0xff000000)|((K_S[i++]<<16)&0x00ff0000)|
					  ((K_S[i++]<<8)&0x0000ff00)|((K_S[i++])&0x000000ff);
					tmp=new byte[j]; System.arraycopy(K_S, i, tmp, 0, j); i+=j;
					n=tmp;
					
//					SignatureRSA sig=new SignatureRSA();
//					sig.init();

					SignatureRSA sig=null;
					try{
					  Class c=Class.forName(session.getConfig("signature.rsa"));
					  sig=(SignatureRSA)(c.newInstance());
					  sig.init();
					}
					catch(Exception e){
					  System.err.println(e);
					}

					sig.setPubKey(ee, n);   
					sig.update(H);
					result=sig.verify(sig_of_H);

				        if(JSch.getLogger().isEnabled(Logger.INFO)){
				          JSch.getLogger().log(Logger.INFO, 
				                               "ssh_rsa_verify: signature "+result);
				        }

				      }
				      else if(alg.equals("ssh-dss")){
					byte[] q=null;
					byte[] tmp;
					byte[] p;
					byte[] g;
				      
					type=DSS;

					j=((K_S[i++]<<24)&0xff000000)|((K_S[i++]<<16)&0x00ff0000)|
					  ((K_S[i++]<<8)&0x0000ff00)|((K_S[i++])&0x000000ff);
					tmp=new byte[j]; System.arraycopy(K_S, i, tmp, 0, j); i+=j;
					p=tmp;
					j=((K_S[i++]<<24)&0xff000000)|((K_S[i++]<<16)&0x00ff0000)|
					  ((K_S[i++]<<8)&0x0000ff00)|((K_S[i++])&0x000000ff);
					tmp=new byte[j]; System.arraycopy(K_S, i, tmp, 0, j); i+=j;
					q=tmp;
					j=((K_S[i++]<<24)&0xff000000)|((K_S[i++]<<16)&0x00ff0000)|
					  ((K_S[i++]<<8)&0x0000ff00)|((K_S[i++])&0x000000ff);
					tmp=new byte[j]; System.arraycopy(K_S, i, tmp, 0, j); i+=j;
					g=tmp;
					j=((K_S[i++]<<24)&0xff000000)|((K_S[i++]<<16)&0x00ff0000)|
					  ((K_S[i++]<<8)&0x0000ff00)|((K_S[i++])&0x000000ff);
					tmp=new byte[j]; System.arraycopy(K_S, i, tmp, 0, j); i+=j;
					f=tmp;
//					SignatureDSA sig=new SignatureDSA();
//					sig.init();
					SignatureDSA sig=null;
					try{
					  Class c=Class.forName(session.getConfig("signature.dss"));
					  sig=(SignatureDSA)(c.newInstance());
					  sig.init();
					}
					catch(Exception e){
					  System.err.println(e);
					}
					sig.setPubKey(f, p, q, g);   
					sig.update(H);
					result=sig.verify(sig_of_H);
*/
				    
			    }
			  
			  //A METTRE AVANT "//ICIICI"
			  {
					ByteBuffer b = prepare();
					b.put((byte) 21);
					finish(b);
				    out.write(b.array(), 0, b.remaining());
				    out.flush();
			  }
			  {
				    ByteBuffer bb = read(in);
				    int command = bb.get();
				    System.out.println("command=" + command + "[21] len=" + bb.remaining());
			  }
			  //fin A METTRE AVANT "//ICIICI"
			  
			  /*
			  for (int k = 0; k < 10; k++) {
				  System.out.println("--- " + k);
				  in.readByte();
			  }
			  */

			  {
					ByteBuffer b = prepare();
				    b.put((byte) 5); //Session.SSH_MSG_SERVICE_REQUEST);
				    putString(b, "ssh-userauth");
					finish(b);
					
				      c2smac.update(3/*seqo*/);
				      c2smac.update(b.array(), 0, b.remaining());
				      byte[] cb = new byte[c2smac.getBlockSize()];
				      c2smac.doFinal(cb, 0);

				      byte[] sb = new byte[100_000];
				      int sbl = c2scipher.update(b.array(), 0, b.remaining(), sb, 0);
				      System.out.println("---------- sbl="+sbl);
				      b = ByteBuffer.wrap(sb, 0, sbl + cb.length);
				      b.position(sbl);
				      b.put(cb);
				      b.flip();
					
				    out.write(b.array(), 0, b.remaining());
				    out.flush();
			  }

			  System.out.println("---");
			 // while (true)
			  {
				  
				  
				    	byte[] b = new byte[s2ccipher_size + 100_000];
				    	in.readFully(b, 0, s2ccipher_size);
				    	/*%%
					    int len = 0;
					    for (int kk = 0; kk < 10; kk++) {
						    len= bb.getInt();
						    System.out.println("len="+ len + " s2ccipher_size=" + s2ccipher_size);
					    }*/

				        s2ccipher.update(b, 0, s2ccipher_size, b, 0);

				       // System.out.println("---> " + new String(b));
				        
				        /*%%
				    	bb = ByteBuffer.wrap(b);
					    for (int kk = 0; kk < 10; kk++) {
						    len= bb.getInt();
						    System.out.println("#len="+ len + " s2ccipher_size=" + s2ccipher_size);
					    }
					    */

				    	ByteBuffer bb = ByteBuffer.wrap(b);
				    	int len = bb.getInt();
					    int need = len+4-s2ccipher_size;
					    System.out.println("need="+need);

				    	in.readFully(b, s2ccipher_size, need);
				    	
		  					  s2ccipher.update(b, s2ccipher_size, need, b, s2ccipher_size);

						    bb = ByteBuffer.wrap(b, 0, s2ccipher_size + need);

					  System.out.println("//////// " + new String(b, 10, 7/*s2ccipher_size + need*/));
					  
				  	  byte[] h = new byte[s2cmac_size];
				        in.readFully(h);
						  for (int k = 0; k < 0; k++) {
							  System.out.println("--- " + k);
							  in.readByte();
						  }

					  for (int iii = 3; iii <= 3; iii++) {
						s2cmac.update(iii);
						s2cmac.update(b, 0, s2ccipher_size + need);
						byte[] s2cmac_result1 = new byte[s2cmac_size];
					        s2cmac.doFinal(s2cmac_result1, 0);
					        

					        System.out.println("========== s2cmac_result1");
					        print(s2cmac_result1, 0, s2cmac_size);
					        System.out.println("========== h");
					        print(h, 0, s2cmac_size);

					  }
					    /*
					s2cmac.update(seqi);
					s2cmac.update(buf.buffer, 0, buf.index);

				        s2cmac.doFinal(s2cmac_result1, 0);
					io.getByte(s2cmac_result2, 0, s2cmac_result2.length);
				        if(!java.util.Arrays.equals(s2cmac_result1, s2cmac_result2)){
				          if(need > PACKET_MAX_SIZE){
				            throw new IOException("MAC Error");
				          }
				          start_discard(buf, s2ccipher, s2cmac, j, PACKET_MAX_SIZE-need);
				          continue;
					}
				      }

				      seqi++;
				      */
				  
					  bb.getInt();
					  bb.get();
				    int command = bb.get();
				    String c = getString(bb);
				    System.out.println("command=" + command + " len=" + bb.remaining() + "      c=" + c);
			    }
				    
				    {
				    	
				        // send
				        // byte      SSH_MSG_USERAUTH_REQUEST(50)
				        // string    user name
				        // string    service name ("ssh-connection")
				        // string    "none"
				    	
						ByteBuffer b = prepare();
					    b.put((byte) 50); //Session.SSH_MSG_USERAUTH_REQUEST);
					    putString(b, "louser");
					    putString(b, "ssh-connection");
					    putString(b, "none");
						finish(b);
						
					      c2smac.update(4/*seqo*/);
					      c2smac.update(b.array(), 0, b.remaining());
					      byte[] cb = new byte[c2smac.getBlockSize()];
					      c2smac.doFinal(cb, 0);

					      byte[] sb = new byte[100_000];
					      int sbl = c2scipher.update(b.array(), 0, b.remaining(), sb, 0);
					      System.out.println("---------- sbl="+sbl);
					      b = ByteBuffer.wrap(sb, 0, sbl + cb.length);
					      b.position(sbl);
					      b.put(cb);
					      b.flip();
						
					    out.write(b.array(), 0, b.remaining());
					    out.flush();

				    }
				    
				    //next command = 52 : success
				    //53: banner
				    //51: partial failure?
					  {
						  
						  
					    	byte[] b = new byte[s2ccipher_size + 100_000];
					    	in.readFully(b, 0, s2ccipher_size);
					    	/*%%
						    int len = 0;
						    for (int kk = 0; kk < 10; kk++) {
							    len= bb.getInt();
							    System.out.println("len="+ len + " s2ccipher_size=" + s2ccipher_size);
						    }*/

					        s2ccipher.update(b, 0, s2ccipher_size, b, 0);

					       // System.out.println("---> " + new String(b));
					        
					        /*%%
					    	bb = ByteBuffer.wrap(b);
						    for (int kk = 0; kk < 10; kk++) {
							    len= bb.getInt();
							    System.out.println("#len="+ len + " s2ccipher_size=" + s2ccipher_size);
						    }
						    */

					    	ByteBuffer bb = ByteBuffer.wrap(b);
					    	int len = bb.getInt();
						    int need = len+4-s2ccipher_size;
						    System.out.println("need="+need);

					    	in.readFully(b, s2ccipher_size, need);
					    	
			  					  s2ccipher.update(b, s2ccipher_size, need, b, s2ccipher_size);

							    bb = ByteBuffer.wrap(b, 0, s2ccipher_size + need);

						  System.out.println("//////// " + new String(b, 10, 7/*s2ccipher_size + need*/));
						  
					  	  byte[] h = new byte[s2cmac_size];
					        in.readFully(h);
							  for (int k = 0; k < 0; k++) {
								  System.out.println("--- " + k);
								  in.readByte();
							  }

						  for (int iii = 4; iii <= 4; iii++) {
							s2cmac.update(iii);
							s2cmac.update(b, 0, s2ccipher_size + need);
							byte[] s2cmac_result1 = new byte[s2cmac_size];
						        s2cmac.doFinal(s2cmac_result1, 0);
						        

						        System.out.println("========== s2cmac_result1");
						        print(s2cmac_result1, 0, s2cmac_size);
						        System.out.println("========== h");
						        print(h, 0, s2cmac_size);

						  }
						    /*
						s2cmac.update(seqi);
						s2cmac.update(buf.buffer, 0, buf.index);

					        s2cmac.doFinal(s2cmac_result1, 0);
						io.getByte(s2cmac_result2, 0, s2cmac_result2.length);
					        if(!java.util.Arrays.equals(s2cmac_result1, s2cmac_result2)){
					          if(need > PACKET_MAX_SIZE){
					            throw new IOException("MAC Error");
					          }
					          start_discard(buf, s2ccipher, s2cmac, j, PACKET_MAX_SIZE-need);
					          continue;
						}
					      }

					      seqi++;
					      */
					  
						  bb.getInt();
						  bb.get();
					    int command = bb.get();
					    String c = getString(bb);
					    int failure = bb.get();
					    System.out.println("command=" + command + " len=" + bb.remaining() + "      c=" + c + "     failure="+failure);
				    }
					  
					  {
					      // send
					      // byte      SSH_MSG_USERAUTH_REQUEST(50)
					      // string    user name
					      // string    service name ("ssh-connection")
					      // string    "password"
					      // boolen    FALSE
					      // string    plaintext password (ISO-10646 UTF-8)

							ByteBuffer b = prepare();
						    b.put((byte) 50); //Session.SSH_MSG_USERAUTH_REQUEST);
						    putString(b, "louser");
						    putString(b, "ssh-connection");
						    putString(b, "password");
						    b.put((byte) 0);
						    putString(b, "pass");
							finish(b);
							
						      c2smac.update(5/*seqo*/);
						      c2smac.update(b.array(), 0, b.remaining());
						      byte[] cb = new byte[c2smac.getBlockSize()];
						      c2smac.doFinal(cb, 0);

						      byte[] sb = new byte[100_000];
						      int sbl = c2scipher.update(b.array(), 0, b.remaining(), sb, 0);
						      System.out.println("---------- sbl="+sbl);
						      b = ByteBuffer.wrap(sb, 0, sbl + cb.length);
						      b.position(sbl);
						      b.put(cb);
						      b.flip();
							
						    out.write(b.array(), 0, b.remaining());
						    out.flush();


					  }
					  
					//next command = 52 : success
					    //53: banner
					    //51: partial failure?
					  //60: change password req
						  {
							  
							  
						    	byte[] b = new byte[s2ccipher_size + 100_000];
						    	in.readFully(b, 0, s2ccipher_size);
						    	/*%%
							    int len = 0;
							    for (int kk = 0; kk < 10; kk++) {
								    len= bb.getInt();
								    System.out.println("len="+ len + " s2ccipher_size=" + s2ccipher_size);
							    }*/

						        s2ccipher.update(b, 0, s2ccipher_size, b, 0);

						       // System.out.println("---> " + new String(b));
						        
						        /*%%
						    	bb = ByteBuffer.wrap(b);
							    for (int kk = 0; kk < 10; kk++) {
								    len= bb.getInt();
								    System.out.println("#len="+ len + " s2ccipher_size=" + s2ccipher_size);
							    }
							    */

						    	ByteBuffer bb = ByteBuffer.wrap(b);
						    	int len = bb.getInt();
							    int need = len+4-s2ccipher_size;
							    System.out.println("need="+need);

						    	in.readFully(b, s2ccipher_size, need);
						    	
				  					  s2ccipher.update(b, s2ccipher_size, need, b, s2ccipher_size);

								    bb = ByteBuffer.wrap(b, 0, s2ccipher_size + need);

							  System.out.println("//////// " + new String(b, 10, 7/*s2ccipher_size + need*/));
							  
						  	  byte[] h = new byte[s2cmac_size];
						        in.readFully(h);
								  for (int k = 0; k < 0; k++) {
									  System.out.println("--- " + k);
									  in.readByte();
								  }

							  for (int iii = 5; iii <= 5; iii++) {
								s2cmac.update(iii);
								s2cmac.update(b, 0, s2ccipher_size + need);
								byte[] s2cmac_result1 = new byte[s2cmac_size];
							        s2cmac.doFinal(s2cmac_result1, 0);
							        

							        System.out.println("========== s2cmac_result1");
							        print(s2cmac_result1, 0, s2cmac_size);
							        System.out.println("========== h");
							        print(h, 0, s2cmac_size);

							  }
							    /*
							s2cmac.update(seqi);
							s2cmac.update(buf.buffer, 0, buf.index);

						        s2cmac.doFinal(s2cmac_result1, 0);
							io.getByte(s2cmac_result2, 0, s2cmac_result2.length);
						        if(!java.util.Arrays.equals(s2cmac_result1, s2cmac_result2)){
						          if(need > PACKET_MAX_SIZE){
						            throw new IOException("MAC Error");
						          }
						          start_discard(buf, s2ccipher, s2cmac, j, PACKET_MAX_SIZE-need);
						          continue;
							}
						      }

						      seqi++;
						      */
						  
							  bb.getInt();
							  bb.get();
						    int command = bb.get();
						    if (command == 52) {
						    	System.out.println("*********** SUCCESS ********");
						    } else {
						    	/*
						    		if(command==SSH_MSG_USERAUTH_BANNER){
	  buf.getInt(); buf.getByte(); buf.getByte();
	  byte[] _message=buf.getString();
	  byte[] lang=buf.getString();
          String message=Util.byte2str(_message);
	  if(userinfo!=null){
	    userinfo.showMessage(message);
	  }
	  continue loop;
	}

						    	 */
						    	System.out.println("*********** ERROR ********");
						    }
						  }
						    {
						        // byte   SSH_MSG_CHANNEL_OPEN(90)
						        // string channel type         //
						        // uint32 sender channel       // 0
						        // uint32 initial window size  // 0x100000(65536)
						        // uint32 maxmum packet size   // 0x4000(16384)
						    	
								ByteBuffer b = prepare();
							    b.put((byte) 90);
							    putString(b, "session");
							    
						        b.putInt(0);
						        b.putInt(1048576);
						        b.putInt(16384);

								finish(b);
								
							      c2smac.update(6/*seqo*/);
							      c2smac.update(b.array(), 0, b.remaining());
							      byte[] cb = new byte[c2smac.getBlockSize()];
							      c2smac.doFinal(cb, 0);

							      byte[] sb = new byte[100_000];
							      int sbl = c2scipher.update(b.array(), 0, b.remaining(), sb, 0);
							      System.out.println("---------- sbl="+sbl);
							      b = ByteBuffer.wrap(sb, 0, sbl + cb.length);
							      b.position(sbl);
							      b.put(cb);
							      b.flip();
								
							    out.write(b.array(), 0, b.remaining());
							    out.flush();



					    }
						    //%%%for (int iiii = 0; iiii < 4; iiii++) 
						    {
								  
								  
							    	byte[] b = new byte[s2ccipher_size + 100_000];
							    	in.readFully(b, 0, s2ccipher_size);
							        s2ccipher.update(b, 0, s2ccipher_size, b, 0);
							    	ByteBuffer bb = ByteBuffer.wrap(b);
							    	int len = bb.getInt();
								    int need = len+4-s2ccipher_size;
							    	in.readFully(b, s2ccipher_size, need);
				  					  s2ccipher.update(b, s2ccipher_size, need, b, s2ccipher_size);
									    bb = ByteBuffer.wrap(b, 0, s2ccipher_size + need);
							  	  byte[] h = new byte[s2cmac_size];
							        in.readFully(h);
									  for (int k = 0; k < 0; k++) {
										  System.out.println("--- " + k);
										  in.readByte();
									  }

								  for (int iii = 6; iii <= 6; iii++) {
									s2cmac.update(iii);
									s2cmac.update(b, 0, s2ccipher_size + need);
									byte[] s2cmac_result1 = new byte[s2cmac_size];
								        s2cmac.doFinal(s2cmac_result1, 0);
								        

								        System.out.println("========== s2cmac_result1");
								        print(s2cmac_result1, 0, s2cmac_size);
								        System.out.println("========== h");
								        print(h, 0, s2cmac_size);

								  }
							  
								  bb.getInt();
								  bb.get();
							    int command = bb.get();
							    	System.out.println("*********** COMMAND="+command+" ********");
							  }
						    
						    {
								ByteBuffer b = prepare();
							    b.put((byte) 98); //SSH_MSG_CHANNEL_REQUEST
							    b.putInt(0);//getRecipient
							    putString(b, "pty-req");
							    b.put((byte) 1);//waitForReply
							    putString(b, "vt100");
						       b.putInt(80);
						        b.putInt(24);
						        b.putInt(640);
						        b.putInt(480);
						        putString(b, ""); //terminal_mode);
							    
								finish(b);
								
							      c2smac.update(7/*seqo*/);
							      c2smac.update(b.array(), 0, b.remaining());
							      byte[] cb = new byte[c2smac.getBlockSize()];
							      c2smac.doFinal(cb, 0);

							      byte[] sb = new byte[100_000];
							      int sbl = c2scipher.update(b.array(), 0, b.remaining(), sb, 0);
							      System.out.println("---------- sbl="+sbl);
							      b = ByteBuffer.wrap(sb, 0, sbl + cb.length);
							      b.position(sbl);
							      b.put(cb);
							      b.flip();
								
							    out.write(b.array(), 0, b.remaining());
							    out.flush();

						    }
						    {
								  
								  
						    	byte[] b = new byte[s2ccipher_size + 100_000];
						    	in.readFully(b, 0, s2ccipher_size);
						        s2ccipher.update(b, 0, s2ccipher_size, b, 0);
						    	ByteBuffer bb = ByteBuffer.wrap(b);
						    	int len = bb.getInt();
							    int need = len+4-s2ccipher_size;
						    	in.readFully(b, s2ccipher_size, need);
			  					  s2ccipher.update(b, s2ccipher_size, need, b, s2ccipher_size);
								    bb = ByteBuffer.wrap(b, 0, s2ccipher_size + need);
						  	  byte[] h = new byte[s2cmac_size];
						        in.readFully(h);
								  for (int k = 0; k < 0; k++) {
									  System.out.println("--- " + k);
									  in.readByte();
								  }

							  for (int iii = 7; iii <= 7; iii++) {
								s2cmac.update(iii);
								s2cmac.update(b, 0, s2ccipher_size + need);
								byte[] s2cmac_result1 = new byte[s2cmac_size];
							        s2cmac.doFinal(s2cmac_result1, 0);
							        

							        System.out.println("========== s2cmac_result1");
							        print(s2cmac_result1, 0, s2cmac_size);
							        System.out.println("========== h");
							        print(h, 0, s2cmac_size);

							  }
						  
							  bb.getInt();
							  bb.get();
						    int command = bb.get();
						    	System.out.println("*********** COMMAND="+command+" ********"); //SSH_MSG_CHANNEL_SUCCESS:99
						  }
					    
						    {
							    // send
							    // byte     (98)
							    // uint32 recipient channel
							    // string request type       // "shell"
							    // boolean want reply        // 0
							    
							    ByteBuffer b = prepare();
							    b.put((byte) 98); //SSH_MSG_CHANNEL_REQUEST
							    b.putInt(0);//getRecipient
							    putString(b, "shell");
							    b.put((byte) 1);//waitForReply
							    
								finish(b);
								
							      c2smac.update(8/*seqo*/);
							      c2smac.update(b.array(), 0, b.remaining());
							      byte[] cb = new byte[c2smac.getBlockSize()];
							      c2smac.doFinal(cb, 0);

							      byte[] sb = new byte[100_000];
							      int sbl = c2scipher.update(b.array(), 0, b.remaining(), sb, 0);
							      System.out.println("---------- sbl="+sbl);
							      b = ByteBuffer.wrap(sb, 0, sbl + cb.length);
							      b.position(sbl);
							      b.put(cb);
							      b.flip();
								
							    out.write(b.array(), 0, b.remaining());
							    out.flush();

						    }

						    {
								  
								  
						    	byte[] b = new byte[s2ccipher_size + 100_000];
						    	in.readFully(b, 0, s2ccipher_size);
						        s2ccipher.update(b, 0, s2ccipher_size, b, 0);
						    	ByteBuffer bb = ByteBuffer.wrap(b);
						    	int len = bb.getInt();
							    int need = len+4-s2ccipher_size;
						    	in.readFully(b, s2ccipher_size, need);
			  					  s2ccipher.update(b, s2ccipher_size, need, b, s2ccipher_size);
								    bb = ByteBuffer.wrap(b, 0, s2ccipher_size + need);
						  	  byte[] h = new byte[s2cmac_size];
						        in.readFully(h);
								  for (int k = 0; k < 0; k++) {
									  System.out.println("--- " + k);
									  in.readByte();
								  }

							  for (int iii = 8; iii <= 8; iii++) {
								s2cmac.update(iii);
								s2cmac.update(b, 0, s2ccipher_size + need);
								byte[] s2cmac_result1 = new byte[s2cmac_size];
							        s2cmac.doFinal(s2cmac_result1, 0);
							        

							        System.out.println("========== s2cmac_result1");
							        print(s2cmac_result1, 0, s2cmac_size);
							        System.out.println("========== h");
							        print(h, 0, s2cmac_size);

							  }
						  
							  bb.getInt();
							  bb.get();
						    int command = bb.get();
						    	System.out.println("*********** COMMAND="+command+" ********"); //93:SSH_MSG_CHANNEL_WINDOW_ADJUST
						  }
					    
						    System.out.println("----------------------wait 0 ??-------------");
						    {
								  
								  
						    	byte[] b = new byte[s2ccipher_size + 100_000];
						    	in.readFully(b, 0, s2ccipher_size);
						        s2ccipher.update(b, 0, s2ccipher_size, b, 0);
						    	ByteBuffer bb = ByteBuffer.wrap(b);
						    	int len = bb.getInt();
							    int need = len+4-s2ccipher_size;
						    	in.readFully(b, s2ccipher_size, need);
			  					  s2ccipher.update(b, s2ccipher_size, need, b, s2ccipher_size);
								    bb = ByteBuffer.wrap(b, 0, s2ccipher_size + need);
						  	  byte[] h = new byte[s2cmac_size];
						        in.readFully(h);
								  for (int k = 0; k < 0; k++) {
									  System.out.println("--- " + k);
									  in.readByte();
								  }

							  for (int iii = 7; iii <= 7; iii++) {
								s2cmac.update(iii);
								s2cmac.update(b, 0, s2ccipher_size + need);
								byte[] s2cmac_result1 = new byte[s2cmac_size];
							        s2cmac.doFinal(s2cmac_result1, 0);
							        

							        System.out.println("========== s2cmac_result1");
							        print(s2cmac_result1, 0, s2cmac_size);
							        System.out.println("========== h");
							        print(h, 0, s2cmac_size);

							  }
						  
							  bb.getInt();
							  bb.get();
						    int command = bb.get();
						    	System.out.println("*********** COMMAND="+command+" ********"); //SSH_MSG_CHANNEL_SUCCESS:99
						  }
						    System.out.println("----------------------wait 1 ??-------------");
						    {
								  
								  
						    	byte[] b = new byte[s2ccipher_size + 100_000];
						    	in.readFully(b, 0, s2ccipher_size);
						        s2ccipher.update(b, 0, s2ccipher_size, b, 0);
						    	ByteBuffer bb = ByteBuffer.wrap(b);
						    	int len = bb.getInt();
							    int need = len+4-s2ccipher_size;
						    	in.readFully(b, s2ccipher_size, need);
			  					  s2ccipher.update(b, s2ccipher_size, need, b, s2ccipher_size);
								    bb = ByteBuffer.wrap(b, 0, s2ccipher_size + need);
						  	  byte[] h = new byte[s2cmac_size];
						        in.readFully(h);
								  for (int k = 0; k < 0; k++) {
									  System.out.println("--- " + k);
									  in.readByte();
								  }

							  for (int iii = 7; iii <= 7; iii++) {
								s2cmac.update(iii);
								s2cmac.update(b, 0, s2ccipher_size + need);
								byte[] s2cmac_result1 = new byte[s2cmac_size];
							        s2cmac.doFinal(s2cmac_result1, 0);
							        

							        System.out.println("========== s2cmac_result1");
							        print(s2cmac_result1, 0, s2cmac_size);
							        System.out.println("========== h");
							        print(h, 0, s2cmac_size);

							  }
						  
							  bb.getInt();
							  bb.get();
						    int command = bb.get();
						    	System.out.println("*********** COMMAND="+command+" ********"); //94
						    	int channelId = bb.getInt();
						    	System.out.println("%%%%%%%%%%% " + getString(bb));
						  }
						   
						    System.out.println("----------------------wait 2 ??-------------");
						    {
								  
								  
						    	byte[] b = new byte[s2ccipher_size + 100_000];
						    	in.readFully(b, 0, s2ccipher_size);
						        s2ccipher.update(b, 0, s2ccipher_size, b, 0);
						    	ByteBuffer bb = ByteBuffer.wrap(b);
						    	int len = bb.getInt();
							    int need = len+4-s2ccipher_size;
						    	in.readFully(b, s2ccipher_size, need);
			  					  s2ccipher.update(b, s2ccipher_size, need, b, s2ccipher_size);
								    bb = ByteBuffer.wrap(b, 0, s2ccipher_size + need);
						  	  byte[] h = new byte[s2cmac_size];
						        in.readFully(h);
								  for (int k = 0; k < 0; k++) {
									  System.out.println("--- " + k);
									  in.readByte();
								  }

							  for (int iii = 7; iii <= 7; iii++) {
								s2cmac.update(iii);
								s2cmac.update(b, 0, s2ccipher_size + need);
								byte[] s2cmac_result1 = new byte[s2cmac_size];
							        s2cmac.doFinal(s2cmac_result1, 0);
							        

							        System.out.println("========== s2cmac_result1");
							        print(s2cmac_result1, 0, s2cmac_size);
							        System.out.println("========== h");
							        print(h, 0, s2cmac_size);

							  }
						  
							  bb.getInt();
							  bb.get();
						    int command = bb.get();
						    	System.out.println("*********** COMMAND="+command+" ********"); //94:SSH_MSG_CHANNEL_DATA
						    	int channelId = bb.getInt();

						    	System.out.println("%%%%%%%%%%% " + getString(bb));
						  }
						    {
							    ByteBuffer b = prepare();
							    b.put((byte) 94);//SSH_MSG_CHANNEL_DATA
							    b.putInt(0);//getRecipient
							    putString(b, "ls\n");
							    
								finish(b);
								
							      c2smac.update(9/*seqo*/);
							      c2smac.update(b.array(), 0, b.remaining());
							      byte[] cb = new byte[c2smac.getBlockSize()];
							      c2smac.doFinal(cb, 0);

							      byte[] sb = new byte[100_000];
							      int sbl = c2scipher.update(b.array(), 0, b.remaining(), sb, 0);
							      System.out.println("---------- sbl="+sbl);
							      b = ByteBuffer.wrap(sb, 0, sbl + cb.length);
							      b.position(sbl);
							      b.put(cb);
							      b.flip();
								
							    out.write(b.array(), 0, b.remaining());
							    out.flush();

						    }


						    System.out.println("----------------------wait 3 ??-------------");
for (int iki = 0; iki < 1000; iki++) {
								  
								  
						    	byte[] b = new byte[s2ccipher_size + 100_000];
						    	in.readFully(b, 0, s2ccipher_size);
						        s2ccipher.update(b, 0, s2ccipher_size, b, 0);
						    	ByteBuffer bb = ByteBuffer.wrap(b);
						    	int len = bb.getInt();
							    int need = len+4-s2ccipher_size;
						    	in.readFully(b, s2ccipher_size, need);
			  					  s2ccipher.update(b, s2ccipher_size, need, b, s2ccipher_size);
								    bb = ByteBuffer.wrap(b, 0, s2ccipher_size + need);
						  	  byte[] h = new byte[s2cmac_size];
						        in.readFully(h);
								  for (int k = 0; k < 0; k++) {
									  System.out.println("--- " + k);
									  in.readByte();
								  }

							  for (int iii = 9; iii <= 9; iii++) {
								s2cmac.update(iii);
								s2cmac.update(b, 0, s2ccipher_size + need);
								byte[] s2cmac_result1 = new byte[s2cmac_size];
							        s2cmac.doFinal(s2cmac_result1, 0);
							        

							        System.out.println("========== s2cmac_result1");
							        print(s2cmac_result1, 0, s2cmac_size);
							        System.out.println("========== h");
							        print(h, 0, s2cmac_size);

							  }
						  
							  bb.getInt();
							  bb.get();
						    int command = bb.get();
						    	System.out.println("*********** COMMAND="+command+" ********"); //94
						    	int channelId = bb.getInt();

						    	System.out.println("%%%%%%%%%%% " + getString(bb));
						  }
						  
			}
		//%% }
	}
	
	private static ByteBuffer prepare() {
		ByteBuffer b = ByteBuffer.allocate(100_000);
		b.put(new byte[5]); // Offset
		return b;
	}
	private static void finish(ByteBuffer b) {
	    // Padding
	    int bsize=16;//8;
	    int pad=(-b.position())&(bsize-1);
	    if(pad<bsize){
	      pad+=bsize;
	    }
	    for (int i = 0; i < pad; i++) {
			b.put((byte) random.nextInt()); //TODO nextByte
		}
	    int len = b.position()-4;
	    b.position(0);
	    b.putInt(len);
	    b.put((byte) pad);
	    b.position(4 + len);
	    b.flip();
	}
	private static ByteBuffer read(DataInputStream in) throws IOException {
	    int len = in.readInt();
	    int pad = in.read();
	    byte[] b = new byte[len - 1];
	    in.readFully(b);
	    return ByteBuffer.wrap(b);
	}
	
    private static void putMPInt(ByteBuffer b, byte[] foo){
        int i=foo.length;
        if((foo[0]&0x80)!=0){
          i++;
          b.putInt(i);
          b.put((byte)0);
        }
        else{
          b.putInt(i);
        }
        b.put(foo);
      }
  private static byte[] getMPInt(ByteBuffer b) {
        int i=b.getInt();  // uint32
        if(i<0 ||  // bigger than 0x7fffffff
           i>8*1024){
          // TODO: an exception should be thrown.
          i = 8*1024; // the session will be broken, but working around OOME.
        }
        byte[] foo=new byte[i];
        b.get(foo, 0, i);
        return foo;
      }
  private static byte[] getBlob(ByteBuffer b) {
	    int i = b.getInt();  // uint32
	    if(i<0 ||  // bigger than 0x7fffffff
	       i>256*1024){
	      // TODO: an exception should be thrown.
	      i = 256*1024; // the session will be broken, but working around OOME.
	    }
	    byte[] foo=new byte[i];
	    b.get(foo, 0, i);
	    return foo;
	  }

	private static void putString(ByteBuffer b, String s) {
		byte[] bb = s.getBytes(Charsets.UTF_8);
		b.putInt(bb.length);
		b.put(bb);
	}
	private static String getString(ByteBuffer b) {
		byte[] bb = new byte[b.getInt()];
		b.get(bb);
		return new String(bb, 0, bb.length, Charsets.UTF_8);
	}
	
	  /*
	   * It seems JCE included in Oracle's Java7u6(and later) has suddenly changed
	   * its behavior.  The secrete generated by KeyAgreement#generateSecret()
	   * may start with 0, even if it is a positive value.
	   */
	  private static byte[] normalize(byte[] secret) {
	    if(secret.length > 1 &&
	       secret[0] == 0 && (secret[1]&0x80) == 0) {
	      byte[] tmp=new byte[secret.length-1];
	      System.arraycopy(secret, 1, tmp, 0, tmp.length);
	      return normalize(tmp);
	    }
	    else {
	      return secret;
	    }
	  }
	  /*
	   * RFC 4253  7.2. Output from Key Exchange
	   * If the key length needed is longer than the output of the HASH, the
	   * key is extended by computing HASH of the concatenation of K and H and
	   * the entire key so far, and appending the resulting bytes (as many as
	   * HASH generates) to the key.  This process is repeated until enough
	   * key material is available; the key is taken from the beginning of
	   * this value.  In other words:
	   *   K1 = HASH(K || H || X || session_id)   (X is e.g., "A")
	   *   K2 = HASH(K || H || K1)
	   *   K3 = HASH(K || H || K1 || K2)
	   *   ...
	   *   key = K1 || K2 || K3 || ...
	   */
	  private static byte[] expandKey(byte[] K, byte[] H, byte[] key,
	                           SHA1 hash, int required_length) throws Exception {
	    byte[] result = key;
	    int size = hash.getBlockSize();
	    while(result.length < required_length){
		     ByteBuffer buf = ByteBuffer.allocate(100_000);
			   putMPInt(buf, K);
			    buf.put(H);
			    buf.put(result);
			    hash.update(buf.array(), 0, buf.position());
			    byte[] foo=hash.digest();

	      byte[] tmp = new byte[result.length+size];
	      System.arraycopy(result, 0, tmp, 0, result.length);
	      System.arraycopy(hash.digest(), 0, tmp, result.length, size);
	      bzero(result);
	      result = tmp;
	    }
	    return result;
	  }
	  
	 private static void bzero(byte[] foo){
		    if(foo==null)
		      return;
		    for(int i=0; i<foo.length; i++)
		      foo[i]=0;
		  }
	 
	 private static void print(byte[] b, int off, int len) {
	  	  for (int i = off; i < off+len; i++) {
	  		System.out.print(b[i] + ",");
	  		if ((i % 50) == 0) {
	  			System.out.println();
	  		}
	  	}
System.out.println();
	 }

}
