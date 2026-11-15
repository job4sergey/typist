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

    public String latestChangedFileName() throws IOException {
        if (!Files.isDirectory(textsDir)) {
            return "";
        }
        try (Stream<Path> stream = Files.list(textsDir)) {
            return stream.filter(Files::isRegularFile)
                    .max(Comparator.comparingLong(TextLibrary::lastModifiedMillis))
                    .map(path -> path.getFileName().toString())
                    .orElse("");
        }
    }

    public Path writeTextFile(String fileName, String content) throws IOException {
        String name = normalizeFileName(fileName);
        Files.createDirectories(textsDir);
        Path dir = textsDir.toAbsolutePath().normalize();
        Path target = dir.resolve(name).normalize();
        if (!dir.equals(target.getParent())) {
            throw new IOException("Invalid file name: " + fileName);
        }
        Files.writeString(target, content == null ? "" : content, StandardCharsets.UTF_8);
        return target;
    }

    static String normalizeFileName(String fileName) throws IOException {
        if (fileName == null || fileName.isBlank()) {
            throw new IOException("File name is empty");
        }
        String name = Path.of(fileName.trim()).getFileName().toString().trim();
        if (name.isEmpty() || ".".equals(name) || "..".equals(name)) {
            throw new IOException("Invalid file name: " + fileName);
        }
        if (!name.toLowerCase().endsWith(".txt")) {
            name = name + ".txt";
        }
        return name;
    }

    private static long lastModifiedMillis(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException ex) {
            return Long.MIN_VALUE;
        }
    }
}
