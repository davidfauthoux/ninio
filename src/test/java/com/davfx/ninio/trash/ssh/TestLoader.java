package com.davfx.ninio.trash.ssh;

import java.io.File;

import com.davfx.ninio.ssh.util.PublicKeyLoader;

public class TestLoader {
	public static void main(String[] args) throws Exception {
		System.out.println(new PublicKeyLoader(new File("/Users/davidfauthoux/.ssh/id_dsa.pub")).publicKey + "/");
	}
}
