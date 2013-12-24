package com.vectorcat.irc.demodbot.util;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.List;

import com.google.inject.Inject;
import com.google.inject.name.Named;

//https://code.google.com/p/pastebin-click/source/browse/src/com/pastebinclick/API.java (Modified)
public class PasteBinMessageSolution implements LongMessageSolution {

	private static final String pasteURL = "http://www.pastebin.com/api/api_post.php";

	private final String apiKey;

	@Inject
	public PasteBinMessageSolution(@Named("Pastebin API Key") String apiKey) {
		this.apiKey = apiKey;
	}

	public URL makePaste(String code, String name, String format)
			throws IOException {
		String content = URLEncoder.encode(code, "UTF-8");
		String title = URLEncoder.encode(name, "UTF-8");
		String data = "api_option=paste&api_user_key="
				+ "&api_paste_private=1&api_paste_name=" + title
				+ "&api_paste_expire_date=N&api_paste_format=" + format
				+ "&api_dev_key=" + this.apiKey + "&api_paste_code=" + content;
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
			connection.setRequestMethod("POST");
			connection.setRequestProperty("Content-Type",
					"application/x-www-form-urlencoded");

			connection.setRequestProperty("Content-Length",
					"" + Integer.toString(urlParameters.getBytes().length));
			connection.setRequestProperty("Content-Language", "en-US");

			connection.setUseCaches(false);
			connection.setDoInput(true);
			connection.setDoOutput(true);

			// Send request
			DataOutputStream wr = new DataOutputStream(
					connection.getOutputStream());
			wr.writeBytes(urlParameters);
			wr.flush();
			wr.close();

			// Get Response
			InputStream is = connection.getInputStream();
			try (BufferedReader rd = new BufferedReader(new InputStreamReader(
					is))) {
				String line;
				StringBuilder builder = new StringBuilder();
				while ((line = rd.readLine()) != null) {
					builder.append(line);
				}
				String response = builder.toString();

				if (response.startsWith("Bad")) {
					throw new IOException(response);
				}

				return new URL(response);
			}

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
