# vavi-awt-html5 — design

Runs an unmodified AWT/Swing application on a JVM and mirrors its UI to a
browser. Pixels flow one way (server → browser), input flows the other.

```
Swing app (unmodified main)
 └─ Html5Toolkit (extends cacio CacioToolkit, managed-window path)
     └─ FullScreenWindowFactory(Html5Screen, Html5EventSource)      [cacio-shared]
         └─ Html5Screen: BufferedImage framebuffer + TrackingGraphics2D → DamageTracker
             └─ FramePump (~30 fps): damage → PNG tiles → binary protocol
                 └─ transport  ⇄  browser
                                   └─ TeaVM WASM client: canvas blits + input capture
StaticHttpServer (com.sun.net.httpserver): index.html + client.wasm + globals
```

## Toolkit backend

Based on the caciocavallosilano fork of Caciocavallo (`cacio-shared`, java-25
line, via jitpack). `Html5Toolkit` extends `CacioToolkit` and uses the managed
window path: all AWT windows are composited into one virtual screen
(`Html5Screen`) backed by a `BufferedImage`. Screen size starts at
`cacio.managed.screensize` (default 1024x768) and then mirrors the browser
viewport: `HELLO` and `CLIENT_RESIZE` are the only triggers for a screen
resize. Window bounds never affect the screen — like a real desktop, a
window resized or moved past the screen edge is simply clipped.

`Html5WindowFactory` wraps cacio's `FullScreenWindowFactory` to post
`COMPONENT_RESIZED`/`COMPONENT_MOVED` to toplevel windows when their bounds
change: AWT suppresses these notifications for toplevels with a native peer
(JDK-5025858) and expects them from the window system, which cacio's managed
windows never provide. Without this, `componentResized`/`componentMoved`
listeners on frames are silent.

`FramePump` detects changes by diffing the framebuffer against the previously
sent frame in 64-pixel tiles ~30 times per second, and ships changed regions as
PNG `BLIT` messages. Framebuffer diffing is used deliberately rather than
intercepting draw operations: cacio composits windows through
`SunGraphics2D.constrain()`, whose offset is not visible in the graphics
transform, so device coordinates cannot be reconstructed reliably from a
`Graphics2D` wrapper — diffing sees the real pixels wherever they land. A new
browser connection is detected by the pump, which then sends a full frame.

Input arrives as protocol messages, is decoded by `InputEventDecoder` into
`EventData` on `Html5EventSource`, and the cacio managed container synthesizes
the derived events (enter/exit/click/focus/window).

`Html5EventSource` also intercepts a left-button press-drag on a decorated
window's title bar and moves the window itself. cacio draws L&F decorations on
an internal proxy window whose peer `setBounds` is a no-op, so its built-in
`MetalRootPaneUI` title-bar drag moves nothing; instead we hit-test the title
bar (top inset minus any menu bar, excluding the resize border) and drive
`Window.setLocation` on the real `Frame`/`Dialog`. The initial press is still
forwarded so activation/focus behave normally; the drag and release are
consumed. Each drag step also posts a `COPY_AREA` hint (see below).

`COPY_AREA` is an optimization: a title-bar drag shifts a whole window block by
a small delta every frame, which would otherwise re-encode the window as a PNG
each tick. `Html5EventSource.hintCopyArea` queues the block move; the
`FramePump` drains hints at the top of a tick, ships a `COPY_AREA` (the browser
does a canvas self-copy of pixels it already has) and mirrors the same shift
into its `prev` buffer. Because `prev` is kept in lock-step with everything the
client is told, the diff that follows blits only the residual (the vacated
background, newly exposed edges) — an inaccurate hint costs a few extra blits,
never correctness.

### Installation on JDK 25

JDK 25 ignores the `awt.toolkit` / `java.awt.graphicsenv` system properties.
`ToolkitInstaller` installs the backend the way the fork's test harness does:
ByteBuddy redefines `GraphicsEnvironment.getLocalGraphicsEnvironment()` and
`sun.awt.PlatformGraphicsInfo.createGE()`, the graphics-environment classes are
injected into the bootstrap classloader, and `Toolkit.toolkit` /
`GraphicsEnvironment.headless` are set reflectively. This needs
`-XX:+EnableDynamicAgentLoading -Djdk.attach.allowAttachSelf=true` and the
`--add-exports` / `--add-opens` set in `bin/run.sh` and the pom.

## Sound

`javax.sound.sampled` playback is mirrored to the browser. A playback-only
mixer (`vavi.awt.html5.sound.Html5Mixer`, registered as a `MixerProvider`
service) supplies `SourceDataLine` and `Clip` lines for 8/16-bit
signed/unsigned PCM, mono or stereo, any sample rate; `Main` points the
`javax.sound.sampled.SourceDataLine` / `.Clip` default-device properties at it,
so an unmodified app's `AudioSystem.getSourceDataLine` / `getClip` land here
without configuration. `Toolkit.beep()` plays a short tone through the same
path.

Written PCM is converted to one wire format (s16 big-endian interleaved) and
shipped as `AUDIO` messages; the source format rides in every chunk so a
browser that connects mid-stream can join. Because no real device consumes
the data, `Html5SourceDataLine.write` paces itself: it blocks once the writer
runs more than 250 ms ahead of wall clock, providing the back-pressure that
streaming apps get from a hardware buffer. `Clip` plays its preloaded data
through an internal line on a daemon thread, which makes loop timing fall out
of the same pacing.

The client schedules each chunk with Web Audio: per stream it keeps a cursor
on the `AudioContext` clock and queues buffers back-to-back (with a ~60 ms
initial lead), so network jitter does not become audible gaps. Browsers keep
an `AudioContext` suspended until the page gets a user gesture (autoplay
policy); until then chunks are dropped and the status line asks for a click,
which one-shot listeners turn into `ctx.resume()`. Capture
(`TargetDataLine`, i.e. a browser microphone) is out of scope for v1.

## Wire protocol

Binary, big-endian, length-prefixed: `u32 length, u8 opcode, body`. Unknown
opcodes are skipped, so it is forward compatible. Server → client: `INIT`,
`BLIT` (PNG of a rectangle), `COPY_AREA` (shift a rectangle already on the
client canvas by `dx, dy`), `FRAME_END`, `RESIZE`, `AUDIO` (PCM chunk with
its format), `AUDIO_STOP`, `PONG`. Client → server: `HELLO`, `MOUSE`,
`WHEEL`, `KEY`, `RESIZE`, `PING`.
See `vavi.awt.html5.protocol.Protocol`. The same framing is used on both
transports; the browser client carries its own mirror of the constants so the
TeaVM-compiled code stays independent of the server sources.

## Browser client

Java compiled to WebAssembly by TeaVM (WASM-GC backend), JS interop via
teavm-jso. `CanvasRenderer` decodes PNG blits with `createImageBitmap` and
draws them to a `<canvas>` in arrival order; `InputCapture` forwards
mouse/wheel/key events. A hand-written `index.html` loads the TeaVM runtime.

## Transport

Two transports implement the same protocol; select with
`-Dvavi.awt.html5.transport`:

- **`ws` (default)** — a plain `ws://` WebSocket (`org.java-websocket` server,
  the browser's native `WebSocket` on the client). No certificate is needed
  because the page origin is `http://localhost`, for which browsers allow
  `ws://`. This is the transport that interoperates with current browsers and
  is what the demo uses.
- **`webtransport`** — HTTP/3 / QUIC via kwik + flupke, with a self-signed
  ECDSA P-256 certificate advertised to the browser through
  `serverCertificateHashes` (`CertManager`, 10-day validity). The QUIC and TLS
  layers work and the certificate is accepted, but current Chrome completes the
  QUIC handshake and then declines the WebTransport session: flupke 0.9.4
  implements an older WebTransport HTTP/3 draft than the browser negotiates.
  Kept in the tree (and covered by an in-JVM round-trip test using flupke's own
  client) for when the library catches up. The mission targeted WebTransport;
  the WebSocket path is the working fallback that keeps the identical
  architecture and protocol, swapping only the substrate.

## Running

```
mvn package
bin/run.sh                       # runs vavi.awt.html5.demo.DemoApp
bin/run.sh com.example.YourApp   # or any Swing app on the classpath
bin/run.sh path/to/app.jar       # a runnable jar (Main-Class from its manifest)
# open http://localhost:8080/
```

When the first argument ends in `.jar`, `run.sh` reads its `Main-Class` from the
manifest and adds the jar to the classpath.

## Scope (v1)

Single session (one browser mirrors one app instance); whole-desktop
framebuffer with decorated windows. Mouse (including press-drag-release, so
sliders, scrollbars, text selection work), wheel and keyboard are supported.
Windows move by title-bar drag, and the block move is sent as `COPY_AREA`.
`javax.sound` playback and `Toolkit.beep()` reach the browser via Web Audio.
Multi-session, browser-driven resize, clipboard, cursor shapes, microphone
capture and full
`java.awt.dnd` data transfer (needs a `DragSourceContextPeer`, currently null)
are left for later.
