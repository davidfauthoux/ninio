package com.davfx.ninio.trash;

import java.io.File;
import java.io.IOException;

import com.davfx.ninio.common.Failable;
import com.davfx.ninio.ssh.util.ScpClient;

public class TestScpClient {
	public static void main(String[] args) throws Exception {
		/*new ScpClient().withHost("172.17.10.31").withLogin("louser").withPassword("pass").get("/home/louser/jsonFile141ubacupibindumrn2at4asga.json", new FileFailableCloseableByteBufferHandler(new File("jsonFile141ubacupibindumrn2at4asga.json_"), new Failable() {
			@Override
			public void failed(IOException e) {
				System.out.println(e);
			}
		}));
		*/
		new ScpClient().withHost("172.17.10.31").withLogin("louser").withPassword("pass").put("/home/louser/jsonFile141ubacupibindumrn2at4asg a.json_", new File("jsonFile141ubacupibindumrn2at4asga.json_"), new Failable() {
			@Override
			public void failed(IOException e) {
				System.out.println(e);
			}
		});
	}
}
