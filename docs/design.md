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
(`Html5Screen`) backed by a `BufferedImage`. Screen size comes from
`cacio.managed.screensize` (default 1024x768).

`TrackingGraphics2D` wraps every `Graphics2D` handed to AWT and records the
device-space bounding box of each draw op into `DamageTracker`. `FramePump`
turns accumulated damage into PNG `BLIT` messages ~30 times per second.
`FrameDiffer` is a tile-hash full-frame diff kept as a correctness net
(`-Dvavi.awt.html5.diff=full`) and used in tests.

Input arrives as protocol messages, is decoded by `InputEventDecoder` into
`EventData` on `Html5EventSource`, and the cacio managed container synthesizes
the derived events (enter/exit/click/focus/window).

### Installation on JDK 25

JDK 25 ignores the `awt.toolkit` / `java.awt.graphicsenv` system properties.
`ToolkitInstaller` installs the backend the way the fork's test harness does:
ByteBuddy redefines `GraphicsEnvironment.getLocalGraphicsEnvironment()` and
`sun.awt.PlatformGraphicsInfo.createGE()`, the graphics-environment classes are
injected into the bootstrap classloader, and `Toolkit.toolkit` /
`GraphicsEnvironment.headless` are set reflectively. This needs
`-XX:+EnableDynamicAgentLoading -Djdk.attach.allowAttachSelf=true` and the
`--add-exports` / `--add-opens` set in `bin/run.sh` and the pom.

## Wire protocol

Binary, big-endian, length-prefixed: `u32 length, u8 opcode, body`. Unknown
opcodes are skipped, so it is forward compatible. Server → client: `INIT`,
`BLIT` (PNG of a rectangle), `COPY_AREA` (reserved), `FRAME_END`, `RESIZE`,
`PONG`. Client → server: `HELLO`, `MOUSE`, `WHEEL`, `KEY`, `RESIZE`, `PING`.
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
# open http://localhost:8080/
```

## Scope (v1)

Single session (one browser mirrors one app instance); whole-desktop
framebuffer with decorated windows; `COPY_AREA`, multi-session, browser-driven
resize, clipboard and cursor shapes are left for later.
