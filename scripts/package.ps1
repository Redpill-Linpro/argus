param(
    [ValidateSet("msi", "exe", "app-image")]
    [string]$Type = "msi"
)

$ErrorActionPreference = "Stop"

$Version = (Select-Xml -Path pom.xml -XPath "/project/version" -Namespace @{ p = "http://maven.apache.org/POM/4.0.0" }).Node.InnerText
$MainJar = "argus-$Version.jar"

mvn -q -DskipTests package dependency:copy-dependencies
if (Test-Path target/jpackage-input) { Remove-Item -Recurse -Force target/jpackage-input }
if (Test-Path target/jpackage) { Remove-Item -Recurse -Force target/jpackage }
New-Item -ItemType Directory -Force target/jpackage-input | Out-Null
Copy-Item "target/$MainJar" target/jpackage-input/

jpackage `
  --type $Type `
  --name argus `
  --app-version ($Version -replace "-SNAPSHOT", "") `
  --input target/jpackage-input `
  --main-jar $MainJar `
  --main-class com.redpill_linpro.argus.launcher.Launcher `
  --dest target/jpackage `
  --win-menu --win-shortcut --win-dir-chooser

Get-ChildItem target/jpackage
