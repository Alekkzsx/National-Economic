#!/usr/bin/env bash
set -e

# ==============================================================================
# Script Autônomo de Compilação e Execução de Testes do SGI
# Ambiente: Java SE Puro (javac / java -ea), sem JUnit ou bibliotecas externas.
# ==============================================================================

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${PROJECT_ROOT}"

echo "================================================================================"
echo "          COMPILAÇÃO E EXECUÇÃO DA SUÍTE DE TESTES AUTÔNOMOS (SGI)              "
echo "================================================================================"

mkdir -p compilado

echo "[1/2] Compilando código-fonte e testes..."
SOURCES=$(find src test -type f -name '*.java' 2>/dev/null)
if [ -z "${SOURCES}" ]; then
    echo "ERRO: Nenhum arquivo .java encontrado em src/ ou test/!" >&2
    exit 1
fi

javac -encoding UTF-8 -d compilado ${SOURCES}
echo "Compilação concluída com sucesso em compilado/."

echo "[2/2] Executando suíte mestre de testes (java -ea -cp compilado ExecutorTestes)..."
java -ea -cp compilado ExecutorTestes

EXIT_CODE=$?
exit ${EXIT_CODE}
