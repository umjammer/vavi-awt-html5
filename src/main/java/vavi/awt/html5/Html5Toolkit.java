/*
 * Copyright (c) 2023 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.awt.html5;

import java.awt.AWTException;
import java.awt.Desktop;
import java.awt.Frame;
import java.awt.GraphicsDevice;
import java.awt.HeadlessException;
import java.awt.PrintJob;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.Window;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.InvalidDnDOperationException;
import java.awt.dnd.peer.DragSourceContextPeer;
import java.awt.font.TextAttribute;
import java.awt.im.InputMethodHighlight;
import java.awt.im.spi.InputMethodDescriptor;
import java.awt.image.ColorModel;
import java.awt.peer.DesktopPeer;
import java.awt.peer.FontPeer;
import java.awt.peer.FramePeer;
import java.awt.peer.RobotPeer;
import java.awt.peer.SystemTrayPeer;
import java.awt.peer.TrayIconPeer;
import java.util.Map;
import java.util.Properties;

import com.github.caciocavallosilano.cacio.peer.CacioToolkit;
import com.github.caciocavallosilano.cacio.peer.PlatformWindowFactory;
import com.github.caciocavallosilano.cacio.peer.managed.FullScreenWindowFactory;
import sun.awt.LightweightFrame;
import sun.awt.datatransfer.DataTransferer;


/**
 * AWT toolkit rendering into an off-screen framebuffer that is streamed
 * to a browser over WebTransport.
 * <p>
 * Uses cacio's managed window path: all toplevel windows are managed by
 * {@link FullScreenWindowFactory} inside one virtual screen
 * ({@link Html5Screen}), sized by the {@code cacio.managed.screensize}
 * system property.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2023-06-14 nsano initial version <br>
 */
public class Html5Toolkit extends CacioToolkit {

    private PlatformWindowFactory platformWindowFactory;

    public Html5Toolkit() {
        setDecorateWindows(true);
        System.setProperty("swing.defaultlaf", "javax.swing.plaf.metal.MetalLookAndFeel");
    }

    @Override
    public PlatformWindowFactory getPlatformWindowFactory() {
        if (platformWindowFactory == null) {
            Html5Screen screen = Html5Screen.getInstance();
            Html5EventSource eventSource = Html5EventSource.getInstance();
            platformWindowFactory = new FullScreenWindowFactory(screen, eventSource);
        }
        return platformWindowFactory;
    }

    @Override
    public InputMethodDescriptor getInputMethodAdapterDescriptor() throws AWTException {
        return null;
    }

    @Override
    public DragSourceContextPeer createDragSourceContextPeer(DragGestureEvent dge) throws InvalidDnDOperationException {
        return null;
    }

    @Override
    public TrayIconPeer createTrayIcon(TrayIcon target) throws HeadlessException, AWTException {
        return null;
    }

    @Override
    public SystemTrayPeer createSystemTray(SystemTray target) {
        return null;
    }

    @Override
    public boolean isTraySupported() {
        return false;
    }

    @Override
    public FontPeer getFontPeer(String name, int style) {
        return null;
    }

    @Override
    public RobotPeer createRobot(GraphicsDevice screen) throws AWTException {
        return new Html5RobotPeer();
    }

    protected int getScreenWidth() {
        return FullScreenWindowFactory.getScreenDimension().width;
    }

    protected int getScreenHeight() {
        return FullScreenWindowFactory.getScreenDimension().height;
    }

    @Override
    protected boolean syncNativeQueue(long timeout) {
        return false;
    }

    @Override
    public void grab(Window w) {
    }

    @Override
    public void ungrab(Window w) {
    }

    @Override
    public boolean isDesktopSupported() {
        return false;
    }

    @Override
    public DesktopPeer createDesktopPeer(Desktop target) throws HeadlessException {
        return null;
    }

    @Override
    public ColorModel getColorModel() throws HeadlessException {
        return Html5Screen.getInstance().getColorModel();
    }

    @Override
    public void sync() {
    }

    @Override
    public PrintJob getPrintJob(Frame frame, String jobtitle, Properties props) {
        return null;
    }

    @Override
    public void beep() {
        vavi.awt.html5.sound.Html5AudioSystem.beep();
    }

    @Override
    public Map<TextAttribute, ?> mapInputMethodHighlight(InputMethodHighlight highlight) throws HeadlessException {
        return null;
    }

    @Override
    public FramePeer createLightweightFrame(LightweightFrame lightweightFrame) throws HeadlessException {
        return null;
    }

    @Override
    public DataTransferer getDataTransferer() {
        return null;
    }

    @Override
    public boolean isTaskbarSupported() {
        return false;
    }
}
