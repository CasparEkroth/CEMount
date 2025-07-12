#!/usr/bin/env bash
set -euo pipefail

VERSION="v0.1.0"
REPO="CasparEkroth/CEMount"
ASSET="cemount-0.1.0-runtime.zip"
URL="https://github.com/${REPO}/releases/download/${VERSION}/${ASSET}"

INSTALL_DIR="$HOME/.local/lib/cemount"
BIN_DIR="$HOME/.local/bin"

mkdir -p "$INSTALL_DIR" "$BIN_DIR"
echo "Downloading $URL…"
curl -sSL "$URL" -o "/tmp/${ASSET}"

echo "Extracting into $INSTALL_DIR…"
unzip -qo "/tmp/${ASSET}" -d "$INSTALL_DIR"

echo "Linking cem launcher to $BIN_DIR"
ln -sf "$INSTALL_DIR/bin/cem" "$BIN_DIR/cem"
chmod +x "$INSTALL_DIR/bin/cem"

# Ensure ~/.local/bin is in PATH
SHELL_RC=""
if [[ -n "${ZSH_VERSION-}" ]]; then
  SHELL_RC="$HOME/.zshrc"
elif [[ -n "${BASH_VERSION-}" ]]; then
  SHELL_RC="$HOME/.bashrc"
fi

if [[ -n "$SHELL_RC" ]] && ! grep -q 'export PATH="$HOME/.local/bin:$PATH"' "$SHELL_RC"; then
  echo 'export PATH="$HOME/.local/bin:$PATH"' >> "$SHELL_RC"
  echo "Added ~/.local/bin to PATH in $SHELL_RC"
fi

echo "Installation complete! Restart your shell or run 'source $SHELL_RC', then try 'cem --help'."