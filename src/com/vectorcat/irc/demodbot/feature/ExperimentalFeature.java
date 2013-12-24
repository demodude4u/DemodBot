package com.vectorcat.irc.demodbot.feature;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.inject.Inject;
import com.vectorcat.irc.demodbot.event.help.FeatureRollCall;

public class ExperimentalFeature {

	@Inject
	ExperimentalFeature(EventBus bus) {
		bus.register(this);
	}

	@Subscribe
	public void onFeatureRollCall(FeatureRollCall event) {
		event.getResponder()
				.reply("Experimental Features",
						"This could have anything my creator is messing around with...");
	}
}
