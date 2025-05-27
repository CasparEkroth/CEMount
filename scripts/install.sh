#!/usr/bin/env bash
set -e

# 1) Find project root (one level up from scripts/)
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# 2) Build the JAR if it doesn’t exist
JAR="$PROJECT_ROOT/target/cemount-0.1.0-SNAPSHOT.jar"
if [ ! -f "$JAR" ]; then
  echo "Building project…"
  mvn -f "$PROJECT_ROOT/pom.xml" clean package
fi

# 3) Choose install locations under your home dir (no sudo needed)
INSTALL_LIB="$HOME/.local/lib/cemount"
INSTALL_BIN="$HOME/.local/bin"

mkdir -p "$INSTALL_LIB" "$INSTALL_BIN"

# 4) Copy the JAR and write the wrapper
cp "$JAR" "$INSTALL_LIB/cemount.jar"

cat > "$INSTALL_BIN/cem" <<EOF
#!/usr/bin/env bash
exec java -jar "$INSTALL_LIB/cemount.jar" "\$@"
EOF

chmod +x "$INSTALL_BIN/cem"

# Auto-add ~/.local/bin to Z-shell PATH if not already present
RC="$HOME/.zshrc"
EXPORT_LINE='export PATH="$HOME/.local/bin:$PATH"'
if ! grep -Fxq "$EXPORT_LINE" "$RC"; then
  echo "" >> "$RC"
  echo "# added by cem install script" >> "$RC"
  echo "$EXPORT_LINE" >> "$RC"
  echo "✔︎ Added $EXPORT_LINE to $RC"
fi

echo "✅ Installation complete! ‘cem’ has been installed to: $INSTALL_BIN/cem"
echo "🔧 Your PATH was automatically updated in ~/.zshrc to include \$HOME/.local/bin"
echo "👉 To start using it now, either open a new terminal or run:"
echo "   source ~/.zshrc"

