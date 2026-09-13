package com.typist.io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public final class TextLibrary {
    private final Path textsDir;

    public TextLibrary(Path textsDir) {
        this.textsDir = textsDir;
    }

    public Path getTextsDir() {
        return textsDir;
    }

    public List<Path> listTextFiles() throws IOException {
        if (!Files.isDirectory(textsDir)) {
            return List.of();
        }
        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.list(textsDir)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".txt"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase()))
                    .forEach(files::add);
        }
        return files;
    }

    public String read(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8).strip();
    }
}
