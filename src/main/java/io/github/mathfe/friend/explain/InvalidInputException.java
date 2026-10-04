package io.github.mathfe.friend.explain;

/** The person sent something we cannot work with. The message is shown on screen. */
public class InvalidInputException extends RuntimeException {

	public InvalidInputException(String message) {
		super(message);
	}
}
