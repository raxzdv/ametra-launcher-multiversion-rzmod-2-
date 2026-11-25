#!/usr/bin/env bash
# Prepara gradle.properties para compilar UNA versión: aplica versions/<mc>.properties y,
# si no traen yarn / loader / Fabric API, los busca solos (Fabric meta + maven).
# Uso (dentro de ametra-mod):  bash tools/resolve.sh 1.20.4
set -euo pipefail
MC="$1"; P="versions/$MC.properties"
[ -f "$P" ] || { echo "Falta $P"; exit 1; }
grep -v '^#' "$P" | grep -v '^$' >> gradle.properties
get() { grep -E "^$1=" gradle.properties | tail -n1 | cut -d= -f2-; }
MV="$(get minecraft_version)"
has() { grep -qE "^$1=" "$P"; }
if ! has yarn_mappings; then
  Y="$(curl -fsS "https://meta.fabricmc.net/v2/versions/yarn/$MV" | python3 -c 'import sys,json;print(json.load(sys.stdin)[0]["version"])')"
  echo "yarn_mappings=$Y" >> gradle.properties
fi
if ! has loader_version; then
  L="$(curl -fsS https://meta.fabricmc.net/v2/versions/loader | python3 -c 'import sys,json;print([x for x in json.load(sys.stdin) if x["stable"]][0]["version"])')"
  echo "loader_version=$L" >> gradle.properties
fi
if ! has fabric_version; then
  F="$(curl -fsS https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml | python3 -c '
import sys,re
mv=sys.argv[1]; v=[x for x in re.findall(r"<version>([^<]+)</version>", sys.stdin.read()) if x.endswith("+"+mv)]
print(v[-1])' "$MV")"
  echo "fabric_version=$F" >> gradle.properties
fi
echo "== gradle.properties para $MC =="; grep -E '^(minecraft_version|yarn_mappings|loader_version|fabric_version|mc_dep|java_version)=' gradle.properties
