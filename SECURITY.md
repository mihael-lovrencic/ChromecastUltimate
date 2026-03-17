# Security Policy

## Supported Versions

The following versions of Chromecast Ultimate are currently supported with security updates:

| Version | Supported          |
| ------- | ------------------ |
| 1.0.x   | :white_check_mark: |

## Reporting a Vulnerability

If you discover a security vulnerability within Chromecast Ultimate, please send an email to the project maintainer. All security vulnerabilities will be promptly addressed.

Please include the following information:

- Type of vulnerability
- Full paths of source file(s) related to the vulnerability
- Location of the affected source code (tag/branch/commit or direct URL)
- Any special configuration required to reproduce the issue
- Step-by-step instructions to reproduce the issue
- Proof-of-concept or exploit code (if possible)
- Impact of the issue, including how an attacker might exploit it

## Security Best Practices

### For Users

1. **Keep the app updated** - Always use the latest version from GitHub Releases
2. **Network Security** - The app uses `network_security_config.xml` to restrict cleartext traffic to localhost only
3. **Permissions** - The app only requests necessary permissions (INTERNET, NETWORK_STATE, WIFI_STATE, FOREGROUND_SERVICE, RECORD_AUDIO)
4. **No data backup** - `android:allowBackup="false"` prevents app data from being backed up

### For Developers

When contributing code:

1. **Input Validation** - Always validate and sanitize user inputs
2. **Secure URLs** - Only allow http/https protocols, never allow file:// or content://
3. **Error Handling** - Never expose internal error details to users
4. **Logging** - Avoid logging sensitive information
5. **Dependencies** - Keep dependencies updated to latest stable versions

## Known Security Considerations

- The embedded HTTP server (NanoHTTPD) runs on port 5000 and is intended for local network communication with the Firefox extension only
- Cleartext traffic is only permitted to localhost/127.0.0.1
- The Chromecast API uses some deprecated methods for backward compatibility with older Android versions

## Security Update History

- **v1.0.0** - Initial release with basic security features:
  - Input validation on all HTTP endpoints
  - Security headers (X-Content-Type-Options, X-Frame-Options, X-XSS-Protection)
  - Network security configuration
  - ProGuard obfuscation for release builds
