package com.davfx.ninio.script;

import com.davfx.ninio.util.Lock;
import com.davfx.ninio.util.Wait;

public final class WaitLockScriptRunnerEnd implements ScriptRunner.End {
	private final Wait wait;
	private final Lock<?, Exception> lock;
	public WaitLockScriptRunnerEnd(Wait wait, Lock<?, Exception> lock) {
		this.wait = wait;
		this.lock = lock;
	}
	
	@Override
	public void ended() {
		wait.run();
	}
	@Override
	public void failed(Exception e) {
		lock.fail(e);
		wait.run();
	}
}
