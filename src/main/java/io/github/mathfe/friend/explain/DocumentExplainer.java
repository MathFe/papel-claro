package io.github.mathfe.friend.explain;

import java.net.ConnectException;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

/**
 * Sends the document to Gemma (running locally through Ollama) and brings back
 * a plain-language explanation. The document never leaves this machine.
 */
@Service
public class DocumentExplainer {

	private static final Logger log = LoggerFactory.getLogger(DocumentExplainer.class);

	private static final String SYSTEM_PROMPT = """
			Você é o Papel Claro, um assistente que explica documentos difíceis (contratos, cartas de banco, \
			boletos, cobranças, comunicados, notificações) para pessoas sem formação jurídica, em português \
			do Brasil simples e respeitoso.

			Regras:
			- Use frases curtas e palavras do dia a dia, como se explicasse para um parente querido.
			- Use SOMENTE o que está no documento. Nunca invente datas, valores, nomes ou prazos. \
			Se algo importante não aparece, diga que o documento não informa.
			- Copie datas e valores exatamente como aparecem no documento.
			- Você não substitui um advogado. Quando fizer sentido, sugira procurar o Procon, a Defensoria \
			Pública, um advogado ou os canais oficiais da empresa.
			- Só fale em golpe se o próprio documento tiver um sinal claro disso (pede senha ou código, traz \
			link estranho, exige pagamento com pressa exagerada para uma pessoa física, tem erros grosseiros de \
			escrita). Se o documento não tem nenhum desses sinais, não fale de golpe.
			- Responda sempre em JSON válido, com os textos em português.
			""";

	// Only "document" and "question" are placeholders. The document itself goes in as a
	// parameter, so braces inside a contract are never read as template syntax.
	private static final String EXPLAIN_TEMPLATE = """
			Explique o documento abaixo para a pessoa que o recebeu.

			Preencha os campos assim:
			- documentType: que tipo de documento é (exemplo: Contrato de aluguel, Boleto de cobrança).
			- summary: de 2 a 4 frases simples dizendo o que o documento quer dizer.
			- actions: o que a pessoa precisa fazer, em ordem, uma ação por item. Lista vazia se não há nada a fazer.
			- deadlines: datas e prazos que aparecem. Cada item tem date (a data como está escrita) e description \
			(o que acontece nessa data).
			- amounts: todos os valores em dinheiro e percentuais que aparecem. Cada item tem value (copiado como \
			está escrito, com R$ ou %) e description (a que se refere).
			- warnings: riscos que o próprio documento traz para a pessoa, como multa, juros, perda de direitos ou \
			nome negativado. Não inclua conselhos genéricos. Lista vazia se não há nenhum.
			- glossary: até 5 palavras difíceis copiadas do documento. Em cada item, term é a palavra difícil e \
			meaning é a explicação dela em palavras simples.
			- urgency: ALTA se há prazo curto ou ameaça séria (processo, despejo, corte, nome negativado), \
			BAIXA se é só um aviso sem nada a fazer, MEDIA nos outros casos.

			DOCUMENTO:
			<<<
			{document}
			>>>
			""";

	private static final String ASK_TEMPLATE = """
			Responda à pergunta usando SOMENTE o documento abaixo, em 1 a 4 frases simples.
			Se o documento não traz a resposta, diga isso com clareza e não tente adivinhar.

			Preencha os campos assim:
			- answer: a resposta para a pessoa.
			- foundInDocument: true se a resposta está no documento, false se não está.

			PERGUNTA: {question}

			DOCUMENTO:
			<<<
			{document}
			>>>
			""";

	private static final String IMAGE_ONLY = "(O documento está na imagem enviada. Leia a imagem com atenção.)";

	private final ChatClient chatClient;

	public DocumentExplainer(ChatClient.Builder builder) {
		this.chatClient = builder.defaultSystem(SYSTEM_PROMPT).build();
	}

	public DocumentExplanation explain(DocumentInput input) {
		return callModel(() -> this.chatClient.prompt()
				.user(user -> {
					user.text(EXPLAIN_TEMPLATE).param("document", documentText(input));
					attachImage(user, input);
				})
				.call()
				.entity(DocumentExplanation.class), explanation -> !explanation.summary().isEmpty());
	}

	public Answer ask(DocumentInput input, String question) {
		return callModel(() -> this.chatClient.prompt()
				.user(user -> {
					user.text(ASK_TEMPLATE).param("document", documentText(input)).param("question", question);
					attachImage(user, input);
				})
				.call()
				.entity(Answer.class), answer -> !answer.answer().isEmpty());
	}

	private static String documentText(DocumentInput input) {
		return input.hasText() ? input.text() : IMAGE_ONLY;
	}

	private static void attachImage(ChatClient.PromptUserSpec user, DocumentInput input) {
		if (input.hasImage()) {
			user.media(MimeTypeUtils.parseMimeType(input.imageContentType()),
					new ByteArrayResource(input.imageBytes()));
		}
	}

	/**
	 * Small models occasionally return JSON we cannot read, so we try twice.
	 * If Ollama is not there at all, a second try will not help and we stop right away.
	 */
	private <T> T callModel(Supplier<T> call, Predicate<T> usable) {
		RuntimeException lastFailure = null;
		for (int attempt = 1; attempt <= 2; attempt++) {
			try {
				T result = call.get();
				if (result != null && usable.test(result)) {
					return result;
				}
				log.warn("Model call gave an empty answer (attempt {})", attempt);
			}
			catch (RuntimeException ex) {
				// Log the kind of failure only. Exception messages can quote the model's answer,
				// and the answer quotes the document, so the message is never logged.
				ModelException.Reason reason = classify(ex);
				log.warn("Model call failed (attempt {}): {} ({})", attempt, reason, ex.getClass().getSimpleName());
				lastFailure = ex;
				if (reason != ModelException.Reason.BAD_ANSWER) {
					throw new ModelException(reason, ex);
				}
			}
		}
		throw new ModelException(ModelException.Reason.BAD_ANSWER, lastFailure);
	}

	static ModelException.Reason classify(Throwable failure) {
		for (Throwable t = failure; t != null; t = t.getCause()) {
			if (t instanceof ConnectException) {
				return ModelException.Reason.OFFLINE;
			}
			String message = t.getMessage() == null ? "" : t.getMessage().toLowerCase();
			if (message.contains("connection refused")) {
				return ModelException.Reason.OFFLINE;
			}
			if (message.contains("not found") && message.contains("model")) {
				return ModelException.Reason.MODEL_MISSING;
			}
			if (t.getCause() == t) {
				break;
			}
		}
		return ModelException.Reason.BAD_ANSWER;
	}
}
