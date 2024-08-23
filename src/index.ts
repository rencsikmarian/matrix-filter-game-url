import { registerPlugin } from '@capacitor/core';

import type { NAIFilterGameUrlPlugin } from './definitions';

const NAIFilterGameUrl = registerPlugin<NAIFilterGameUrlPlugin>(
  'NAIFilterGameUrl',
  {
    web: () => import('./web').then(m => new m.NAIFilterGameUrlWeb()),
  },
);

export * from './definitions';
export { NAIFilterGameUrl };
