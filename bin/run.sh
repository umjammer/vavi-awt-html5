#!/bin/sh
# launches a swing application with the html5 backend:
#   bin/run.sh [app-main-class] [args...]
# defaults to the built-in demo app. requires `mvn package` first.

cd "$(dirname "$0")/.." || exit 1

MAIN_CLASS=${1:-vavi.awt.html5.demo.DemoApp}
[ $# -gt 0 ] && shift

CP="target/classes:$(mvn -q dependency:build-classpath -Dmdep.outputFile=/dev/stdout -Dmdep.includeScope=runtime 2>/dev/null | tail -1)"

exec java \
  -XX:+EnableDynamicAgentLoading \
  -Djdk.attach.allowAttachSelf=true \
  --add-exports=java.desktop/java.awt.peer=ALL-UNNAMED \
  --add-exports=java.desktop/java.awt.dnd.peer=ALL-UNNAMED \
  --add-exports=java.desktop/sun.awt=ALL-UNNAMED \
  --add-exports=java.desktop/sun.awt.datatransfer=ALL-UNNAMED \
  --add-exports=java.desktop/sun.awt.event=ALL-UNNAMED \
  --add-exports=java.desktop/sun.awt.image=ALL-UNNAMED \
  --add-exports=java.desktop/sun.font=ALL-UNNAMED \
  --add-exports=java.desktop/sun.java2d=ALL-UNNAMED \
  --add-exports=java.desktop/sun.java2d.loops=ALL-UNNAMED \
  --add-exports=java.desktop/sun.java2d.pipe=ALL-UNNAMED \
  --add-opens=java.desktop/java.awt=ALL-UNNAMED \
  --add-opens=java.desktop/sun.awt=ALL-UNNAMED \
  --add-opens=java.desktop/sun.java2d=ALL-UNNAMED \
  --add-opens=java.desktop/sun.font=ALL-UNNAMED \
  --add-opens=java.base/java.util=ALL-UNNAMED \
  --add-opens=java.base/java.lang.reflect=ALL-UNNAMED \
  -Dcacio.managed.screensize=1024x768 \
  -cp "$CP" \
  vavi.awt.html5.Main "$MAIN_CLASS" "$@"
