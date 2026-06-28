# QuickAuth-Android (Kotlin)

QuickAuth-Android is a native Android application built with Kotlin and Jetpack Compose, designed to act as a client for the [QuickAuth](https://github.com/TerAlone6300/QuickAuth) system.

## Overview

This application enables secure management and generation of Time-based One-Time Passwords (TOTP) directly on your Android device. It synchronizes your account list and authentication secrets with a central QuickAuth server while maintaining local control.

## Key Features

- **Secure TOTP Generation:** Native Kotlin implementation for generating TOTP codes.
- **Synchronized Accounts:** Securely syncs your accounts with the QuickAuth server using Retrofit.
- **Modern UI:** Built with **Jetpack Compose** and **Material 3**, featuring support for both Light and Dark themes.
- **Secure Storage:** Uses Android **EncryptedSharedPreferences** for local data encryption.
- **CI/CD Integration:** Automatically builds and signs release APKs using GitHub Actions.

## Requirements

- Android SDK 34 (API 34)
- Java 17 (Eclipse Temurin)

## Security Configuration

To enable automated release APK signing via GitHub Actions, configure the following repository secrets:

- `SIGNING_KEY_BASE64`: The Base64-encoded `release.jks` keystore.
- `STORE_PASSWORD`: Keystore password.
- `KEY_ALIAS`: Keystore alias (default: `key0`).
- `KEY_PASSWORD`: Key password.
