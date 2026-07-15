# Krish RGB Controller — Test Build

Targets BLE device name `GATT-DEMO`, service `FFF0`, writable characteristic `FFF3`.

This is intentionally a protocol-test build. It includes:
- BLE scanning and connection
- Candidate Red / Yellow / White packets from the captured session
- Effect IDs `01` and `4C`
- Speed packet `BC 08 01 XX 55`
- Raw HEX sender

Open the folder in Android Studio, let Gradle sync, then Build > Build APK(s).

Important: the candidate color labels are not yet guaranteed. Test them on the actual rope light and note what each button does.
