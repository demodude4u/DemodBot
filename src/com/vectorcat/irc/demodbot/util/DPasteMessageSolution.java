package com.vectorcat.irc.demodbot.util;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.List;

public class DPasteMessageSolution implements LongMessageSolution {

	private static final String pasteURL = "http://dpaste.com/api/v1/";

	public URL makePaste(String code, String name, String format)
			throws IOException {
		String content = URLEncoder.encode(code, "UTF-8");
		String title = URLEncoder.encode(name, "UTF-8");
		String data = "title=" + title + "&content=" + content;
		URL response = this.page(pasteURL, data);
		return response;
	}

	public URL page(String uri, String urlParameters) throws IOException {
		URL url;
		HttpURLConnection connection = null;
		try {
			// Create connection
			url = new URL(uri);
			connection = (HttpURLConnection) url.openConnection();
			connection.setDoOutput(true);
			connection.setRequestProperty("Accept-Charset", "UTF-8");
			connection.setRequestProperty("Content-Type",
					"application/x-www-form-urlencoded;charset=UTF-8");

			try (OutputStream output = connection.getOutputStream()) {
				output.write(urlParameters.getBytes("UTF-8"));
			}

			connection.getInputStream().close();

			return connection.getURL();

		} finally {
			if (connection != null) {
				connection.disconnect();
			}
		}
	}

	@Override
	public URL provideSolution(List<String> messages, String title)
			throws IOException {
		StringBuilder builder = new StringBuilder();
		for (String message : messages) {
			builder.append(message + "\n");
		}
		return makePaste(builder.toString(), title, "text");
	}

}
