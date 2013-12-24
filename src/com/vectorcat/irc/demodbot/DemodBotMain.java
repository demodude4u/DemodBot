package com.vectorcat.irc.demodbot;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.UnknownHostException;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
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
import com.vectorcat.irc.demodbot.feature.HelpFeature;
import com.vectorcat.irc.demodbot.feature.MailFeature;
import com.vectorcat.irc.demodbot.feature.ReJoinFeature;
import com.vectorcat.irc.demodbot.util.DPasteMessageSolution;
import com.vectorcat.irc.demodbot.util.LongMessageSolution;
import com.vectorcat.irc.event.IRCSendEvent;
import com.vectorcat.irc.event.recv.IRCRecvRaw;
import com.vectorcat.irc.event.send.IRCSendRaw;

public class DemodBotMain {

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

	public static void main(String[] args) throws InterruptedException,
			UnknownHostException, IOException {
		Injector injector = Guice.createInjector(new IRCModule(),
				new AbstractModule() {
					@Override
					protected void configure() {
						// bind(LongMessageSolution.class).to(
						// PasteBinMessageSolution.class);
						// bindConstant().annotatedWith(
						// Names.named("Pastebin API Key")).to(
						// "6042c1ee1fc8f498c24371b02a830097");

						bind(LongMessageSolution.class).to(
								DPasteMessageSolution.class);
					}
				});

		EventBus bus = injector.getInstance(EventBus.class);

		bus.register(createEventLogger());

		IRCControl control = injector.getInstance(IRCControl.class);

		// Initialize Features
		AdminFeature adminFeature = injector.getInstance(AdminFeature.class);
		adminFeature.addAdmins("Bui", "jtran", "Demod", "zinmirai");
		injector.getInstance(HelpFeature.class);
		injector.getInstance(ReJoinFeature.class);
		injector.getInstance(CleverBotFeature.class);
		injector.getInstance(MailFeature.class);
		injector.getInstance(ExperimentalFeature.class);

		Server server = control.getServer("irc.fyrechat.net", 6667, "DemodBot",
				"password");
		try {
			// Initial actions
			server.connect();

			Channel channel = control.getChannel("#Vana");
			channel.join();

			control.ignore("SolidSnake");

		} catch (Exception e) {
			e.printStackTrace();
			server.disconnect();
			System.exit(-1);
		}

		// Send anything typed in console as raw lines to the IRC
		BufferedReader sysin = new BufferedReader(new InputStreamReader(
				System.in));
		String line;
		while ((line = sysin.readLine()) != null) {
			bus.post(new IRCSendRaw(line));
		}

	}
}
