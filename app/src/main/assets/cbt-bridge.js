// Disuntikkan ke halaman server ujian sebelum skrip halaman berjalan.
// Menambahkan header aplikasi + tanda tangan ke setiap request AJAX/fetch ke
// server ujian (Inertia memakai XHR untuk navigasi dan simpan jawaban), lalu
// memblokir salin/tempel dan menu tekan-lama seperti aplikasi desktop.
(function () {
  if (window.__cbtBridge) return;
  window.__cbtBridge = true;

  var bridge = window.CBTNative;

  function headers() {
    try {
      return bridge ? JSON.parse(bridge.headers()) : {};
    } catch (e) {
      return {};
    }
  }

  function sameOrigin(url) {
    try {
      return new URL(String(url), location.href).origin === location.origin;
    } catch (e) {
      return false;
    }
  }

  var xhrOpen = XMLHttpRequest.prototype.open;
  var xhrSend = XMLHttpRequest.prototype.send;

  XMLHttpRequest.prototype.open = function (method, url) {
    this.__cbtUrl = url;
    return xhrOpen.apply(this, arguments);
  };

  XMLHttpRequest.prototype.send = function () {
    if (sameOrigin(this.__cbtUrl)) {
      var h = headers();
      for (var name in h) {
        try {
          this.setRequestHeader(name, h[name]);
        } catch (e) {}
      }
    }
    return xhrSend.apply(this, arguments);
  };

  if (window.fetch) {
    var originalFetch = window.fetch;
    window.fetch = function (input, init) {
      try {
        var url = typeof input === "string" ? input : (input && input.url) || input;
        if (sameOrigin(url)) {
          var base = (init && init.headers) || (input instanceof Request ? input.headers : undefined);
          var merged = new Headers(base);
          var h = headers();
          for (var name in h) merged.set(name, h[name]);
          init = Object.assign({}, init || {}, { headers: merged });
        }
      } catch (e) {}
      return originalFetch.call(this, input, init);
    };
  }

  ["copy", "cut", "paste", "contextmenu", "dragstart"].forEach(function (type) {
    document.addEventListener(
      type,
      function (e) {
        e.preventDefault();
      },
      true
    );
  });

  window.print = function () {};
})();
