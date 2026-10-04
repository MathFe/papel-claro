package io.github.mathfe.friend.explain;

import java.util.Set;

/**
 * The document as the person sent it: pasted text, a photo, or both.
 * Everything is validated here, before anything reaches the model.
 */
public record DocumentInput(String text, byte[] imageBytes, String imageContentType) {

	/** Keeps the document inside the context window configured for the model. */
	public static final int MAX_TEXT_LENGTH = 16_000;

	private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

	public static DocumentInput of(String text, byte[] imageBytes, String imageContentType) {
		String cleanText = text == null ? "" : text.strip();
		boolean hasImage = imageBytes != null && imageBytes.length > 0;

		if (cleanText.isEmpty() && !hasImage) {
			throw new InvalidInputException("Cole o texto do documento ou envie uma foto dele.");
		}
		if (cleanText.length() > MAX_TEXT_LENGTH) {
			throw new InvalidInputException("O texto está muito grande. Cole só a parte que você quer entender (até "
					+ MAX_TEXT_LENGTH + " letras).");
		}
		if (hasImage && (imageContentType == null || !ALLOWED_IMAGE_TYPES.contains(imageContentType.toLowerCase()))) {
			throw new InvalidInputException("Envie a foto em JPG, PNG ou WEBP.");
		}
		return new DocumentInput(cleanText, hasImage ? imageBytes : null, hasImage ? imageContentType.toLowerCase() : null);
	}

	public boolean hasImage() {
		return imageBytes != null;
	}

	public boolean hasText() {
		return !text.isEmpty();
	}
}
