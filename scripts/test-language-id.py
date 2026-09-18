#!/usr/bin/env python3
"""Install external speech fixtures into a debug app for VoskSpeechLanguageTest.

Usage: python3 scripts/test-language-id.py /path/to/spoken-language-identification-test-wavs
Requires adb on PATH and the debug APK installed. Fixtures are deliberately not
redistributed in this repository. Download the test archive linked from:
https://k2-fsa.github.io/sherpa/onnx/spoken-language-identification/pretrained_models.html
"""
import pathlib
import subprocess
import sys
import wave

source = pathlib.Path(sys.argv[1])
package = "com.charles.livecaptionn"
subprocess.run(["adb", "shell", "run-as", package, "mkdir", "-p", "files/language-id-test"], check=True)
for code, name in (("en", "en-english.wav"), ("de", "de-german.wav")):
    with wave.open(str(source / name), "rb") as audio:
        assert (audio.getframerate(), audio.getnchannels(), audio.getsampwidth()) == (16000, 1, 2)
        pcm = audio.readframes(audio.getnframes())
    subprocess.run(["adb", "shell", "run-as", package, "sh", "-c",
                    f"'cat > files/language-id-test/{code}.pcm'"], input=pcm, check=True)
print("Fixtures installed. Run connectedGithubDebugAndroidTest with class com.charles.livecaptionn.VoskSpeechLanguageTest.")
