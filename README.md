# CEMount

CEMount is a lightweight distributed versioned file repository client and daemon written in Java, inspired by Git. The project provides a minimal Git-like interface for file versioning and clustered storage.

## Features
- Initialize a new CEMount repository with multiple replicas
- Store metadata and objects in a local directory
- Simple push/pull synchronization between client and remote cluster over TCP/HTTP
- Support for branches, merges, and conflict resolution
- Basic repository integrity check (fsck)

## Requirements
- Java 17+ / OpenJDK 17
- Maven 3.6+
- (Optional) Docker for integration testing

## Getting Started

### 1) Build and install the binary (one-time)
```bash
$ mvn clean package -DskipTests
$ sudo cp target/cemount.jar /usr/local/bin/cemount.jar
# or
$ make install
