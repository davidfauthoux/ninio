package com.davfx.ninio.script.util;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

import com.davfx.ninio.common.Queue;
import com.davfx.ninio.http.HttpClientConfigurator;
import com.davfx.ninio.http.util.SimpleHttpClient;
import com.davfx.ninio.ping.PingClientConfigurator;
import com.davfx.ninio.ping.util.PingClientCache;
import com.davfx.ninio.remote.WaitingRemoteClientCache;
import com.davfx.ninio.remote.WaitingRemoteClientConfigurator;
import com.davfx.ninio.remote.ssh.SshRemoteConnectorFactory;
import com.davfx.ninio.remote.telnet.TelnetRemoteConnectorFactory;
import com.davfx.ninio.script.ExecutorScriptRunner;
import com.davfx.ninio.script.QueueScriptRunner;
import com.davfx.ninio.script.RegisteredFunctionsScriptRunner;
import com.davfx.ninio.script.RoundRobinScriptRunner;
import com.davfx.ninio.script.SimpleScriptRunnerScriptRegister;
import com.davfx.ninio.snmp.SnmpClientConfigurator;
import com.davfx.ninio.snmp.util.SnmpClientCache;
import com.davfx.ninio.ssh.SshClientConfigurator;
import com.davfx.ninio.telnet.TelnetClientConfigurator;
import com.davfx.util.ConfigUtils;
import com.google.gson.JsonElement;

public class AllAvailableScriptRunner implements AutoCloseable {

	private static final int THREADING = ConfigUtils.load(AllAvailableScriptRunner.class).getInt("script.threading");

	private final RoundRobinScriptRunner<JsonElement> scriptRunner;
	//%% private final RoundRobinScriptRunner<String> scriptRunner;
	private final Queue queue;
	
	private final SimpleHttpClient http;
	private final WaitingRemoteClientCache telnet;
	private final WaitingRemoteClientCache ssh;
	private final SnmpClientCache snmp;
	private final PingClientCache ping;
	
	private final ScheduledExecutorService scheduledExecutor = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
		@Override
		public Thread newThread(Runnable r) {
			return new Thread(r, AllAvailableScriptRunner.class.getSimpleName());
		}
	});
	
	public final HttpClientConfigurator httpConfigurator;
	public final WaitingRemoteClientConfigurator remoteConfigurator;
	public final TelnetClientConfigurator telnetConfigurator;
	public final SshClientConfigurator sshConfigurator;
	public final SnmpClientConfigurator snmpConfigurator;
	public final PingClientConfigurator pingConfigurator;

	public AllAvailableScriptRunner(Queue queue) throws IOException {
		this.queue = queue;
		
		httpConfigurator = new HttpClientConfigurator(queue, scheduledExecutor);
		remoteConfigurator = new WaitingRemoteClientConfigurator(scheduledExecutor);
		telnetConfigurator = new TelnetClientConfigurator(queue);
		sshConfigurator = new SshClientConfigurator(queue);
		snmpConfigurator = new SnmpClientConfigurator(queue, scheduledExecutor);
		pingConfigurator = new PingClientConfigurator(queue); //%%%, scheduledExecutor);

		scriptRunner = new RoundRobinScriptRunner<>();
		for (int i = 0; i < THREADING; i++) {
			scriptRunner.add(new ExecutorScriptRunner());
		}

		http = new SimpleHttpClient(httpConfigurator);
		telnet = new WaitingRemoteClientCache(remoteConfigurator, queue, new TelnetRemoteConnectorFactory(telnetConfigurator));
		ssh = new WaitingRemoteClientCache(remoteConfigurator, queue, new SshRemoteConnectorFactory(sshConfigurator));
		snmp = new SnmpClientCache(snmpConfigurator);
		ping = new PingClientCache(pingConfigurator);
	}

	public SimpleScriptRunnerScriptRegister runner() {
		RegisteredFunctionsScriptRunner runner = new RegisteredFunctionsScriptRunner(new QueueScriptRunner<JsonElement>(queue, scriptRunner));
		//%% RegisteredFunctionsScriptRunner runner = new RegisteredFunctionsScriptRunner(new QueueScriptRunner<JsonElement>(queue, new JsonScriptRunner(scriptRunner)));
		new PingAvailable(ping).registerOn(runner);
		new SnmpAvailable(snmp).registerOn(runner);
		new WaitingTelnetAvailable(telnet).registerOn(runner);
		new WaitingSshAvailable(ssh).registerOn(runner);
		new HttpAvailable(http).registerOn(runner);
		return runner;
	}
	
	@Override
	public void close() {
		http.close();
		telnet.close();
		ssh.close();
		snmp.close();
		ping.close();

		scriptRunner.close();
		
		httpConfigurator.close();
		remoteConfigurator.close();
		telnetConfigurator.close();
		sshConfigurator.close();
		snmpConfigurator.close();
		pingConfigurator.close();
		
		scheduledExecutor.shutdown();
	}
}
