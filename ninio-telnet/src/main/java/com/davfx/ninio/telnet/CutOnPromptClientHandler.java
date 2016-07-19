package com.davfx.ninio.telnet;

import com.davfx.ninio.core.Closing;
import com.davfx.ninio.core.Connecting;
import com.davfx.ninio.core.Failing;

public interface CutOnPromptClientHandler extends Closing, Failing, Connecting {
	void connected(CutOnPromptClientWriter write);
}