# QuickAuth-Android

This repository provides an Android-optimized client implementation for the [QuickAuth](https://github.com/TerAlone6300/QuickAuth) system, designed to work seamlessly within Android environments (e.g., Termux).

## Overview

QuickAuth-Android enables secure management and generation of Time-based One-Time Passwords (TOTP) directly on your Android device. It acts as a client for the core QuickAuth server, ensuring your authentication tokens are synchronized securely across devices while maintaining local control.

## Key Features

- **Secure TOTP Generation:** Native implementation for generating TOTP codes, supporting standard algorithms without external dependencies.
- **Synchronized Accounts:** Securely syncs your account list and authentication secrets with a central QuickAuth server.
- **Session Management:** Handles secure session tokens, automatic token refreshing, and server-side session revocation.
- **Android Optimized:** Built with awareness of Android/Termux environments, utilizing appropriate storage and input handling for mobile terminal usage.
- **Profile Support:** Manage multiple authentication profiles easily.

## Functionality

The client handles the complete lifecycle of authentication:
1.  **Secure Storage:** Stores account data locally, with platform-specific encryption (where applicable).
2.  **Sync:** Connects to the QuickAuth server to fetch or update your account list.
3.  **Generation:** Displays live, updating TOTP codes based on the configured secrets.
4.  **TUI/CLI:** Provides an interactive interface for managing keys, sessions, and profiles directly from your terminal.

