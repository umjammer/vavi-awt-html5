/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.awt.html5;

import java.awt.AWTException;
import java.awt.BufferCapabilities;
import java.awt.BufferCapabilities.FlipContents;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.ComponentEvent;
import java.awt.image.ColorModel;
import java.awt.peer.ContainerPeer;

import com.github.caciocavallosilano.cacio.peer.CacioComponent;
import com.github.caciocavallosilano.cacio.peer.CacioEventPump;
import com.github.caciocavallosilano.cacio.peer.PlatformToplevelWindow;
import com.github.caciocavallosilano.cacio.peer.PlatformWindow;
import com.github.caciocavallosilano.cacio.peer.PlatformWindowFactory;
import com.github.caciocavallosilano.cacio.peer.managed.FullScreenWindowFactory;

import sun.java2d.pipe.Region;


/**
 * A {@link FullScreenWindowFactory} that additionally delivers
 * {@link ComponentEvent#COMPONENT_RESIZED}/{@link ComponentEvent#COMPONENT_MOVED}
 * to toplevel windows.
 * <p>
 * {@code Component.reshape} suppresses these notifications for toplevel
 * windows with a native peer ("done from peer or native code when the window
 * is really resized or moved", JDK-5025858), so on a native toolkit the
 * window system posts them. cacio's managed windows never do, which left
 * {@code componentResized}/{@code componentMoved} listeners on frames silent.
 * Toplevel platform windows are therefore wrapped so a bounds change posts
 * the matching event through {@link CacioComponent#handlePeerEvent}, exactly
 * like a native peer would.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-07-12 nsano initial version <br>
 */
public class Html5WindowFactory implements PlatformWindowFactory {

    private final FullScreenWindowFactory delegate;

    public Html5WindowFactory(FullScreenWindowFactory delegate) {
        this.delegate = delegate;
    }

    /** cacio casts the parent to its own ManagedWindow, so hand it the raw window */
    private static PlatformWindow unwrap(PlatformWindow w) {
        return w instanceof BoundsNotifyingWindow b ? b.inner : w;
    }

    @Override
    public PlatformWindow createPlatformWindow(CacioComponent awtComponent, PlatformWindow parent) {
        // nested (non-toplevel) windows: AWT posts their component events itself
        return delegate.createPlatformWindow(awtComponent, unwrap(parent));
    }

    @Override
    public PlatformToplevelWindow createPlatformToplevelWindow(CacioComponent component) {
        return new BoundsNotifyingWindow(delegate.createPlatformToplevelWindow(component), component);
    }

    @Override
    public PlatformWindow createPlatformToplevelWindow(CacioComponent component, PlatformWindow owner) {
        PlatformWindow w = delegate.createPlatformToplevelWindow(component, unwrap(owner));
        if (w instanceof PlatformToplevelWindow t) {
            return new BoundsNotifyingWindow(t, component);
        }
        return w;
    }

    @Override
    public CacioEventPump<?> createEventPump() {
        return delegate.createEventPump();
    }

    /**
     * Delegates everything to the wrapped managed window and posts
     * {@code COMPONENT_RESIZED}/{@code COMPONENT_MOVED} when a
     * {@link #setBounds} call actually changes the toplevel's bounds.
     */
    private static final class BoundsNotifyingWindow implements PlatformToplevelWindow {

        private final PlatformToplevelWindow inner;
        private final CacioComponent cacioComponent;

        BoundsNotifyingWindow(PlatformToplevelWindow inner, CacioComponent cacioComponent) {
            this.inner = inner;
            this.cacioComponent = cacioComponent;
        }

        @Override
        public void setBounds(int x, int y, int width, int height, int op) {
            Rectangle old = inner.getBounds();
            inner.setBounds(x, y, width, height, op);
            Rectangle now = inner.getBounds();
            Component c = cacioComponent.getAWTComponent();
            if (!(c instanceof Window)) {
                return;
            }
            if (old.width != now.width || old.height != now.height) {
                cacioComponent.handlePeerEvent(new ComponentEvent(c, ComponentEvent.COMPONENT_RESIZED));
            }
            if (old.x != now.x || old.y != now.y) {
                cacioComponent.handlePeerEvent(new ComponentEvent(c, ComponentEvent.COMPONENT_MOVED));
            }
        }

        @Override
        public int getState() {
            return inner.getState();
        }

        @Override
        public void setState(int state) {
            inner.setState(state);
        }

        @Override
        public void setMaximizedBounds(Rectangle bounds) {
            inner.setMaximizedBounds(bounds);
        }

        @Override
        public void setResizable(boolean resizable) {
            inner.setResizable(resizable);
        }

        @Override
        public void setTitle(String title) {
            inner.setTitle(title);
        }

        @Override
        public void setBlocked(boolean blocked) {
            inner.setBlocked(blocked);
        }

        @Override
        public ColorModel getColorModel() {
            return inner.getColorModel();
        }

        @Override
        public GraphicsConfiguration getGraphicsConfiguration() {
            return inner.getGraphicsConfiguration();
        }

        @Override
        public Rectangle getBounds() {
            return inner.getBounds();
        }

        @Override
        public void dispose() {
            inner.dispose();
        }

        @Override
        public Graphics2D getGraphics(Color foreground, Color background, Font font) {
            return inner.getGraphics(foreground, background, font);
        }

        @Override
        public Insets getInsets() {
            return inner.getInsets();
        }

        @Override
        public Point getLocationOnScreen() {
            return inner.getLocationOnScreen();
        }

        @Override
        public boolean canDetermineObscurity() {
            return inner.canDetermineObscurity();
        }

        @Override
        public boolean isObscured() {
            return inner.isObscured();
        }

        @Override
        public void applyShape(Region shape) {
            inner.applyShape(shape);
        }

        @Override
        public boolean isReparentSuppored() {
            return inner.isReparentSuppored();
        }

        @Override
        public void reparent(ContainerPeer newContainer) {
            inner.reparent(newContainer);
        }

        @Override
        public boolean isRestackSupported() {
            return inner.isRestackSupported();
        }

        @Override
        public void restack() {
            inner.restack();
        }

        @Override
        public void setVisible(boolean b) {
            inner.setVisible(b);
        }

        @Override
        public void createBuffers(int numBuffers, BufferCapabilities caps) throws AWTException {
            inner.createBuffers(numBuffers, caps);
        }

        @Override
        public void destroyBuffers() {
            inner.destroyBuffers();
        }

        @Override
        public void flip(int x1, int y1, int x2, int y2, FlipContents flipAction) {
            inner.flip(x1, y1, x2, y2, flipAction);
        }

        @Override
        public Image getBackBuffer() {
            return inner.getBackBuffer();
        }

        @Override
        public void requestFocus() {
            inner.requestFocus();
        }
    }
}
