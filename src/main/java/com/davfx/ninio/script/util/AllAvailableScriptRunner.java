package com.davfx.ninio.script.util;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.common.ByAddressDatagramReadyFactory;
import com.davfx.ninio.common.ClassThreadFactory;
import com.davfx.ninio.common.Queue;
import com.davfx.ninio.common.Trust;
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
import com.davfx.ninio.script.ScriptRunner;
import com.davfx.ninio.snmp.SnmpClientConfigurator;
import com.davfx.ninio.snmp.util.SnmpClientCache;
import com.davfx.ninio.ssh.SshClientConfigurator;
import com.davfx.ninio.telnet.TelnetClientConfigurator;
import com.davfx.util.ConfigUtils;
import com.typesafe.config.Config;

public final class AllAvailableScriptRunner implements AutoCloseable {
	private static final Logger LOGGER = LoggerFactory.getLogger(AllAvailableScriptRunner.class);

	private static final Config CONFIG = ConfigUtils.load(AllAvailableScriptRunner.class);
	
	private static final int THREADING = CONFIG.getInt("script.threading");
	private static final boolean CACHE = CONFIG.getBoolean("script.cache");

	private final ScriptRunner[] runners;
	private int index = 0;
	private final Queue queue;
	
	private final SimpleHttpClient http;
	private final WaitingRemoteClientCache telnet;
	private final WaitingRemoteClientCache ssh;
	private final SnmpClientCache snmp;
	private final PingClientCache ping;
	
	private final ScheduledExecutorService scheduledExecutor = Executors.newSingleThreadScheduledExecutor(new ClassThreadFactory(AllAvailableScriptRunner.class));
	
	private final ByAddressDatagramReadyFactory udpReadyFactory; // Should be used to override all UDP connection factories in order to share the datagram socket
	
	public final HttpClientConfigurator httpConfigurator;
	public final WaitingRemoteClientConfigurator remoteConfigurator;
	public final TelnetClientConfigurator telnetConfigurator;
	public final SshClientConfigurator sshConfigurator;
	public final SnmpClientConfigurator snmpConfigurator;
	public final PingClientConfigurator pingConfigurator;

	private final Cache pingCache = CACHE ? new Cache() : null;
	private final Cache snmpCache = CACHE ? new Cache() : null;
	private final Cache telnetCache = CACHE ? new Cache() : null;
	private final Cache sshCache = CACHE ? new Cache() : null;

	public AllAvailableScriptRunner(Queue queue) {
		this.queue = queue;
		
		udpReadyFactory = new ByAddressDatagramReadyFactory(queue);
		
		httpConfigurator = new HttpClientConfigurator(queue, scheduledExecutor);
		Trust trust;
		try {
			trust = new Trust();
		} catch (IOException e) {
			LOGGER.error("Could not initialize trust", e);
			trust = null;
		}
		if (trust != null) {
			httpConfigurator.withTrust(trust);
		}
		remoteConfigurator = new WaitingRemoteClientConfigurator(scheduledExecutor, scheduledExecutor);
		telnetConfigurator = new TelnetClientConfigurator(queue);
		sshConfigurator = new SshClientConfigurator(queue);
		snmpConfigurator = new SnmpClientConfigurator(queue, scheduledExecutor).override(udpReadyFactory);
		pingConfigurator = new PingClientConfigurator(queue); //%%%, scheduledExecutor);

		http = new SimpleHttpClient(httpConfigurator);
		telnet = new WaitingRemoteClientCache(remoteConfigurator, queue, new TelnetRemoteConnectorFactory(telnetConfigurator));
		ssh = new WaitingRemoteClientCache(remoteConfigurator, queue, new SshRemoteConnectorFactory(sshConfigurator));
		snmp = new SnmpClientCache(snmpConfigurator);
		ping = new PingClientCache(pingConfigurator);

		runners = new ScriptRunner[THREADING];
		for (int i = 0; i < THREADING; i++) {
			runners[i] = new QueueScriptRunner(queue, new ExecutorScriptRunner());
			PingAvailable.register(runners[i], ping, pingCache);
			SnmpAvailable.register(runners[i], snmp, snmpCache);
			WaitingTelnetAvailable.register(runners[i], telnet, telnetCache);
			WaitingSshAvailable.register(runners[i], ssh, sshCache);
			HttpAvailable.register(runners[i], http); // No cache for HTTP
		}
	}

	public Iterable<ScriptRunner> runners() {
		List<ScriptRunner> l = new LinkedList<>();
		for (ScriptRunner r : runners) {
			l.add(r);
		}
		return l;
	}
	
	public ScriptRunner.Engine runner() {
		int i = index;
		index = (index + 1) % runners.length;
		return runners[i].engine();
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

		for (ScriptRunner r : runners) {
			r.close();
		}
		
		httpConfigurator.close();
		remoteConfigurator.close();
		telnetConfigurator.close();
		sshConfigurator.close();
		snmpConfigurator.close();
		pingConfigurator.close();
		
		udpReadyFactory.close();
		
		scheduledExecutor.shutdown();
	}
}
