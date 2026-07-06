/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.awt.html5;

import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.IllegalComponentStateException;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JMenuBar;
import javax.swing.SwingUtilities;

import com.github.caciocavallosilano.cacio.peer.CacioEventSource;
import com.github.caciocavallosilano.cacio.peer.managed.EventData;


/**
 * Input event queue feeding cacio's managed event pump. Producers are the
 * WebTransport input decoder (browser events) and tests. Only the primitive
 * events (mouse pressed/released/moved/wheel, key pressed/released/typed)
 * are posted here; the managed window container synthesizes the rest
 * (enter/exit/click/focus/window events).
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-07-05 nsano initial version <br>
 */
public class Html5EventSource implements CacioEventSource {

    private static Html5EventSource instance;

    public static synchronized Html5EventSource getInstance() {
        if (instance == null) {
            instance = new Html5EventSource();
        }
        return instance;
    }

    private final BlockingQueue<EventData> queue = new LinkedBlockingQueue<>();

    /** the decorated window currently being moved by a title-bar drag, or null */
    private Window dragWindow;
    /** grab point inside {@link #dragWindow} at the moment the drag started */
    private int grabOffsetX, grabOffsetY;
    /** window origin the client currently shows it at (tracks the copy chain) */
    private int dragCurX, dragCurY;
    /** {@link #dragWindow} size, captured at press */
    private int dragW, dragH;

    private Html5EventSource() {
    }

    /**
     * Intercepts a left-button press-drag-release on a decorated window's
     * title bar and moves the window itself.
     * <p>
     * cacio draws L&amp;F window decorations on an internal proxy window whose
     * peer {@code setBounds} is a no-op, so its built-in title-bar drag moves
     * nothing. We therefore hit-test the title bar here and drive
     * {@link Window#setLocation} on the real {@link Frame}/{@link Dialog}. The
     * initial press is still forwarded (so the window activates and focuses
     * normally); only the drag and release are consumed.
     *
     * @return {@code true} when the event was consumed by a window move
     */
    private boolean handleTitleBarDrag(int id, int x, int y, int button) {
        if (id == MouseEvent.MOUSE_PRESSED) {
            dragWindow = null;
            if (button == MouseEvent.BUTTON1_DOWN_MASK) {
                Window w = findTitleBarWindow(x, y);
                if (w != null) {
                    try {
                        Point loc = w.getLocationOnScreen();
                        Dimension size = w.getSize();
                        dragWindow = w;
                        grabOffsetX = x - loc.x;
                        grabOffsetY = y - loc.y;
                        dragCurX = loc.x;
                        dragCurY = loc.y;
                        dragW = size.width;
                        dragH = size.height;
                    } catch (IllegalComponentStateException e) {
                        dragWindow = null;
                    }
                }
            }
            return false;
        }
        if (dragWindow == null) {
            return false;
        }
        if (id == MouseEvent.MOUSE_DRAGGED) {
            Window w = dragWindow;
            int nx = x - grabOffsetX;
            int ny = y - grabOffsetY;
            // shift is relative to where the client currently shows the window
            // (the copy chain), not the async setLocation that lags behind it
            int cdx = nx - dragCurX;
            int cdy = ny - dragCurY;
            if (cdx != 0 || cdy != 0) {
                Html5Screen.getInstance().hintCopyArea(dragCurX, dragCurY, dragW, dragH, cdx, cdy);
                dragCurX = nx;
                dragCurY = ny;
            }
            SwingUtilities.invokeLater(() -> w.setLocation(nx, ny));
            return true;
        }
        if (id == MouseEvent.MOUSE_RELEASED) {
            dragWindow = null;
            return true;
        }
        return false;
    }

    /** the topmost decorated Frame/Dialog whose title bar contains (x, y), or null */
    private static Window findTitleBarWindow(int x, int y) {
        Window best = null;
        for (Window w : Window.getWindows()) {
            // only real toplevels carry a title bar; this also excludes cacio's
            // decoration proxy windows, which are plain Windows
            if (!(w instanceof Frame || w instanceof Dialog)) {
                continue;
            }
            if (!w.isVisible() || !w.isShowing()) {
                continue;
            }
            Point loc;
            try {
                loc = w.getLocationOnScreen();
            } catch (IllegalComponentStateException e) {
                continue;
            }
            Dimension size = w.getSize();
            if (x < loc.x || x >= loc.x + size.width || y < loc.y || y >= loc.y + size.height) {
                continue;
            }
            Insets in = w.getInsets();
            int border = Math.max(1, in.left);
            // the title pane occupies the top inset minus any menu bar below it
            int titleBottom = in.top - menuBarHeight(w);
            int relX = x - loc.x;
            int relY = y - loc.y;
            if (relY < border || relY >= titleBottom) {
                continue;
            }
            if (relX < border || relX >= size.width - border) {
                continue;
            }
            if (best == null || w.isActive()) {
                best = w;
            }
        }
        return best;
    }

    private static int menuBarHeight(Window w) {
        JMenuBar mb = null;
        if (w instanceof JFrame jf) {
            mb = jf.getJMenuBar();
        } else if (w instanceof JDialog jd) {
            mb = jd.getJMenuBar();
        }
        if (mb != null && mb.isVisible()) {
            int h = mb.getHeight();
            return h > 0 ? h : mb.getPreferredSize().height;
        }
        return 0;
    }

    @Override
    public EventData getNextEvent() throws InterruptedException {
        return queue.take();
    }

    public void postEvent(EventData ev) {
        queue.offer(ev);
    }

    /**
     * @param id {@link MouseEvent#MOUSE_PRESSED}, {@link MouseEvent#MOUSE_RELEASED},
     *           {@link MouseEvent#MOUSE_MOVED} or {@link MouseEvent#MOUSE_DRAGGED}
     * @param modifiers extended (DOWN_MASK) modifiers at the time of the event
     * @param button {@link MouseEvent#BUTTON1_DOWN_MASK} etc. of the affected button
     */
    public void postMouseEvent(int id, int x, int y, int modifiers, int button, int clickCount) {
        if (handleTitleBarDrag(id, x, y, button)) {
            // consumed: the press-drag is moving a decorated window's title bar
            return;
        }
        EventData ev = new EventData();
        ev.setId(id);
        ev.setSource(Html5Screen.getInstance());
        ev.setTime(System.currentTimeMillis());
        ev.setModifiers(modifiers);
        ev.setX(x);
        ev.setY(y);
        ev.setButton(button);
        ev.setClickCount(clickCount);
        postEvent(ev);
    }

    /** @param wheelUp true scrolls up (away from the user) */
    public void postWheelEvent(int x, int y, int modifiers, boolean wheelUp) {
        EventData ev = new EventData();
        ev.setId(MouseEvent.MOUSE_WHEEL);
        ev.setSource(Html5Screen.getInstance());
        ev.setTime(System.currentTimeMillis());
        ev.setModifiers(modifiers);
        ev.setX(x);
        ev.setY(y);
        // EventData.createAWTEvent maps button == 4 to wheel rotation -1 (up)
        ev.setButton(wheelUp ? 4 : 5);
        postEvent(ev);
    }

    /**
     * @param id {@link KeyEvent#KEY_PRESSED}, {@link KeyEvent#KEY_RELEASED}
     *           or {@link KeyEvent#KEY_TYPED}
     */
    public void postKeyEvent(int id, int keyCode, char keyChar, int modifiers) {
        EventData ev = new EventData();
        ev.setId(id);
        ev.setSource(Html5Screen.getInstance());
        ev.setTime(System.currentTimeMillis());
        ev.setModifiers(modifiers);
        ev.setKeyCode(keyCode);
        ev.setKeyChar(keyChar);
        postEvent(ev);
    }
}
