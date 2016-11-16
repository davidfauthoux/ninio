package com.davfx.ninio.ssh;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Connecter;
import com.davfx.ninio.core.Connection;
import com.davfx.ninio.core.Ninio;
import com.davfx.ninio.core.Nop;
import com.davfx.ninio.core.TcpSocket;
import com.davfx.ninio.core.Trust;
import com.davfx.ninio.telnet.TelnetSpecification;
import com.google.common.base.Charsets;

///!\ NOT WORKING WITH Java7 ON UP-TO-DATE open-ssl SERVERS

//*** TO ADD THE PUBLIC KEY TO THE AUTHORIZED KEYS ON THE SERVER: *** 
//keytool -importkeystore -srckeystore keystore.jks -destkeystore keystore.p12 -deststoretype PKCS12 -srcalias "test-alias" -deststorepass "test-password" -destkeypass "test-password"
//openssl pkcs12 -in keystore.p12  -nokeys -out cert.pem
//pkcs12 -in keystore.p12 -nocerts -out privateKey.pem
//chmod 400 privateKey.pem
//ssh-keygen -y -f privateKey.pem > publicKey.pub
//cat publicKey.pub >> ~/.ssh/authorized_keys

//*** ON MAC OS X El Capitan, TO CONFIGURE THE SSH SERVER: *** 
// [Apple Menu] > System Preferences > Sharing > Remote Login > On
//sudo nano /private/etc/ssh/sshd_config

public class SshTest {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(SshTest.class);
	
	public static void main(String[] args) throws Exception {
		Trust trust = new Trust("/keystore.jks", "test-password", "/keystore.jks", "test-password");
		RsaSshPublicKey publicKey = new RsaSshPublicKey((RSAPrivateKey) trust.getPrivateKey("test-alias", "test-password"), (RSAPublicKey) trust.getPublicKey("test-alias"));

		try (Ninio ninio = Ninio.create()) {
			try (Connecter c = ninio.create(SshClient.builder().login("davidfauthoux", publicKey).with(TcpSocket.builder().to(new Address(Address.LOCALHOST, SshSpecification.DEFAULT_PORT))))) {
				c.connect(new Connection() {
					private int n = 0;
					
					@Override
					public void received(Address address, ByteBuffer buffer) {
						LOGGER.debug("Received: {}", com.davfx.ninio.core.ByteBufferUtils.toString(buffer));
						switch (n) {
						case 0:
							c.send(null, ByteBuffer.wrap(("ls" + TelnetSpecification.EOL).getBytes(Charsets.UTF_8)), new Nop());
							break;
						}
						n++;
					}
					
					@Override
					public void failed(IOException ioe) {
						LOGGER.error("Failed", ioe);
					}
					
					@Override
					public void connected(Address address) {
						LOGGER.debug("Connected");
					}
					
					@Override
					public void closed() {
						LOGGER.debug("Closed");
					}
				});
				Thread.sleep(60000);
			}
		}
	}
}
