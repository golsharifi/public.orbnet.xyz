package com.orbvpn.api.utils;

import java.security.SecureRandom;

public class Utilities {

	public static String getRandomPassword(int len) {
		// ASCII range – alphanumeric (0-9, a-z, A-Z)

		final String uppers = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
		final String lowers = "abcdefghijklmnopqrstuvwxyz";
		final String numbers = "0123456789";
		final String symbols = ",./<>?!@#$%^&*()_+-=";

		SecureRandom random = new SecureRandom();
		StringBuilder sb = new StringBuilder();

		// each iteration of the loop randomly chooses a character from the given
		// ASCII range and appends it to the `StringBuilder` instance

		int randomIndex = random.nextInt(uppers.length());
		sb.append(uppers.charAt(randomIndex));

		for (int i = 1; i < len; i++) {
			randomIndex = random.nextInt(lowers.length());
			sb.append(lowers.charAt(randomIndex));
		}

		randomIndex = random.nextInt(numbers.length());
		sb.append(numbers.charAt(randomIndex));

		randomIndex = random.nextInt(symbols.length());
		sb.append(symbols.charAt(randomIndex));

		return sb.toString();
	}

	public static String getRandomUpperCaseString(int length) {

		String upperCase = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
		StringBuilder sb = new StringBuilder(length);

		for (int i = 0; i < length; i++) {
			int index = (int)(upperCase.length() * Math.random());
			sb.append(upperCase.charAt(index));
		}

		return sb.toString();
	}

	/**
	 * Converts a string to title case (first letter uppercase, rest lowercase for each word).
	 * Handles null, empty strings, and multiple words separated by spaces.
	 * Examples: "john" -> "John", "JOHN DOE" -> "John Doe", "mary jane" -> "Mary Jane"
	 * @param str the string to convert
	 * @return the title-cased string, or null if input is null
	 */
	public static String toTitleCase(String str) {
		if (str == null) {
			return null;
		}
		str = str.trim();
		if (str.isEmpty()) {
			return str;
		}

		StringBuilder result = new StringBuilder();
		String[] words = str.split("\\s+");

		for (int i = 0; i < words.length; i++) {
			if (i > 0) {
				result.append(" ");
			}
			String word = words[i];
			if (!word.isEmpty()) {
				result.append(Character.toUpperCase(word.charAt(0)));
				if (word.length() > 1) {
					result.append(word.substring(1).toLowerCase());
				}
			}
		}

		return result.toString();
	}
}
