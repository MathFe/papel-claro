package io.github.mathfe.friend.web;

import java.util.Map;

import io.github.mathfe.friend.explain.InvalidInputException;
import io.github.mathfe.friend.explain.ModelException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/** Turns failures into short messages a non-technical person can act on. */
@RestControllerAdvice
public class ApiExceptionHandler {

	private final String modelName;

	public ApiExceptionHandler(@Value("${app.model}") String modelName) {
		this.modelName = modelName;
	}

	@ExceptionHandler(InvalidInputException.class)
	public ResponseEntity<Map<String, String>> invalidInput(InvalidInputException ex) {
		return error(HttpStatus.BAD_REQUEST, ex.getMessage());
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ResponseEntity<Map<String, String>> tooLarge(MaxUploadSizeExceededException ex) {
		// 413 by number: the constant for it was renamed between Spring versions.
		return ResponseEntity.status(413).body(Map.of("error", "A foto está muito grande. Tente uma foto menor."));
	}

	@ExceptionHandler(ModelException.class)
	public ResponseEntity<Map<String, String>> model(ModelException ex) {
		return switch (ex.reason()) {
			case OFFLINE -> error(HttpStatus.SERVICE_UNAVAILABLE,
					"Não consegui falar com o Ollama. Abra o Ollama neste computador e tente de novo.");
			case MODEL_MISSING -> error(HttpStatus.SERVICE_UNAVAILABLE,
					"O modelo ainda não foi baixado. No terminal, rode: ollama pull " + this.modelName);
			case BAD_ANSWER -> error(HttpStatus.BAD_GATEWAY,
					"Não consegui entender a resposta do modelo desta vez. Tente de novo.");
		};
	}

	private static ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
		return ResponseEntity.status(status).body(Map.of("error", message));
	}
}
