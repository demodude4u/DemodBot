package com.vectorcat.irc.demodbot;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.util.concurrent.AbstractIdleService;
import com.google.common.util.concurrent.Service;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.vectorcat.irc.Channel;
import com.vectorcat.irc.IRCControl;
import com.vectorcat.irc.IRCModule;
import com.vectorcat.irc.Server;
import com.vectorcat.irc.demodbot.feature.AdminFeature;
import com.vectorcat.irc.demodbot.feature.CleverBotFeature;
import com.vectorcat.irc.demodbot.feature.ExperimentalFeature;
import com.vectorcat.irc.demodbot.feature.KeepAliveFeature;
import com.vectorcat.irc.demodbot.feature.MailFeature;
import com.vectorcat.irc.demodbot.feature.ReJoinFeature;
import com.vectorcat.irc.demodbot.util.DPasteMessageSolution;
import com.vectorcat.irc.demodbot.util.LongMessageSolution;
import com.vectorcat.irc.event.IRCSendEvent;
import com.vectorcat.irc.event.recv.IRCRecvRaw;

public class DemodBot extends AbstractIdleService implements Service {

	private static Object createEventLogger() {
		return new Object() {
			@Subscribe
			public void onAnythingElse(Object event) {
				if (event instanceof IRCRecvRaw) {
					return;
				}
				if (event instanceof IRCSendEvent) {
					return;
				}
				System.out.println(" [" + event.getClass().getSimpleName()
						+ "]");
			}

			@Subscribe
			public void onRecvRaw(IRCRecvRaw event) {
				System.out.println("RECV <- " + event.getMessage());
			}

			@Subscribe
			public void onSend(IRCSendEvent event) {
				System.out.println("##### SEND -> " + event.getRawMessage());
			}
		};
	}

	private Server server = null;

	private final String initHost;
	private final int initPort;
	private final String initName;
	private final String initPassword;
	private final String[] initChannels;

	public DemodBot(String host, int port, String name, String password,
			String... channels) {
		this.initHost = host;
		this.initPort = port;
		this.initName = name;
		this.initPassword = password;
		this.initChannels = channels;

	}

	private AbstractModule createGuiceModule() {
		return new AbstractModule() {
			@Override
			protected void configure() {
				// bind(LongMessageSolution.class).to(
				// PasteBinMessageSolution.class);
				// bindConstant().annotatedWith(
				// Names.named("Pastebin API Key")).to(
				// "6042c1ee1fc8f498c24371b02a830097");

				bind(LongMessageSolution.class).to(DPasteMessageSolution.class);
			}
		};
	}

	private Injector createInjector() {
		return Guice.createInjector(new IRCModule(), createGuiceModule());
	}

	@Override
	protected void shutDown() throws Exception {
		if (server != null) {
			server.disconnect();
		}
	}

	@Override
	protected void startUp() throws Exception {
		Injector injector = createInjector();

		EventBus bus = injector.getInstance(EventBus.class);

		bus.register(createEventLogger());

		IRCControl control = injector.getInstance(IRCControl.class);

		// Initialize Features
		injector.getInstance(ReJoinFeature.class);
		injector.getInstance(KeepAliveFeature.class);
		injector.getInstance(CleverBotFeature.class);
		injector.getInstance(MailFeature.class);
		injector.getInstance(ExperimentalFeature.class);

		AdminFeature adminFeature = injector.getInstance(AdminFeature.class);
		adminFeature.addAdmins("Bui", "jtran", "Demod", "zinmirai");

		server = control.getServer(initHost, initPort, initName, initPassword);
		// Initial actions
		server.connect();

		for (String initChannel : initChannels) {
			Channel channel = control.getChannel(initChannel);
			channel.join();
		}

		control.ignore("SolidSnake");
	}

}
