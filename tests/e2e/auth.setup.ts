import { test as setup } from '@playwright/test';
import { loginAs, writeStorageState } from './helpers/auth';
import { storageStates, type AccountKey } from './helpers/test-data';

setup.setTimeout(180_000);

setup('prepare role storage states', async () => {
  const roles: AccountKey[] = [
    'admin',
    'bizLeader',
    'bizStaff',
    'channelLeader',
    'channelStaff',
    'ops'
  ];
  for (const role of roles) {
    const auth = await loginAs(role);
    writeStorageState(storageStates[role], auth);
  }
});
