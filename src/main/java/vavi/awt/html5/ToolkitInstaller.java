/*
 * Copyright (c) 2026 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.awt.html5;

import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Map;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.loading.ClassInjector;
import net.bytebuddy.dynamic.loading.ClassReloadingStrategy;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.pool.TypePool;


/**
 * Installs {@link Html5Toolkit} and {@code Html5GraphicsEnvironment} as the
 * AWT backend. JDK 25 ignores the {@code awt.toolkit} and
 * {@code java.awt.graphicsenv} system properties, so installation redefines
 * {@code GraphicsEnvironment.getLocalGraphicsEnvironment()} and
 * {@code sun.awt.PlatformGraphicsInfo.createGE()} with ByteBuddy and sets the
 * {@code Toolkit.toolkit} field reflectively (the technique of the
 * caciocavallosilano fork's {@code CacioExtension}).
 * <p>
 * The graphics environment classes are injected into the bootstrap
 * classloader <em>by name, before ever being loaded here</em>: parent-first
 * delegation then resolves every later reference to the single bootstrap
 * copy, keeping the singleton consistent between AWT internals and
 * application code.
 * <p>
 * Must run before any AWT/Swing class initializes. Requires
 * {@code -XX:+EnableDynamicAgentLoading -Djdk.attach.allowAttachSelf=true}
 * and the {@code --add-opens java.desktop/java.awt=ALL-UNNAMED} set from
 * the pom.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2026-07-05 nsano initial version <br>
 */
public final class ToolkitInstaller {

    private static final String GE = "vavi.awt.html5.Html5GraphicsEnvironment";

    private static boolean installed;

    private ToolkitInstaller() {
    }

    public static synchronized void install() {
        if (installed) {
            return;
        }
        try {
            System.setProperty("java.awt.headless", "false");
            if (System.getProperty("cacio.managed.screensize") == null) {
                System.setProperty("cacio.managed.screensize", "1024x768");
            }

            ByteBuddyAgent.install();
            injectGraphicsEnvironment();

            Field toolkit = Toolkit.class.getDeclaredField("toolkit");
            toolkit.setAccessible(true);
            toolkit.set(null, new Html5Toolkit());

            Field defaultHeadless = GraphicsEnvironment.class.getDeclaredField("defaultHeadless");
            defaultHeadless.setAccessible(true);
            defaultHeadless.set(null, Boolean.FALSE);
            Field headless = GraphicsEnvironment.class.getDeclaredField("headless");
            headless.setAccessible(true);
            headless.set(null, Boolean.FALSE);

            if (!(Toolkit.getDefaultToolkit() instanceof Html5Toolkit)) {
                throw new IllegalStateException("toolkit installation failed: " + Toolkit.getDefaultToolkit());
            }
            String geName = GraphicsEnvironment.getLocalGraphicsEnvironment().getClass().getName();
            if (!GE.equals(geName)) {
                throw new IllegalStateException("graphics environment installation failed: " + geName);
            }
            installed = true;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("cannot install html5 toolkit", e);
        }
    }

    private static void injectGraphicsEnvironment() throws IOException {
        // GraphicsEnvironment lives in the bootstrap classloader; everything the
        // redefined method touches must be visible there too. Injection happens
        // by name so these classes are never defined in the application
        // classloader (which would create a second, distinct copy).
        injectIntoBootstrapClassLoader(
                "vavi.awt.html5.Html5Interceptor",
                GE,
                "vavi.awt.html5.Html5GraphicsConfiguration",
                "vavi.awt.html5.Html5GraphicsDevice");

        TypePool pool = TypePool.Default.ofSystemLoader();
        ClassFileLocator locator = ClassFileLocator.ForClassLoader.ofSystemLoader();
        TypeDescription interceptor = pool.describe("vavi.awt.html5.Html5Interceptor").resolve();

        ByteBuddy byteBuddy = new ByteBuddy();

        byteBuddy
                .redefine(pool.describe("java.awt.GraphicsEnvironment").resolve(), locator)
                .method(ElementMatchers.named("getLocalGraphicsEnvironment"))
                .intercept(MethodDelegation.to(interceptor))
                .make()
                .load(Object.class.getClassLoader(), ClassReloadingStrategy.fromInstalledAgent());

        byteBuddy
                .redefine(pool.describe("sun.awt.PlatformGraphicsInfo").resolve(), locator)
                .method(ElementMatchers.nameStartsWith("createGE"))
                .intercept(MethodDelegation.to(interceptor))
                .make()
                .load(Thread.currentThread().getContextClassLoader(), ClassReloadingStrategy.fromInstalledAgent());
    }

    private static void injectIntoBootstrapClassLoader(String... classNames) throws IOException {
        ClassLoader cl = ToolkitInstaller.class.getClassLoader();
        for (String name : classNames) {
            try (InputStream in = cl.getResourceAsStream(name.replace('.', '/').concat(".class"))) {
                if (in == null) {
                    throw new IOException("class resource not found: " + name);
                }
                byte[] buffer = in.readAllBytes();
                ClassInjector.UsingUnsafe injector = new ClassInjector.UsingUnsafe(null);
                injector.injectRaw(Map.of(name, buffer));
            }
        }
    }
}
