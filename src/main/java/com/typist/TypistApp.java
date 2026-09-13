package com.typist;

import com.typist.db.StatsRepository;
import com.typist.io.TextLibrary;
import com.typist.ui.MainFrame;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.nio.file.Path;

public final class TypistApp {
    public static void main(String[] args) throws Exception {
        Path root = Path.of("").toAbsolutePath();
        Path textsDir = root.resolve("texts");
        Path dbFile = root.resolve("typist.db");

        StatsRepository stats = new StatsRepository(dbFile);
        TextLibrary texts = new TextLibrary(textsDir);

        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // keep default look and feel
            }
            MainFrame frame = new MainFrame(texts, stats);
            frame.setSize(1100, 760);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }

    private TypistApp() {
    }
}
