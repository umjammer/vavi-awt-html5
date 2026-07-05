/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.awt.html5;

import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

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

    private Html5EventSource() {
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
