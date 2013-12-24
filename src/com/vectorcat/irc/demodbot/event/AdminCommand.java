package com.vectorcat.irc.demodbot.event;

import lombok.Data;

import com.vectorcat.irc.Target;
import com.vectorcat.irc.User;
import com.vectorcat.irc.util.Arguments;

@Data
public class AdminCommand {
	private final Target target;
	private final User user;
	private final String login;
	private final String hostname;
	private final String rawMessage;
	private final String message;
	private final boolean directedAtMe;
	private final String command;
	private final Arguments arguments;
}
