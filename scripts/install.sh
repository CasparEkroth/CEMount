#!/usr/bin/env bash
set -euo pipefail

# 1) Variables
VERSION="v0.1.0"
REPO="CasparEkroth/CEMount"
ASSET="cemount-0.1.0-runtime.zip"
URL="https://github.com/${REPO}/releases/download/${VERSION}/${ASSET}"

# 2) Download & unpack into ~/.local/lib/cemount
INSTALL_DIR="$HOME/.local/lib/cemount"
BIN_DIR="$HOME/.local/bin"

mkdir -p "$INSTALL_DIR" "$BIN_DIR"
echo "Downloading $URL…"
curl -sSL "$URL" -o "/tmp/${ASSET}"

echo "Extracting into $INSTALL_DIR…"
unzip -qo "/tmp/${ASSET}" -d "$INSTALL_DIR"

# 3) Symlink the 'cem' launcher
ln -sf "$INSTALL_DIR/bin/cem" "$BIN_DIR/cem"
chmod +x "$INSTALL_DIR/bin/cem"

# 4) Ensure ~/.local/bin is in your PATH
SHELL_RC=""
if [[ -n "${ZSH_VERSION-}" ]]; then
  SHELL_RC="$HOME/.zshrc"
elif [[ -n "${BASH_VERSION-}" ]]; then
  SHELL_RC="$HOME/.bashrc"
fi

if ! grep -q 'export PATH="$HOME/.local/bin:$PATH"' "$SHELL_RC"; then
  echo 'export PATH="$HOME/.local/bin:$PATH"' >> "$SHELL_RC"
  echo "Added ~/.local/bin to PATH in $SHELL_RC"
fi

echo "Installation complete! Restart your shell or run 'source $SHELL_RC', then try 'cem --help'."
