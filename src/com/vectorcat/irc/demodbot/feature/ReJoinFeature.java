package com.vectorcat.irc.demodbot.feature;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.common.collect.Sets;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.inject.Inject;
import com.vectorcat.irc.Channel;
import com.vectorcat.irc.IRCState;
import com.vectorcat.irc.demodbot.event.help.FeatureRollCall;
import com.vectorcat.irc.event.recv.IRCRecvKick;
import com.vectorcat.irc.exception.IRCBannedFromChannelException;
import com.vectorcat.irc.exception.IRCNoSuchChannelException;

public class ReJoinFeature {

	private final ExecutorService service = Executors.newCachedThreadPool();
	private final Set<Channel> tryingChannels = Sets.newConcurrentHashSet();
	private final IRCState state;

	@Inject
	ReJoinFeature(EventBus bus, IRCState state) {
		this.state = state;
		bus.register(this);
	}

	@Subscribe
	public void onFeatureRollCall(FeatureRollCall event) {
		event.getResponder()
				.reply("Re-Join Feature",
						"If I'm kicked by Peter, this feature will try to get me back into the ",
						"chat room.",
						"Admins, don't feel bad for kicking me or banning me momentarilly.",
						"I will always keep trying to come back to whatever channels I have been ",
						"kicked from.");
	}

	@Subscribe
	public void onKick(final IRCRecvKick event) {
		if (!state.getMyUser().equals(event.getRecipient())) {
			return;
		}
		service.submit(new Callable<Boolean>() {
			@Override
			public Boolean call() throws Exception {
				if (tryingChannels.contains(event.getChannel())) {
					return null;
				} else {
					tryingChannels.add(event.getChannel());
				}
				try {
					while (true) {
						try {
							event.getChannel().join();
							return true;
						} catch (IRCNoSuchChannelException e) {
							return false;
						} catch (IRCBannedFromChannelException e) {
							// Keep on trying!
						} catch (IOException e) {
							return false;
						}
						Thread.sleep(5000);
					}
				} finally {
					tryingChannels.remove(event.getChannel());
				}
			}
		});
	}
}
