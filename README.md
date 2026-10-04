# Papel Claro

**Paste a confusing document, or take a photo of it, and get it explained in plain Brazilian Portuguese.**
Everything runs on your own computer with [Gemma](https://ai.google.dev/gemma) through [Ollama](https://ollama.com). The document never leaves the machine.

Built for the DEV [Hacktoberfest Weekend Challenge: Build for a Friend](https://dev.to/devteam/join-the-hacktoberfest-weekend-challenge-build-for-a-friend-2450-in-prizes-across-17-winners-1aj5).

## What it does

Bank letters, rental contracts, collection notices and official notifications in Brazil are written in dense legal Portuguese ("juridiquês"). Papel Claro reads the document and answers the questions a person actually has:

- **What is this?** The type of document and a summary in two to four short sentences.
- **What do I have to do?** A numbered list of actions.
- **By when, and how much?** Dates and amounts, copied exactly as they appear.
- **What should I watch out for?** Fines, interest, automatic renewal, and common signs of a scam.
- **What do these words mean?** A small glossary of the hard terms in the document.
- **Follow-up questions**, answered only from the document.
- **Read aloud**, using a voice installed on the device.

The page uses large type and plain wording, and works on a phone connected to the same Wi-Fi as the computer.

## Why open source and local

- **Privacy.** These documents contain names, debts, addresses and ID numbers. With a local open-weight model, nothing is uploaded anywhere and nothing is stored. The app has no database and does not log document content.
- **No cost, no account.** No API key, no subscription, no usage limit.
- **Works offline** once the model is downloaded.
- **Anyone can inspect it.** The prompts and the code are all in this repository.

## How it works

```
Browser  ──►  Spring Boot (Java)  ──►  Spring AI  ──►  Ollama  ──►  Gemma (on your GPU/CPU)
 text or photo        validates input      builds the prompt,        local inference
                                           maps JSON to records
```

- **Gemma** (`gemma3:4b` by default) reads text and images, so a photo of a letter works without a separate OCR step.
- **Spring AI** `ChatClient` sends the prompt and maps the JSON answer onto Java records.
- The photo is resized in the browser before upload, so it stays small and fast to read.
- If the model returns JSON that cannot be read, the call is retried once.

## Run it

Requirements: Java 17 or newer, and Ollama.

```bash
ollama pull gemma3:4b
./mvnw spring-boot:run        # Windows: .\mvnw spring-boot:run
```

Open <http://localhost:8080>. To use it from a phone on the same Wi-Fi, open `http://<computer-ip>:8080`.

To use a different Gemma, set `GEMMA_MODEL` before starting:

```powershell
$env:GEMMA_MODEL="gemma4:12b"
```

Run the unit tests (no Ollama needed):

```bash
./mvnw test
```

## Limits

- This is a reading aid, **not legal advice**. A small local model can misread a document. Check anything important with someone you trust before signing or paying.
- Text is limited to 16,000 characters per document.
- Photos need to be sharp and well lit. Handwriting is unreliable.
- The interface and the explanations are in Portuguese only.

## License

[MIT](LICENSE)
