package io.github.mathfe.friend.explain;

/** Something went wrong while talking to the local model. */
public class ModelException extends RuntimeException {

	public enum Reason {
		/** Ollama is not running or cannot be reached. */
		OFFLINE,
		/** Ollama is running but the Gemma model was not downloaded. */
		MODEL_MISSING,
		/** The model answered, but not in a shape we could read. */
		BAD_ANSWER
	}

	private final Reason reason;

	public ModelException(Reason reason, Throwable cause) {
		super(reason.name(), cause);
		this.reason = reason;
	}

	public Reason reason() {
		return reason;
	}
}
