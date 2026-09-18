# Typist

A desktop speed-typing trainer (Swing) in the spirit of [Monkeytype](https://monkeytype.com/). Pick a text, type with live coloring, and review letter and bigram mistakes in SQLite-backed stats.

## Requirements

- **JDK 17** or newer
- **Apache Maven** 3.8+
- A graphical display (this is a Swing GUI)

## Get started

Clone or open the project, then run everything from the **repository root** so the app can find the `texts/` folder:

```bash
cd typist
mvn compile exec:java
```

Alternatively, build a single runnable jar:

```bash
mvn package
java -jar target/typist-1.0.0.jar
```

Run tests with `mvn test`.

## Add typing texts

Put plain `.txt` files in `texts/`. They show up as a flat list of file names (no subfolders). Sample files are already included (`pangram.txt`, `common-english.txt`, `programming.txt`).

The last text you selected is remembered and restored the next time you start the app.

## Typing

- Click the typing pane and type the highlighted text.
- **Correct** characters turn light; **errors** turn red.
- Mistyped **spaces** get a light pink background.
- **Backspace** undoes one character; **Ctrl+Backspace** jumps to the start of the current word.
- **Esc** restarts the current text and **does not** save an unfinished run.
- Finishing the text saves the session immediately. Esc after that starts a new attempt; the completed run stays in the database.

## Stats

The bottom panel tracks:

- **Current** row — WPM, accuracy, letter failures, and bigram failures for the run in progress (updates as you type)
- **Sessions** — saved runs; select a row to inspect that session’s letter and bigram failures
- **Monthly** — the same failure totals aggregated by calendar month
- **Word timings** — **Export** writes a JSON file (defaults to `~/Downloads/word_timings.json`) with WPM, accuracy, letter failures, bigram failures, and per-word timings.

Data lives in `typist.db` in the working directory (created automatically). Bigrams are the expected character plus the character before it (not recorded for the first character of a text).

Closing the window with **X** exits the process.
