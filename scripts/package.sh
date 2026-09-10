#!/usr/bin/env bash
set -euo pipefail

TYPE="${1:-auto}"
VERSION="$(mvn -q -Dexec.executable=echo -Dexec.args='${project.version}' --non-recursive exec:exec 2>/dev/null || sed -n 's/.*<version>\(.*\)<\/version>.*/\1/p' pom.xml | head -1)"
MAIN_JAR="argus-${VERSION}.jar"

case "$TYPE" in
  auto)
    case "$(uname -s)" in
      Linux) TYPE="deb" ;;
      Darwin) TYPE="dmg" ;;
      *) echo "unsupported OS; pass a jpackage --type as \$1 (deb|rpm|dmg|msi|exe|app-image)" >&2; exit 1 ;;
    esac
    ;;
  deb|rpm|dmg|msi|exe|app-image) ;;
  *) echo "unknown type '$TYPE'" >&2; exit 1 ;;
esac

mvn -q -DskipTests package dependency:copy-dependencies
rm -rf target/jpackage-input target/jpackage
mkdir -p target/jpackage-input
cp "target/${MAIN_JAR}" target/jpackage-input/

FLAGS=()
case "$TYPE" in
  deb)
    FLAGS+=(--linux-shortcut --linux-menu-group "Development")
    ;;
  rpm)
    FLAGS+=(--linux-shortcut --linux-menu-group "Development")
    ;;
  msi)
    FLAGS+=(--win-menu --win-shortcut --win-dir-chooser)
    ;;
  exe)
    FLAGS+=(--win-menu --win-shortcut --win-dir-chooser)
    ;;
esac

jpackage \
  --type "$TYPE" \
  --name argus \
  --app-version "${VERSION%-SNAPSHOT}" \
  --input target/jpackage-input \
  --main-jar "$MAIN_JAR" \
  --main-class com.redpill_linpro.argus.launcher.Launcher \
  --dest target/jpackage \
  "${FLAGS[@]:+${FLAGS[@]}}"

echo "created: $(ls target/jpackage/*)"
