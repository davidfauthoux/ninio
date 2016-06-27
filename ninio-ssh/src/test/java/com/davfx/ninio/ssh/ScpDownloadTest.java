package com.davfx.ninio.ssh;

import java.io.File;
import java.io.IOException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Connecting;
import com.davfx.ninio.core.Connector;
import com.davfx.ninio.core.Failing;
import com.davfx.ninio.core.Ninio;
import com.davfx.ninio.core.TcpSocket;
import com.davfx.ninio.core.ToFileReceiverClosing;
import com.davfx.ninio.core.Trust;
import com.davfx.ninio.core.WaitClosing;
import com.davfx.ninio.core.WaitFailing;
import com.davfx.ninio.util.SerialExecutor;
import com.davfx.ninio.util.Wait;

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

public class ScpDownloadTest {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(ScpDownloadTest.class);
	
	public static void main(String[] args) throws Exception {
		Trust trust = new Trust("/keystore.jks", "test-password", "/keystore.jks", "test-password");
		RsaSshPublicKey publicKey = new RsaSshPublicKey((RSAPrivateKey) trust.getPrivateKey("test-alias", "test-password"), (RSAPublicKey) trust.getPublicKey("test-alias"));

		try (Ninio ninio = Ninio.create()) {
			Wait wait = new Wait();
			try (ToFileReceiverClosing toFileReceiverClosing = new ToFileReceiverClosing(new File("downloaded.txt"), new WaitClosing(wait), new WaitFailing(wait))) {
				try (Connector c = ninio.create(ScpDownloadClient.builder().path("todownload.txt").receiving(toFileReceiverClosing).closing(toFileReceiverClosing).failing(new Failing() {
					@Override
					public void failed(IOException e) {
						LOGGER.error("Failed", e);
					}
				}).build(SshClient.builder().login("davidfauthoux", publicKey).with(new SerialExecutor(SshTest.class)).to(new Address(Address.LOCALHOST, SshSpecification.DEFAULT_PORT)).connecting(new Connecting() {
					@Override
					public void connected(Connector connector, Address address) {
						LOGGER.debug("Connected");
					}
				}).with(TcpSocket.builder())))) {
					wait.waitFor();
				}
			}
		}
	}
}
