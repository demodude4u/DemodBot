package com.vectorcat.irc.demodbot;

import java.util.Arrays;

public class DemodBotMain {

	public static void main(String[] args) {

		try {
			DemodBot bot = new DemodBot(args[0], Integer.parseInt(args[1]),
					args[2], args[3], Arrays.copyOfRange(args, 4, args.length));

			bot.startAsync();
		} catch (Exception e) {
			System.out
					.println("Usage: DemodBotMain <host> <port> <name> <password> <channel1> [<channel2> (...) <channelN>]");

			System.out.println();
			e.printStackTrace();
		}

	}
}
