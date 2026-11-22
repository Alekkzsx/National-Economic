#!/usr/bin/env bash
set -euo pipefail

# ==============================================================================
# Script Autônomo de Compilação e Execução de Testes do SGI
# Ambiente: Java SE Puro (javac / java -ea), sem JUnit ou bibliotecas externas.
# ==============================================================================

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${PROJECT_ROOT}"

echo "================================================================================"
echo "          COMPILAÇÃO E EXECUÇÃO DA SUÍTE DE TESTES AUTÔNOMOS (SGI)              "
echo "================================================================================"

rm -rf compilado && mkdir -p compilado

# [1/2] Compila todas as fontes Java de produção e teste em UTF-8
echo "[1/2] Compilando código-fonte e testes..."
if ! find src test -type f -name '*.java' -print0 | grep -qz .; then
    echo "ERRO: Nenhum arquivo .java encontrado em src/ ou test/!" >&2
    exit 1
fi
find src test -type f -name '*.java' -print0 | xargs -0 javac -encoding UTF-8 -d compilado
echo "Compilação concluída com sucesso em compilado/."

# [2/2] Executa o executor mestre com assertions da JVM ativadas (-ea)
echo "[2/2] Executando suíte mestre de testes (java -ea -cp compilado test.ExecutorTestes)..."
java -ea -cp compilado test.ExecutorTestes
