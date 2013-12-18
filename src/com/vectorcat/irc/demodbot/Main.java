package com.vectorcat.irc.demodbot;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.UnknownHostException;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.vectorcat.irc.Channel;
import com.vectorcat.irc.IRCControl;
import com.vectorcat.irc.IRCModule;
import com.vectorcat.irc.Server;
import com.vectorcat.irc.event.IRCSendEvent;
import com.vectorcat.irc.event.recv.IRCRecvRaw;
import com.vectorcat.irc.event.send.IRCSendRaw;

public class Main {

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
		Injector injector = Guice.createInjector(new IRCModule());

		EventBus bus = injector.getInstance(EventBus.class);

		bus.register(createEventLogger());

		IRCControl control = injector.getInstance(IRCControl.class);

		// Initialize Features
		injector.getInstance(CleverBotFeature.class);

		Server server = control.getServer("irc.fyrechat.net", 6667, "TestBot",
				"password");
		try {
			// Initial actions
			server.connect();

			Channel channel = control.getChannel("#DemodLand");
			channel.join();

			control.ignore("SolidSnake");

			// Can add more actions here, but might want to use sleep()

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
