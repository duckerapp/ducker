"""Run the real publishing shell with local Git and fake hosting commands."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

import test_release_artifacts as fixtures


FAKE = r'''#!/usr/bin/env python3
import json, os, pathlib, shutil, subprocess, sys
args=sys.argv[1:]; tool=pathlib.Path(sys.argv[0]).name
state=pathlib.Path(os.environ['FIXTURE_STATE']); root=pathlib.Path.cwd()
if tool=='git':
    if args[:2]==['remote','get-url']:
        print('git@github-duckerapp:duckerapp/ducker.git' if args[2]=='origin' else 'git@gitlab-duckerx:duckerx/ducker.git')
    elif args[0]=='ls-remote': pass
    elif args[0]=='push':
        p=state/'pushes'; previous=p.read_text() if p.exists() else '';p.write_text(previous+' '.join(args)+'\n')
        if args[1]=='gitlab' and (state/'fail-final').exists() and (root/'update.json').exists():
            (state/'fail-final').unlink();sys.exit(1)
    elif args[0]=='config' and len(args)==2:
        print({'user.name':'ducker','user.email':'345288940+duckwright@users.noreply.github.com','commit.gpgsign':'true'}[args[1]])
    else:
        sys.exit(subprocess.call([os.environ['FIXTURE_GIT'], '-c','commit.gpgsign=false',*args]))
elif tool=='apksigner': print('Signer #1 certificate SHA-256 digest: '+'a'*64)
elif tool=='aapt2': print("package: name='app.ducker.android' versionCode='56' versionName='1.0.0'\nminSdkVersion:'26'")
elif tool=='gh':
    if args[:2]==['api','user']: print('duckwright')
    elif args[:2]==['release','view']:
        if '--json' in args and set(args[args.index('--json')+1].split(',')) != {'assets','isPrerelease','isDraft','body'}:sys.exit('unsupported gh JSON field')
        if not (state/'created').exists():sys.exit(1)
        print(json.dumps({'assets':[{'name':p.name} for p in (state/'github').glob('*')], 'isPrerelease':not (state/'stable').exists(),'isDraft':not (state/'visible').exists(),'body':(state/'body').read_text()}))
    elif args[:2]==['release','create']:
        (state/'created').touch();shutil.copyfile(args[args.index('--notes-file')+1],state/'body')
    elif args[:2]==['release','upload']:
        p=pathlib.Path(args[-1]);target=state/'github'/p.name
        if target.exists():sys.exit('attempted overwrite')
        shutil.copyfile(p,target)
    elif args[:2]==['release','download']:
        name=args[args.index('--pattern')+1];out=pathlib.Path(args[args.index('--dir')+1]);shutil.copyfile(state/'github'/name,out/name)
    elif args[:2]==['release','edit']:
        (state/'visible').touch()
        if '--prerelease=false' in args:(state/'stable').touch()
    else:sys.exit('unknown gh call')
elif tool=='curl':
    if '--upload-file' not in args and '--header' in args:sys.exit('credentials must not follow public download redirects')
    name=args[-1].rsplit('/',1)[-1];target=state/'gitlab'/name
    if '--upload-file' in args:
        if target.exists():sys.exit('attempted mirror overwrite')
        shutil.copyfile(args[args.index('--upload-file')+1],target)
    else:
        out=pathlib.Path(args[args.index('--output')+1]);out.write_bytes(target.read_bytes() if target.exists() else b'')
        print('200' if target.exists() else '404',end='')
else: sys.exit('unknown tool')
'''


class PublishTest(unittest.TestCase):
    def setUp(self):
        self.fixture = fixtures.ArtifactsTest()
        self.fixture.setUp()
        self.addCleanup(self.fixture.doCleanups)
        self.root = self.fixture.root
        scripts = self.root / 'scripts'
        real = Path(__file__).resolve().parents[1]
        for name in ('publish.sh', 'release_artifacts.py'):
            shutil.copyfile(real / name, scripts / name)
        (self.root / '.gitignore').write_text('out/\nstate/\nbin/\nsdk/\n**/__pycache__/\n')
        self.state = self.root / 'state'
        for name in ('github', 'gitlab'):
            (self.state / name).mkdir(parents=True)
        bin_dir = self.root / 'bin'
        bin_dir.mkdir()
        for name in ('git','gh','curl'):
            p=bin_dir/name;p.write_text(FAKE);p.chmod(0o755)
        sdk = self.root / 'sdk/build-tools/36.0.0'
        sdk.mkdir(parents=True)
        for name in ('apksigner','aapt2'):
            p=sdk/name;p.write_text(FAKE);p.chmod(0o755)
        self.env = dict(os.environ, FIXTURE_STATE=str(self.state), FIXTURE_GIT=shutil.which('git'),
                        PATH=str(bin_dir)+os.pathsep+os.environ['PATH'], ANDROID_HOME=str(self.root/'sdk'),
                        OUT_ROOT=str(self.root/'out'), GITLAB_TOKEN='fake-fixture-token')
        self.fixture.out.rename(self.root/'staging')
        (self.root/'out').mkdir()
        (self.root/'staging').rename(self.root/'out/v1.0.0')
        self.fixture.out=self.root/'out/v1.0.0'
        subprocess.run(['git','-C',str(self.root),'branch','-M','main'],check=True)
        subprocess.run(['git','-C',str(self.root),'add','.gitignore','scripts'],check=True)
        subprocess.run(['git','-C',str(self.root),'-c','user.name=ducker','-c','user.email=345288940+duckwright@users.noreply.github.com','-c','commit.gpgsign=false','commit','-qm','publishing fixture'],check=True)
        # Configure fixture-only commit identity; the wrapper disables real signing.
        for key,value in [('user.name','ducker'),('user.email','345288940+duckwright@users.noreply.github.com')]:
            subprocess.run(['git','-C',str(self.root),'config',key,value],check=True)
        fixtures.a.seal(self.fixture.out, '1.0.0', smoke_passed=True)

    def publish(self, *args):
        return subprocess.run(['bash','scripts/publish.sh','1.0.0',*args],cwd=self.root,env=self.env,capture_output=True,text=True)

    def test_publish_resume_and_promote_identical_files(self):
        (self.state/'fail-final').touch()
        first=self.publish()
        self.assertNotEqual(first.returncode,0,first.stdout+first.stderr)
        self.assertTrue((self.root/'update.json').exists(), first.stderr)
        retry=self.publish()
        self.assertEqual(retry.returncode,0,retry.stdout+retry.stderr)
        beta=json.loads((self.root/'update.json').read_text())['beta']
        promoted=self.publish('--stable')
        self.assertEqual(promoted.returncode,0,promoted.stdout+promoted.stderr)
        self.assertEqual(json.loads((self.root/'update.json').read_text())['stable'],beta)
        self.assertTrue((self.state/'stable').exists())
        # The immutable uploads remain byte-identical on both hosts.
        for p in (self.state/'github').iterdir():
            self.assertEqual(p.read_bytes(),(self.state/'gitlab'/p.name).read_bytes())

    def test_changed_uploaded_apk_is_never_clobbered(self):
        first=self.publish();self.assertEqual(first.returncode,0,first.stderr)
        (self.state/'github/ducker-1.0.0-arm64-v8a.apk').write_bytes(b'foreign bytes')
        retry=self.publish()
        self.assertNotEqual(retry.returncode,0)
        self.assertIn('GitHub artifact differs',retry.stderr)

    def test_local_mode_does_not_touch_hosts_or_tags(self):
        result=self.publish('--local');self.assertEqual(result.returncode,0,result.stderr)
        self.assertFalse((self.state/'created').exists())
        self.assertFalse((self.state/'pushes').exists())
        self.assertTrue((self.fixture.out/'update.json').exists())

    def test_live_publication_requires_exact_device_smoke(self):
        path=self.fixture.out/'release-manifest.json'
        manifest=json.loads(path.read_text());manifest['smokePassed']=False
        path.write_text(json.dumps(manifest))
        result=self.publish()
        self.assertNotEqual(result.returncode,0)
        self.assertIn('successful agreed device smoke',result.stderr)
        self.assertFalse((self.state/'pushes').exists())

    def test_mirror_corruption_blocks_stable_promotion(self):
        first=self.publish();self.assertEqual(first.returncode,0,first.stderr)
        (self.state/'gitlab/ducker-1.0.0-arm64-v8a.apk').write_bytes(b'corrupt mirror')
        result=self.publish('--stable')
        self.assertNotEqual(result.returncode,0)
        self.assertIn('GitLab artifact differs',result.stderr)
        self.assertFalse((self.state/'stable').exists())
