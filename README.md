# CEMount

> A lightweight, mini Git-like distributed file system implemented in Java.

CEMount is inspired by Git’s architecture and enables you to initialize repositories, track file versions, and push/pull changes over TCP—all from a familiar CLI interface (`cem`). It’s designed to run even on older or low-power hardware, making it ideal for lightweight deployments.

---

## Table of Contents

- [Features](#features)  
- [Prerequisites](#prerequisites)  
- [Installation](#installation)  
- [Quick Start](#quick-start)  
- [CLI Commands](#cli-commands)   
- [License](#license)  

---

## Features

- **Content-Addressed Storage**: Files are stored by hash, ensuring integrity and deduplication.  
- **Distributed Workflow**: Push and pull commits over TCP—no SSH or HTTP required.  
- **Lightweight Server**: Runs on minimal resources—suitable for older laptops or embedded devices.  
- **Familiar CLI**: Commands mirror Git’s interface (`cem init`, `cem add`, `cem commit`, etc.).

## Prerequisites

- **Java 11+** (tested on Java 17)  
- **Maven 3.6+**  
- **Unix-like OS** (Linux, macOS)  
- **Network Connectivity** for client-server operations  

## Installation
Choose one of the following methods to install CEMount on your system:

## Automated (recommended)

Install CEMount globally with a one-liner (fetches the latest runtime ZIP and installs the cem launcher):
```bash
curl -sSL https://raw.githubusercontent.com/CasparEkroth/CEMount/main/scripts/install.sh | bash
`````
This script will:

- Download the runtime ZIP from GitHub Releases  
- Unpack it into `~/.local/lib/cemount`  
- Symlink the `cem` launcher into `~/.local/bin`  
- Ensure `~/.local/bin` is in your `PATH`

## Manual Installation
```bash
# Clone the repo
git clone https://github.com/CasparEkroth/cemount.git
cd cemount

# Build the project
mvn clean package

# Create install directories
mkdir -p "$HOME/.local/lib/cemount" "$HOME/.local/bin"

# Copy the JAR
cp target/cemount-0.1.0-SNAPSHOT.jar ~/.local/lib/cemount/cemount.jar

# Create wrapper script
cat > ~/.local/bin/cem << 'EOF'
#!/usr/bin/env bash
exec java -jar "$HOME/.local/lib/cemount/cemount.jar" "$@"
EOF
chmod +x ~/.local/bin/cem
``````



## Quick Start
```bash
# 1. Launch the server
cem server <port> [<repo-directory>]

# Example:
cem server 7842 ~/Documents/CETest
# → Server listening on port 7842, serving ~/Documents/CETest

# 2. Initialize & commit
cem init 
cem add 
cem commit -m "Initial commit"

# 3. Add remote & push
cem remote add <remoteName> tcp://<server-host>:<port>/my-repo
cem push <remoteName>

# 4. Clone & pull on another machine
mkdir CloneTest && cd CloneTest
cem clone <remoteName> tcp://<server-host>:<port>/my-repo
cem fetch <remoteName>
cem pull <remoteName>
`````

## CLI Commands

| Command                        | Description                                  |
| -------------------------------| -------------------------------------------- |
| `cem init <path> `             | Initialize a new repository in `<path>`      |
| `cem server <port> [<path>]`   | Start a CEMount server on `<port>`           |
| `cem clone <reomte> <url>`     | Clone a remote repository via TCP            |
| `cem add <paths>`              | Stage files for commit                       |
| `cem commit -m "<msg>"`        | Commit staged changes with a message         |
| `cem log`                      | Show commit history                          |
| `cem fetch <remote>`           | Fetch objects and refs from `<remote>`       |
| `cem pull <remote> `           | Fetch and merge changes from a remote branch |
| `cem push <remote> `           | Push local commits to a remote               |
| `cem remote -v`                | List configured remotes and their URLs       |
| `cem remote add <remote> <url>`| List configured remotes and their URLs       |

## License

This project is licensed under the MIT License. See the LICENSE file for full terms and conditions.
