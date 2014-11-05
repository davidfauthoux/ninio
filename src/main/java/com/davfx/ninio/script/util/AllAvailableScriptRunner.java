package com.davfx.ninio.script.util;

import java.io.IOException;

import com.davfx.ninio.common.Queue;
import com.davfx.ninio.common.ReadyFactory;
import com.davfx.ninio.http.HttpClient;
import com.davfx.ninio.ping.util.PingClientCache;
import com.davfx.ninio.script.ExecutorScriptRunner;
import com.davfx.ninio.script.JsonScriptRunner;
import com.davfx.ninio.script.QueueScriptRunner;
import com.davfx.ninio.script.RegisteredFunctionsScriptRunner;
import com.davfx.ninio.script.RoundRobinScriptRunner;
import com.davfx.ninio.script.SimpleScriptRunnerScriptRegister;
import com.davfx.ninio.snmp.util.SnmpClientCache;
import com.davfx.ninio.ssh.util.SshTelnetConnectorFactory;
import com.davfx.ninio.telnet.util.WaitingTelnetClientCache;
import com.davfx.util.ConfigUtils;
import com.google.gson.JsonElement;

public class AllAvailableScriptRunner implements AutoCloseable {

	private static final int THREADING = ConfigUtils.load(AllAvailableScriptRunner.class).getInt("script.threading");

	private final RoundRobinScriptRunner<String> scriptRunner;
	private final Queue queue;
	private final HttpClient http;
	private final WaitingTelnetClientCache telnet;
	private final WaitingTelnetClientCache ssh;
	private final SnmpClientCache snmp;
	private final PingClientCache ping;

	public AllAvailableScriptRunner(Queue queue) throws IOException {
		this.queue = queue;
		
		scriptRunner = new RoundRobinScriptRunner<>();
		for (int i = 0; i < THREADING; i++) {
			scriptRunner.add(new ExecutorScriptRunner());
		}

		http = new HttpClient(queue, null);
		
		telnet = new WaitingTelnetClientCache(queue);
		
		ssh = new WaitingTelnetClientCache(queue);
		ssh.override(new SshTelnetConnectorFactory());
		
		snmp = new SnmpClientCache(queue);
		
		ping = new PingClientCache(queue);
	}

	public AllAvailableScriptRunner withTelnetCallWithEmptyTime(double callWithEmptyTime) {
		telnet.withCallWithEmptyTime(callWithEmptyTime);
		return this;
	}
	public AllAvailableScriptRunner withTelnetEndOfCommandTime(double endOfCommandTime) {
		telnet.withEndOfCommandTime(endOfCommandTime);
		return this;
	}
	public AllAvailableScriptRunner withTelnetTimeout(double timeout) {
		telnet.withTimeout(timeout);
		return this;
	}

	public AllAvailableScriptRunner withSnmpMinTimeToRepeat(double minTimeToRepeat) {
		snmp.withMinTimeToRepeat(minTimeToRepeat);
		return this;
	}
	public AllAvailableScriptRunner withSnmpRepeatTime(double repeatTime) {
		snmp.withRepeatTime(repeatTime);
		return this;
	}
	public AllAvailableScriptRunner withSnmpTimeoutFromBeginning(double timeoutFromBeginning) {
		snmp.withTimeoutFromBeginning(timeoutFromBeginning);
		return this;
	}
	public AllAvailableScriptRunner withSnmpTimeoutFromLastReception(double timeoutFromLastReception) {
		snmp.withTimeoutFromLastReception(timeoutFromLastReception);
		return this;
	}
	public AllAvailableScriptRunner withSnmpBulkSize(int bulkSize) {
		snmp.withBulkSize(bulkSize);
		return this;
	}
	public AllAvailableScriptRunner withSnmpGetLimit(int getLimit) {
		snmp.withGetLimit(getLimit);
		return this;
	}

	public AllAvailableScriptRunner withPingMinTimeToRepeat(double minTimeToRepeat) {
		ping.withMinTimeToRepeat(minTimeToRepeat);
		return this;
	}
	public AllAvailableScriptRunner withPingRepeatTime(double repeatTime) {
		ping.withRepeatTime(repeatTime);
		return this;
	}
	public AllAvailableScriptRunner withPingTimeoutFromBeginning(double timeoutFromBeginning) {
		ping.withTimeoutFromBeginning(timeoutFromBeginning);
		return this;
	}

	public AllAvailableScriptRunner httpSecureSocketOverride(ReadyFactory readyFactory) {
		http.overrideSecure(readyFactory);
		return this;
	}
	public AllAvailableScriptRunner httpSocketOverride(ReadyFactory readyFactory) {
		http.override(readyFactory);
		return this;
	}
	public AllAvailableScriptRunner telnetSocketOverride(ReadyFactory readyFactory) {
		telnet.override(readyFactory);
		return this;
	}
	public AllAvailableScriptRunner snmpDatagramOverride(ReadyFactory readyFactory) {
		snmp.override(readyFactory);
		return this;
	}
	public AllAvailableScriptRunner pingDatagramOverride(ReadyFactory readyFactory) {
		ping.override(readyFactory);
		return this;
	}
	
	public SimpleScriptRunnerScriptRegister runner() {
		//TODO mal ecrit
		return new PingAvailable(ping).register(
				new SnmpAvailable(snmp).register(
					new WaitingTelnetAvailable(telnet).register(
					new WaitingSshAvailable(ssh).register(
						new HttpAvailable(http).register(
							new RegisteredFunctionsScriptRunner(new QueueScriptRunner<JsonElement>(queue, new JsonScriptRunner(scriptRunner))))))));
	}
	
	@Override
	public void close() {
		http.close();
		telnet.close();
		ssh.close();
		snmp.close();
		ping.close();
		scriptRunner.close();
	}
}
