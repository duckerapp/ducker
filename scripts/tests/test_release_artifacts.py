import copy
import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import zipfile

SCRIPT = Path(__file__).resolve().parents[1] / "release_artifacts.py"
spec = importlib.util.spec_from_file_location("artifacts", SCRIPT)
a = importlib.util.module_from_spec(spec)
spec.loader.exec_module(a)


class ArtifactsTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        (self.root / "scripts").mkdir()
        (self.root / "scripts/release-certificate.sha256").write_text("a" * 64 + "\n")
        (self.root / "docs").mkdir()
        (self.root / "build.gradle.kts").write_text('versionName = "1.0.0"\nversionCode = 56\nminSdk = 26\n')
        for name in ("CHANGELOG.md", "docs/CHANGELOG.ru.md"):
            (self.root / name).write_text('## 1.0.0 — 2026-11-15\n<!-- ignored -->\n- Fixed [link](https://example.com).\n  More detail.\n\n## 0.4.51\n- Old\n')
        self.out = self.root / "out"
        self.out.mkdir()
        for abi in a.ABIS:
            with zipfile.ZipFile(self.out / f"ducker-1.0.0-{abi}.apk", "w") as z:
                z.writestr(f"lib/{abi}/libclash.so", b"fixture, not executable")
                for i in range(7):
                    z.writestr(f"assets/licenses/{i}.txt", "licence")
        self.sums()
        (self.out / "release-notes.md").write_text("release notes")
        root_patch = patch.object(a, "ROOT", self.root)
        root_patch.start()
        self.addCleanup(root_patch.stop)
        tool_patch = patch.object(a, "tool", lambda name: name)
        tool_patch.start()
        self.addCleanup(tool_patch.stop)
        self.cert = "a" * 64
        self.code = 56
        self.package = a.PACKAGE
        self.sdk = 26
        self.real_run = a.run
        def fake_run(*args, **kwargs):
            if args[0] == "apksigner":
                return f"Signer #1 certificate SHA-256 digest: {self.cert}"
            if args[0] == "aapt2":
                return f"package: name='{self.package}' versionCode='{self.code}' versionName='1.0.0'\nminSdkVersion:'{self.sdk}'"
            return self.real_run(*args, **kwargs)
        run_patch = patch.object(a, "run", fake_run)
        run_patch.start()
        self.addCleanup(run_patch.stop)
        subprocess.run(["git", "init", "-q", str(self.root)], check=True)
        subprocess.run(["git", "-C", str(self.root), "add", "build.gradle.kts", "scripts", "docs", "CHANGELOG.md"], check=True)
        subprocess.run(["git", "-C", str(self.root), "-c", "user.name=Fixture", "-c", "user.email=fixture@example.invalid", "-c", "commit.gpgsign=false", "commit", "-qm", "fixture"], check=True)

    def sums(self):
        (self.out / "SHA256SUMS").write_text("".join(f"{a.sha(p)}  ./{p.name}\n" for p in sorted(self.out.glob("*.apk"))))

    def seal(self):
        return a.seal(self.out, "1.0.0")

    def test_valid_artifacts_bind_to_source_and_ignore_only_feed(self):
        self.seal()
        a.verify(self.out, "1.0.0", "HEAD")
        (self.root / "update.json").write_text('{}')
        a.verify(self.out, "1.0.0")
        (self.root / "build.gradle.kts").write_text('versionName = "1.0.1"\nversionCode = 57\nminSdk = 26\n')
        with self.assertRaisesRegex(ValueError, "source differs"):
            a.verify(self.out, "1.0.0")

    def test_foreign_certificate_with_same_display_name_is_rejected(self):
        self.cert = "b" * 64
        with self.assertRaisesRegex(ValueError, "certificate"):
            self.seal()

    def test_wrong_package_code_and_sdk_are_rejected(self):
        for field, value in (("package", "another.app"), ("code", 55), ("sdk", 29)):
            old = getattr(self, field)
            setattr(self, field, value)
            with self.subTest(field=field), self.assertRaises(ValueError):
                self.seal()
            setattr(self, field, old)

    def test_missing_or_extra_abi_is_rejected(self):
        path = self.out / "ducker-1.0.0-arm64-v8a.apk"
        path.rename(self.out / "ducker-1.0.0-x86.apk")
        with self.assertRaisesRegex(ValueError, "two ARM"):
            self.seal()

    def test_checksum_and_tampering_are_rejected(self):
        self.seal()
        (self.out / "SHA256SUMS").write_text("0" * 64 + "  ./wrong.apk\n")
        with self.assertRaisesRegex(ValueError, "SHA256SUMS"):
            a.verify(self.out, "1.0.0")
        self.sums()
        (self.out / "release-notes.md").write_text("changed after seal")
        with self.assertRaisesRegex(ValueError, "changed after sealing"):
            a.verify(self.out, "1.0.0")

    def test_manifest_cannot_be_resealed_with_different_bytes(self):
        self.seal()
        (self.out / "release-notes.md").write_text("new notes")
        with self.assertRaisesRegex(ValueError, "manifest differs"):
            self.seal()

    def test_beta_promotion_is_identical_and_cannot_downgrade(self):
        manifest = self.seal()
        empty = dict(stable=None, beta=None, mirrors=[])
        beta = a.candidate(empty, manifest, self.out, ["https://example.invalid/"], False)
        stable = a.candidate(beta, manifest, self.out, [], True)
        self.assertEqual(stable["stable"], beta["beta"])
        self.assertNotIn("<!--", beta["beta"]["notes"]["en"])
        self.assertEqual(a.candidate(beta, manifest, self.out, ["https://example.invalid/"], False), beta)
        damaged = copy.deepcopy(beta)
        damaged["beta"]["apk"]["arm64-v8a"]["sha256"] = "b" * 64
        with self.assertRaises(ValueError):
            a.candidate(damaged, manifest, self.out, [], True)
        newer = copy.deepcopy(beta)
        newer["stable"] = dict(beta["beta"], code=57)
        with self.assertRaises(ValueError):
            a.candidate(newer, manifest, self.out, [], True)
        with self.assertRaises(ValueError):
            a.candidate(empty, manifest, self.out, [], True)

    def test_missing_sdk_tool_is_fatal(self):
        with patch.object(a, "ROOT", self.root), patch.dict(a.os.environ, {"ANDROID_HOME": str(self.root), "ANDROID_SDK_ROOT": str(self.root)}), patch.object(a.Path, "home", return_value=self.root):
            # Use the real resolver, not setUp's tool stub.
            resolver = spec.loader
            other = importlib.util.module_from_spec(spec)
            resolver.exec_module(other)
            other.ROOT = self.root
            with self.assertRaisesRegex(ValueError, "tool required"):
                other.tool("apksigner")

    def test_emulator_fixture_cannot_be_used_for_live_publication(self):
        for p in self.out.glob('*.apk'):
            p.unlink()
        with zipfile.ZipFile(self.out / 'ducker-1.0.0-x86_64.apk', 'w') as z:
            z.writestr('lib/x86_64/libclash.so', b'fixture')
            for i in range(7):
                z.writestr(f'assets/licenses/{i}', 'licence')
        self.sums()
        manifest = a.seal(self.out, '1.0.0', fixture=True)
        self.assertEqual(manifest['abis'], ['x86_64'])
        a.verify(self.out, '1.0.0', allow_fixture=True)
        with self.assertRaisesRegex(ValueError, 'cannot be published'):
            a.verify(self.out, '1.0.0')

    def test_first_release_waits_for_wallets_and_final_changelog(self):
        support=self.root/'app/src/main/java/com/github/kr328/clash/ui/settings/ProjectSupport.kt'
        support.parent.mkdir(parents=True)
        support.write_text('  DonationWallet("BTC", "Bitcoin")\n')
        with self.assertRaisesRegex(ValueError, 'wallet addresses'):
            a.release_inputs('1.0.0')
        support.write_text('  DonationWallet("BTC", "Bitcoin", "fixture-address")\n')
        a.release_inputs('1.0.0')
        (self.root/'CHANGELOG.md').write_text('## 1.0.0 — draft\n- Pending\n')
        with self.assertRaisesRegex(ValueError, 'changelog'):
            a.release_inputs('1.0.0')
