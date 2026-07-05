/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.awt.html5.demo;

import java.awt.BorderLayout;
import java.awt.FlowLayout;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;


/**
 * Plain Swing demo application (no dependency on the toolkit — launch it
 * through {@code vavi.awt.html5.Main} to see it in a browser).
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-07-05 nsano initial version <br>
 */
public final class DemoApp {

    private DemoApp() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("vavi-awt-html5 demo");
            JPanel top = new JPanel(new FlowLayout());
            JLabel clock = new JLabel("--:--:--");
            JButton button = new JButton("Click me");
            JTextField field = new JTextField(20);
            JTextArea log = new JTextArea();
            log.setEditable(false);

            button.addActionListener(e -> log.append("button clicked, text: " + field.getText() + "\n"));
            field.addActionListener(e -> {
                log.append("entered: " + field.getText() + "\n");
                field.setText("");
            });
            new Timer(1000, e -> clock.setText(java.time.LocalTime.now().withNano(0).toString())).start();

            top.add(clock);
            top.add(field);
            top.add(button);
            frame.getContentPane().add(top, BorderLayout.NORTH);
            frame.getContentPane().add(new JScrollPane(log), BorderLayout.CENTER);
            frame.setSize(640, 480);
            frame.setLocation(100, 80);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setVisible(true);
        });
    }
}
