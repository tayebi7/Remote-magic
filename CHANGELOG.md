# Changelog

## 1.1.0

### Fixed
- Bluetooth now pairs with the TV (system PIN / confirmation) before opening the HID link. 1.0.0 did not start pairing.
- Bluetooth TVs can reconnect automatically; unexpected drops are retried with growing delays.

### Added
- Air mouse (gyroscope, calibration, speed) and touchpad: Bluetooth HID, LG, Samsung.
- Keyboard text input: LG (confirmed), Samsung, Android TV, Roku, Bluetooth (US English characters only).
- Share Screen button (opens Android screen mirroring).
- Standby indication where the TV reports it; Bluetooth wake attempt.
- BLE devices are listed as unsupported instead of being offered for control.
- Stop, *, #, Enter keys; hideable number pad; landscape layout.

### Changed
- New remote layout; royal blue and gold theme.
- Only three shortcuts: YouTube, IPTV, Radio. Netflix, Prime Video, Disney+, Movies, Music and Browser were removed.
- "Return" is not a separate button because it duplicates Back.
