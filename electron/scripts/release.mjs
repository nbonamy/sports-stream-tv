import { spawnSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import path from 'node:path';
import { signAsync } from '@electron/osx-sign';
import { notarize } from '@electron/notarize';

if (process.platform !== 'darwin') throw new Error('Build the Mac release on macOS.');
if (existsSync('.env')) process.loadEnvFile('.env');
const required = ['IDENTIFY_DARWIN_CODE', 'APPLE_ID', 'APPLE_PASSWORD', 'APPLE_TEAM_ID'];
const missing = required.filter(name => !process.env[name]);
if (missing.length) throw new Error(`Missing signing settings: ${missing.join(', ')}`);
// Never print signing credentials, including in third-party command failures.
const redact = text => required.reduce((result, name) => result.replaceAll(process.env[name], '[redacted]'), String(text));
function run(command, args) {
  const result = spawnSync(command, args, { encoding: 'utf8', env: { ...process.env, DEBUG: '' } });
  if (result.stdout) process.stdout.write(redact(result.stdout));
  if (result.stderr) process.stderr.write(redact(result.stderr));
  if (result.error || result.status !== 0) throw new Error(`${command} failed`);
}
try {
  run('npm', ['run', 'pack']);
  const directory = process.arch === 'arm64' ? 'mac-arm64' : 'mac';
  const appPath = path.resolve('out', directory, 'Sports.app');
  if (!existsSync(appPath)) throw new Error('Packaged Sports.app was not found.');
  console.log('Signing Sports.app…');
  await signAsync({ app: appPath, identity: process.env.IDENTIFY_DARWIN_CODE,
    optionsForFile: () => ({ hardenedRuntime: true, entitlements: path.resolve('scripts/entitlements.mac.plist') }),
  });
  console.log('Submitting to Apple for notarization…');
  await notarize({ appPath, appleId: process.env.APPLE_ID, appleIdPassword: process.env.APPLE_PASSWORD, teamId: process.env.APPLE_TEAM_ID });
  run('codesign', ['--verify', '--deep', '--strict', appPath]);
  run('spctl', ['--assess', '--type', 'execute', appPath]);
  const archive = path.resolve('out', `Sports-mac-${process.arch}.zip`);
  run('ditto', ['-c', '-k', '--keepParent', '--sequesterRsrc', appPath, archive]);
  console.log(`Signed and notarized: ${appPath}\nArchive: ${archive}`);
} catch (error) {
  console.error(redact(error instanceof Error ? error.message : error));
  process.exitCode = 1;
}
