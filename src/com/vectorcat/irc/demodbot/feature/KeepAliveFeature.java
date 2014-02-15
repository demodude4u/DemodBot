package com.vectorcat.irc.demodbot.feature;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.google.common.collect.Sets;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.util.concurrent.AbstractExecutionThreadService;
import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.Uninterruptibles;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.vectorcat.irc.Channel;
import com.vectorcat.irc.IRCControl;
import com.vectorcat.irc.IRCState;
import com.vectorcat.irc.demodbot.event.help.FeatureRollCall;
import com.vectorcat.irc.event.IRCServerDisconnect;
import com.vectorcat.irc.event.IRCServerExceptionEvent;
import com.vectorcat.irc.event.recv.IRCRecvJoin;
import com.vectorcat.irc.event.recv.IRCRecvPart;

@Singleton
public class KeepAliveFeature {

	private final IRCControl control;
	private final IRCState state;

	private volatile Service reconnectService = null;

	private final Set<Channel> channels = Sets.newLinkedHashSet();

	@Inject
	public KeepAliveFeature(HelpFeature helpFeature, EventBus bus,
			IRCControl control, IRCState state) {
		this.control = control;
		this.state = state;

		bus.register(this);
	}

	private synchronized void checkOrReconnect() {
		if (control.isServerPresent() && !control.isConnected()) {
			if (reconnectService == null || !reconnectService.isRunning()) {
				reconnectService = new AbstractExecutionThreadService() {
					@Override
					protected void run() throws Exception {
						while (control.isServerPresent()
								&& !control.isConnected()) {
							Uninterruptibles.sleepUninterruptibly(1,
									TimeUnit.SECONDS);
							System.out.println("\tReconnecting...");
							try {
								control.reconnectServer();
								System.out.println("Connected!");
								for (Channel channel : channels) {
									try {
										channel.join();
									} catch (Exception e) {
										System.err.println("Error joining "
												+ channel + " : "
												+ e.getClass().getSimpleName());
									}
								}
							} catch (Exception e) {
								System.err
										.println(e.getClass().getSimpleName());
							}
						}
					}
				};
				reconnectService.startAsync();
			}
		}
	}

	@Subscribe
	public void onFeatureRollCall(FeatureRollCall event) {
		event.getResponder()
				.reply("Keep Alive",
						"This feature simply tries to reconnect to the current server if there was any issue in the connection.");
	}

	@Subscribe
	public void onIRCServerDisconnect(IRCServerDisconnect event) {
		checkOrReconnect();
	}

	@Subscribe
	public void onIRCServerExceptionEvent(IRCServerExceptionEvent event) {
		checkOrReconnect();
	}

	@Subscribe
	public void onJoin(IRCRecvJoin event) {
		if (state.getMyUser().equals(event.getUser())) {
			channels.add(event.getChannel());
		}
	}

	@Subscribe
	public void onPart(IRCRecvPart event) {
		if (state.getMyUser().equals(event.getUser())) {
			channels.remove(event.getChannel());
		}
	}

}
