package com.davfx.ninio.ssh;

import java.io.IOException;
import java.nio.ByteBuffer;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.FailableCloseableByteBufferHandler;
import com.davfx.ninio.core.ReadyConnection;
import com.davfx.ninio.core.Trust;
import com.google.common.base.Charsets;

// /!\ NOT WORKING WITH Java7 ON UP-TO-DATE open-ssl SERVERS

//*** TO ADD THE PUBLIC KEY TO THE AUTHORIZED KEYS ON THE SERVER: *** 
// keytool -importkeystore -srckeystore keystore.jks -destkeystore keystore.p12 -deststoretype PKCS12 -srcalias "test-alias" -deststorepass "test-password" -destkeypass "test-password"
// openssl pkcs12 -in keystore.p12  -nokeys -out cert.pem
// pkcs12 -in keystore.p12 -nocerts -out privateKey.pem
// chmod 400 privateKey.pem
// ssh-keygen -y -f privateKey.pem > publicKey.pub
// cat publicKey.pub >> ~/.ssh/authorized_keys

//*** ON MAC OS X El Capitan, TO CONFIGURE THE SSH SERVER: *** 
// sudo nano /private/etc/ssh/sshd_config

public final class Readme {
	public static void main(String[] args) throws Exception {
		/*
			for (Provider p : Security.getProviders()) {
				Security.removeProvider(p.getName());
			}
			Security.addProvider(new BouncyCastleProvider());
		*/
		
		Trust trust = new Trust("/keystore.jks", "test-password", "/keystore.jks", "test-password");

		new Ssh()
			.withLogin("<your-login>")
			.withKey(trust, "test-alias", "test-password")
			// .withPassword("<your-password>")
		.to(new Address("127.0.0.1", Ssh.DEFAULT_PORT)).client().connect(new ReadyConnection() {
			
			private FailableCloseableByteBufferHandler write;

			@Override
			public void failed(IOException e) {
				e.printStackTrace();
			}
			
			@Override
			public void close() {
				System.out.println("Closed");
			}
			
			private void send(String line) {
				write.handle(null, ByteBuffer.wrap((line + '\n').getBytes(Charsets.UTF_8)));
			}
			
			@Override
			public void handle(Address address, ByteBuffer buffer) {
				String s = new String(buffer.array(), buffer.position(), buffer.remaining(), Charsets.UTF_8);
				System.out.print(s);
			}
			
			@Override
			public void connected(FailableCloseableByteBufferHandler write) {
				this.write = write;
				send("echo TEST");
			}
		});
		
		Thread.sleep(1000);
	}
}
