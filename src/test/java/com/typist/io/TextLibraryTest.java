package com.typist.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextLibraryTest {
    @TempDir
    Path tempDir;

    @Test
    void latestChangedFileNameIsMostRecentlyModified() throws Exception {
        Path older = tempDir.resolve("older.txt");
        Path newer = tempDir.resolve("newer.txt");
        Files.writeString(older, "a");
        Files.writeString(newer, "b");
        Files.setLastModifiedTime(older, FileTime.fromMillis(1_000));
        Files.setLastModifiedTime(newer, FileTime.fromMillis(2_000));

        TextLibrary library = new TextLibrary(tempDir);
        assertEquals("newer.txt", library.latestChangedFileName());
    }

    @Test
    void writeTextFileCreatesAndOverwrites() throws Exception {
        TextLibrary library = new TextLibrary(tempDir);
        Path created = library.writeTextFile("drill.txt", "first");
        assertEquals("first", Files.readString(created));
        library.writeTextFile("drill.txt", "second");
        assertEquals("second", Files.readString(created));
        assertEquals(1, library.listTextFiles().size());
    }

    @Test
    void normalizeFileNameAppendsTxtAndRejectsBlank() throws Exception {
        assertEquals("note.txt", TextLibrary.normalizeFileName("note"));
        assertEquals("keep.txt", TextLibrary.normalizeFileName("keep.txt"));
        IOException ex = assertThrows(IOException.class, () -> TextLibrary.normalizeFileName("  "));
        assertTrue(ex.getMessage().contains("empty"));
    }
}
