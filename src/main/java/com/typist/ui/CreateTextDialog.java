package com.typist.ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.util.Optional;

final class CreateTextDialog extends JDialog {
    record Draft(String fileName, String content) {
    }

    private Draft result;

    private CreateTextDialog(Frame owner, String suggestedFileName, String clipboardText) {
        super(owner, "Create text", true);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        JTextField fileNameField = new JTextField(suggestedFileName == null ? "" : suggestedFileName);
        fileNameField.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        fileNameField.setBackground(Theme.SURFACE);
        fileNameField.setForeground(Theme.TEXT_CORRECT);
        fileNameField.setCaretColor(Theme.CARET);
        fileNameField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.TEXT_PENDING),
                BorderFactory.createEmptyBorder(4, 6, 4, 6)
        ));

        JLabel fileLabel = new JLabel("File name");
        fileLabel.setForeground(Theme.MUTED);
        JPanel fileRow = new JPanel(new BorderLayout(8, 0));
        fileRow.setOpaque(false);
        fileRow.add(fileLabel, BorderLayout.WEST);
        fileRow.add(fileNameField, BorderLayout.CENTER);

        JTextArea body = new JTextArea(clipboardText == null ? "" : clipboardText);
        body.setLineWrap(true);
        body.setWrapStyleWord(true);
        body.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        body.setBackground(Theme.SURFACE);
        body.setForeground(Theme.TEXT_CORRECT);
        body.setCaretColor(Theme.CARET);
        body.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        JScrollPane bodyScroll = new JScrollPane(body);
        bodyScroll.setBorder(BorderFactory.createLineBorder(Theme.TEXT_PENDING));

        JButton importButton = new JButton("Import");
        JButton cancelButton = new JButton("Cancel");
        importButton.setBackground(Theme.SURFACE);
        importButton.setForeground(Theme.ACCENT);
        cancelButton.setBackground(Theme.SURFACE);
        cancelButton.setForeground(Theme.MUTED);
        importButton.addActionListener(event -> {
            result = new Draft(fileNameField.getText(), body.getText());
            dispose();
        });
        cancelButton.addActionListener(event -> dispose());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(importButton);
        buttons.add(cancelButton);

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBackground(Theme.BACKGROUND);
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        content.add(fileRow, BorderLayout.NORTH);
        content.add(bodyScroll, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);
        getRootPane().setDefaultButton(importButton);

        setPreferredSize(new Dimension(640, 480));
        pack();
        setLocationRelativeTo(owner);
        fileNameField.selectAll();
    }

    static Optional<Draft> show(Frame owner, String suggestedFileName) {
        CreateTextDialog dialog = new CreateTextDialog(owner, suggestedFileName, clipboardText());
        dialog.setVisible(true);
        return Optional.ofNullable(dialog.result);
    }

    static String clipboardText() {
        try {
            var clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                Object data = clipboard.getData(DataFlavor.stringFlavor);
                return data == null ? "" : data.toString();
            }
        } catch (Exception ignored) {
            // clipboard may be empty or unavailable
        }
        return "";
    }
}
