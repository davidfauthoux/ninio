package com.davfx.ninio.script.util;

import java.util.Iterator;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import com.davfx.ninio.common.ClassThreadFactory;
import com.davfx.ninio.common.Failable;
import com.davfx.ninio.common.Queue;
import com.davfx.ninio.http.HttpClientConfigurator;
import com.davfx.ninio.http.util.SimpleHttpClient;
import com.davfx.ninio.ping.PingClientConfigurator;
import com.davfx.ninio.ping.util.PingClientCache;
import com.davfx.ninio.remote.WaitingRemoteClientCache;
import com.davfx.ninio.remote.WaitingRemoteClientConfigurator;
import com.davfx.ninio.remote.ssh.SshRemoteConnectorFactory;
import com.davfx.ninio.remote.telnet.TelnetRemoteConnectorFactory;
import com.davfx.ninio.script.AsyncScriptFunction;
import com.davfx.ninio.script.ExecutorScriptRunner;
import com.davfx.ninio.script.QueueScriptRunner;
import com.davfx.ninio.script.SyncScriptFunction;
import com.davfx.ninio.snmp.SnmpClientConfigurator;
import com.davfx.ninio.snmp.util.SnmpClientCache;
import com.davfx.ninio.ssh.SshClientConfigurator;
import com.davfx.ninio.telnet.TelnetClientConfigurator;
import com.davfx.util.ConfigUtils;
import com.google.common.collect.Iterators;
import com.google.gson.JsonElement;
import com.typesafe.config.Config;

public class AllAvailableScriptRunner implements AutoCloseable {

	private static final Config CONFIG = ConfigUtils.load(AllAvailableScriptRunner.class);
	
	private static final int THREADING = CONFIG.getInt("script.threading");
	private static final boolean CACHE = CONFIG.getBoolean("script.cache");

	private final ExecutorScriptRunner[] runners;
	private final RegisteredFunctionsScriptRunner[] scriptRunners;
	private int index = 0;
	private final Queue queue;
	
	private final SimpleHttpClient http;
	private final WaitingRemoteClientCache telnet;
	private final WaitingRemoteClientCache ssh;
	private final SnmpClientCache snmp;
	private final PingClientCache ping;
	
	private final ScheduledExecutorService scheduledExecutor = Executors.newSingleThreadScheduledExecutor(new ClassThreadFactory(AllAvailableScriptRunner.class));
	
	public final HttpClientConfigurator httpConfigurator;
	public final WaitingRemoteClientConfigurator remoteConfigurator;
	public final TelnetClientConfigurator telnetConfigurator;
	public final SshClientConfigurator sshConfigurator;
	public final SnmpClientConfigurator snmpConfigurator;
	public final PingClientConfigurator pingConfigurator;

	public AllAvailableScriptRunner(Queue queue) {
		this.queue = queue;
		
		httpConfigurator = new HttpClientConfigurator(queue, scheduledExecutor);
		remoteConfigurator = new WaitingRemoteClientConfigurator(scheduledExecutor, scheduledExecutor);
		telnetConfigurator = new TelnetClientConfigurator(queue);
		sshConfigurator = new SshClientConfigurator(queue);
		snmpConfigurator = new SnmpClientConfigurator(queue, scheduledExecutor);
		pingConfigurator = new PingClientConfigurator(queue); //%%%, scheduledExecutor);

		http = new SimpleHttpClient(httpConfigurator);
		telnet = new WaitingRemoteClientCache(remoteConfigurator, queue, new TelnetRemoteConnectorFactory(telnetConfigurator));
		ssh = new WaitingRemoteClientCache(remoteConfigurator, queue, new SshRemoteConnectorFactory(sshConfigurator));
		snmp = new SnmpClientCache(snmpConfigurator);
		ping = new PingClientCache(pingConfigurator);

		runners = new ExecutorScriptRunner[THREADING];
		for (int i = 0; i < THREADING; i++) {
			runners[i] = new ExecutorScriptRunner();
		}
		scriptRunners = new RegisteredFunctionsScriptRunner[THREADING];
		Cache pingCache = CACHE ? new Cache() : null;
		Cache snmpCache = CACHE ? new Cache() : null;
		Cache telnetCache = CACHE ? new Cache() : null;
		Cache sshCache = CACHE ? new Cache() : null;
		for (int i = 0; i < THREADING; i++) {
			final RegisteredFunctionsScriptRunner runner = new RegisteredFunctionsScriptRunner(new QueueScriptRunner<JsonElement>(queue, runners[i]));
			PingAvailable.link(runner, ping, pingCache);
			SnmpAvailable.link(runner, snmp, snmpCache);
			WaitingTelnetAvailable.link(runner, telnet, telnetCache);
			WaitingSshAvailable.link(runner, ssh, sshCache);
			HttpAvailable.link(runner, http); // No cache for HTTP
			
			scriptRunners[i] = runner;
		}
	}

	public Iterable<RegisteredFunctionsScriptRunner> runners() {
		return new Iterable<RegisteredFunctionsScriptRunner>() {
			public Iterator<RegisteredFunctionsScriptRunner> iterator() {
				return Iterators.forArray(scriptRunners);
			}
		};
	}
	
	public AllAvailableRunner runner() {
		int i = index;
		index = (index + 1) % scriptRunners.length;
		
		final RegisteredFunctionsScriptRunner runner = scriptRunners[i];
		final CallingEndScriptRunner callingEnd = new CallingEndScriptRunner(runner);

		return new AllAvailableRunner() {
			@Override
			public void register(String functionId) {
				runner.register(functionId);
			}
			@Override
			public void prepare(Iterable<String> script, Failable fail) {
				callingEnd.prepare(script, fail);
			}
			@Override
			public void link(Runnable onEnd) {
				callingEnd.link(onEnd);
			}
			@Override
			public void link(String functionId, AsyncScriptFunction<JsonElement> function) {
				runner.link(functionId, function);
			}
			@Override
			public void link(String functionId, SyncScriptFunction<JsonElement> function) {
				runner.link(functionId, function);
			}
			@Override
			public void eval(Iterable<String> script, Failable fail) {
				callingEnd.eval(script, fail);
			}
		};
	}
	
	@Override
	public void close() {
		queue.post(new Runnable() {
			@Override
			public void run() {
				http.close();
				telnet.close();
				ssh.close();
				snmp.close();
				ping.close();
			}
		});

		for (ExecutorScriptRunner r : runners) {
			r.close();
		}
		
		httpConfigurator.close();
		remoteConfigurator.close();
		telnetConfigurator.close();
		sshConfigurator.close();
		snmpConfigurator.close();
		pingConfigurator.close();
		
		scheduledExecutor.shutdown();
	}
}
