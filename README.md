# Peace Space

**An offline PDF reader that explains words, phrases, and idioms in context, using a small language model running entirely on your machine.**

No app switching. No internet. No data leaving your device.

> **Status:** Concept / early development. Features below describe the intended design; see the [Roadmap](#roadmap) for what is built.

---

## Why this exists

When reading a PDF, every unfamiliar word or idiom forces a context switch: open a dictionary, Google, or a chatbot, then come back and find your place. That breaks reading flow.

Existing options fall short:

- **Dictionary apps** give word-level definitions but ignore context, and handle idioms and multi-word phrases poorly.
- **Chatbots** understand context but need internet, require leaving the reader, and send your text to a server.

Peace Space keeps lookup *inside* the reader: select text, get a contextual explanation instantly, keep reading.

## Features (planned)

- Select a word, phrase, or idiom in a PDF and get a short explanation **based on the surrounding sentence**, not just a generic dictionary entry
- Handles single words, multi-word expressions, idioms, and figurative language
- Fully offline after the one-time model download
- Private by design: no telemetry, no network calls during reading
- Short, skimmable answers (meaning, plain-English paraphrase of the sentence, optional example)
- Local vocabulary list: save looked-up words for later review

## How it works

```
 PDF (PDF.js)  ->  text selection + surrounding sentence/paragraph
                          |
                          v
              Prompt builder (word + context)
                          |
                          v
        Local SLM (quantized GGUF via llama.cpp)
                          |
                          v
        Popup explanation next to the selection
```

1. The PDF is rendered with a PDF engine (PDF.js).
2. On selection, the app extracts the selected text and the sentence (plus neighboring sentences) around it.
3. A prompt asks the model to explain the selection **as used in that context**.
4. The quantized model runs locally and streams the answer into a popup.

### Prompt sketch

```
You are a reading assistant. Explain the highlighted text as it is used in the
passage. Be brief and use simple English.

Passage: "{context}"
Highlighted: "{selection}"

Reply with:
Meaning: <one or two plain sentences>
In this sentence: <what it means here, if different from the usual meaning>
```

### Optional: dictionary grounding

Small models can hallucinate rare words. An optional local dictionary (e.g., WordNet or a Wiktionary dump) can supply candidate definitions that are passed to the model, which then picks and explains the one that fits the context. This keeps answers accurate while staying offline.

## Model choice

Peace Space needs a model that is strong at **English language nuance** (idioms, figurative use), small enough to run on a laptop CPU, and permissively licensed. Benchmarks for math and coding do not measure this, so the project includes a small evaluation set of sentences with idioms and ambiguous words.

| Model | Size (Q4 GGUF, approx.) | Notes |
|---|---|---|
| Gemma 4 edge variant (E4B) | under ~4 GB RAM | Primary candidate; verify exact model name, size, and license on release page |
| Gemma 3 4B | ~2.3 GB | Stable fallback, 128K context |
| Qwen3-4B | ~2.6 GB | Apache 2.0; benchmark against Gemma on idiom accuracy |
| Gemma 3 1B / Qwen3 1.7B | ~0.7-1.1 GB | For low-RAM machines |

The model is swappable: any GGUF model supported by llama.cpp can be dropped in via config.

## Tech stack (proposed)

- **Desktop shell:** Electron or Tauri
- **PDF rendering:** PDF.js
- **Inference:** llama.cpp (via `node-llama-cpp` or `llama-cpp-python`)
- **Model format:** GGUF, 4-bit quantized (Q4_K_M)
- **Storage:** local SQLite or JSON for saved words
- **Frontend:** vanilla HTML/CSS/JS

## Getting started

> Not yet available. Planned flow:

```bash
git clone https://github.com/Commander-Ayush/peace-space.git
cd peace-space
npm install
npm run download-model   # one-time download of the default GGUF model
npm start
```

After the model download, the app works with no internet connection.

## Roadmap

- [ ] PDF viewer with text selection
- [ ] Local model loading and inference via llama.cpp
- [ ] Context extraction (sentence + neighbors) and prompt builder
- [ ] Popup UI with streamed answers
- [ ] Evaluation set for idioms and context-dependent words; compare candidate models
- [ ] Saved-words list and export
- [ ] Optional dictionary grounding
- [ ] Model picker and settings (context window size, answer length)
- [ ] Packaged installers for Windows, macOS, Linux
- [ ] EPUB support

## Hardware expectations

- 8 GB RAM recommended for 4B-class models; 4 GB workable with 1B-2B models
- No GPU required; a GPU speeds up responses if available
- Disk: roughly 1-3 GB for the model

## Privacy

All processing happens on-device. The app makes no network requests while you read. The only network use is the optional one-time model download.

## Contributing

Ideas, bug reports, and pull requests are welcome. Good first contributions: adding test sentences to the evaluation set, trying other GGUF models, or improving the prompt.

## License

To be decided (MIT suggested for the app code). Model weights are subject to their own licenses.

## Author

Ayush ([@Commander-Ayush](https://github.com/Commander-Ayush))
