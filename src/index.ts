import { registerPlugin } from '@capacitor/core';

import type { NAIFilterGameUrlPlugin } from './definitions';

const NAIFilterGameUrl = registerPlugin<NAIFilterGameUrlPlugin>(
  'NAIFilterGameUrl',
  {},
);

export * from './definitions';
export { NAIFilterGameUrl };
