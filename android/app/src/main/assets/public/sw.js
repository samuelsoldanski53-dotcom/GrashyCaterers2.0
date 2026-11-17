/**
 * Copyright 2018 Google Inc. All Rights Reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// If the loader is already loaded, just stop.
if (!self.define) {
  let registry = {};

  // Used for `eval` and `importScripts` where we can't get script URL by other means.
  // In both cases, it's safe to use a global var because those functions are synchronous.
  let nextDefineUri;

  const singleRequire = (uri, parentUri) => {
    uri = new URL(uri + ".js", parentUri).href;
    return registry[uri] || (
      
        new Promise(resolve => {
          if ("document" in self) {
            const script = document.createElement("script");
            script.src = uri;
            script.onload = resolve;
            document.head.appendChild(script);
          } else {
            nextDefineUri = uri;
            importScripts(uri);
            resolve();
          }
        })
      
      .then(() => {
        let promise = registry[uri];
        if (!promise) {
          throw new Error(`Module ${uri} didn’t register its module`);
        }
        return promise;
      })
    );
  };

  self.define = (depsNames, factory) => {
    const uri = nextDefineUri || ("document" in self ? document.currentScript.src : "") || location.href;
    if (registry[uri]) {
      // Module is already loading or loaded.
      return;
    }
    let exports = {};
    const require = depUri => singleRequire(depUri, uri);
    const specialDeps = {
      module: { uri },
      exports,
      require
    };
    registry[uri] = Promise.all(depsNames.map(
      depName => specialDeps[depName] || require(depName)
    )).then(deps => {
      factory(...deps);
      return exports;
    });
  };
}
define(['./workbox-afac4cd2'], (function (workbox) { 'use strict';

  self.skipWaiting();
  workbox.clientsClaim();
  /**
   * The precacheAndRoute() method efficiently caches and responds to
   * requests for URLs in the manifest.
   * See https://goo.gl/S9QRab
   */
  workbox.precacheAndRoute([{
    "url": "pwa-maskable-512x512.png",
    "revision": "a5d614d4bc8010236a06c7ccbcf90616"
  }, {
    "url": "pwa-512x512.png",
    "revision": "58c188725324a691913f44c08662cdd3"
  }, {
    "url": "pwa-192x192.png",
    "revision": "337a1a0e83b458e5d1733ff00ecfa0d8"
  }, {
    "url": "index.html",
    "revision": "992cdf95567dcc18b6a3d9a0d477c5d4"
  }, {
    "url": "icon.svg",
    "revision": "0d8dfa2d38b539747c2b321d9b5b3c3e"
  }, {
    "url": "apple-touch-icon.png",
    "revision": "9df990337b97195ca17819b8a524e82a"
  }, {
    "url": "assets/workbox-window.prod.es5-Bd17z0YL.js",
    "revision": null
  }, {
    "url": "assets/web-bJkgBm6V.js",
    "revision": null
  }, {
    "url": "assets/web-BdsKKMzv.js",
    "revision": null
  }, {
    "url": "assets/web-80JC4GM3.js",
    "revision": null
  }, {
    "url": "assets/purify.es-Dzkdzyfv.js",
    "revision": null
  }, {
    "url": "assets/pdf.worker.min-CIqJB0yW.mjs",
    "revision": null
  }, {
    "url": "assets/index.es-PpRhdn3q.js",
    "revision": null
  }, {
    "url": "assets/index-qA9_ZWiB.js",
    "revision": null
  }, {
    "url": "assets/index-DuIGtt0j.css",
    "revision": null
  }, {
    "url": "assets/html2canvas-REmW9d5t.js",
    "revision": null
  }, {
    "url": "assets/grashy-logo.png",
    "revision": null
  }, {
    "url": "assets/grashy-footer-wave.png",
    "revision": null
  }, {
    "url": "apple-touch-icon.png",
    "revision": "9df990337b97195ca17819b8a524e82a"
  }, {
    "url": "icon.svg",
    "revision": "0d8dfa2d38b539747c2b321d9b5b3c3e"
  }, {
    "url": "pwa-192x192.png",
    "revision": "337a1a0e83b458e5d1733ff00ecfa0d8"
  }, {
    "url": "pwa-512x512.png",
    "revision": "58c188725324a691913f44c08662cdd3"
  }, {
    "url": "pwa-maskable-512x512.png",
    "revision": "a5d614d4bc8010236a06c7ccbcf90616"
  }, {
    "url": "assets/grashy-footer-wave.png",
    "revision": "617bcce95563aa953fe5f7089ba2c4c4"
  }, {
    "url": "assets/grashy-logo.png",
    "revision": "f609335e3af192654ce03ad0c1e80ec4"
  }, {
    "url": "manifest.webmanifest",
    "revision": "8fc8ee827d93df19136b8f572e8a3b81"
  }], {});
  workbox.cleanupOutdatedCaches();
  workbox.registerRoute(new workbox.NavigationRoute(workbox.createHandlerBoundToURL("index.html")));
  workbox.registerRoute(({
    url
  }) => url.pathname.startsWith("/assets/") || url.pathname.endsWith(".png"), new workbox.CacheFirst({
    "cacheName": "local-brand-assets",
    plugins: [new workbox.ExpirationPlugin({
      maxEntries: 30,
      maxAgeSeconds: 31536000
    }), new workbox.CacheableResponsePlugin({
      statuses: [0, 200]
    })]
  }), 'GET');
  workbox.registerRoute(/^https:\/\/fonts\.googleapis\.com\/.*/i, new workbox.CacheFirst({
    "cacheName": "google-fonts-cache",
    plugins: [new workbox.ExpirationPlugin({
      maxEntries: 12,
      maxAgeSeconds: 31536000
    }), new workbox.CacheableResponsePlugin({
      statuses: [0, 200]
    })]
  }), 'GET');
  workbox.registerRoute(/^https:\/\/fonts\.gstatic\.com\/.*/i, new workbox.CacheFirst({
    "cacheName": "gstatic-fonts-cache",
    plugins: [new workbox.ExpirationPlugin({
      maxEntries: 24,
      maxAgeSeconds: 31536000
    }), new workbox.CacheableResponsePlugin({
      statuses: [0, 200]
    })]
  }), 'GET');

}));
