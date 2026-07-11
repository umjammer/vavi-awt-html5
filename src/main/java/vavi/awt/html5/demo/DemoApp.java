/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.awt.html5.demo;

import java.awt.BorderLayout;
import java.awt.FlowLayout;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
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

            JSlider slider = new JSlider(0, 100, 25);
            JButton tone = new JButton("Tone");
            tone.addActionListener(e -> {
                log.append("playing tone\n");
                Thread t = new Thread(DemoApp::playTone, "demo-tone");
                t.setDaemon(true);
                t.start();
            });
            button.addActionListener(e -> log.append("button clicked, text: " + field.getText() + "\n"));
            field.addActionListener(e -> {
                log.append("entered: " + field.getText() + "\n");
                field.setText("");
            });
            slider.addChangeListener(e -> log.append("slider = " + slider.getValue() + "\n"));
            new Timer(1000, e -> clock.setText(java.time.LocalTime.now().withNano(0).toString())).start();

            top.add(clock);
            top.add(field);
            top.add(button);
            top.add(slider);
            top.add(tone);
            frame.getContentPane().add(top, BorderLayout.NORTH);
            frame.getContentPane().add(new JScrollPane(log), BorderLayout.CENTER);
            frame.setSize(640, 480);
            frame.setLocation(100, 80);
            // dispose rather than exit: closing the mirrored window must not
            // take the mirror server down with it
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.setVisible(true);
        });
    }

    /** one second of 440 Hz through plain javax.sound (mirrored to the browser under Main) */
    private static void playTone() {
        AudioFormat fmt = new AudioFormat(22050f, 16, 1, true, true);
        try (SourceDataLine line = AudioSystem.getSourceDataLine(fmt)) {
            line.open(fmt);
            line.start();
            int frames = 22050;
            byte[] buf = new byte[frames * 2];
            for (int i = 0; i < frames; i++) {
                double env = Math.min(1.0, Math.min(i, frames - i) / (0.05 * frames));
                short v = (short) (Math.sin(2 * Math.PI * 440 * i / 22050) * 0.4 * 32767 * env);
                buf[2 * i] = (byte) (v >> 8);
                buf[2 * i + 1] = (byte) v;
            }
            line.write(buf, 0, buf.length);
            line.drain();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
