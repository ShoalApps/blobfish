# UI language catalogues

The default is Android's system or per-app language. Settings can select another language or return to the system language. Android 13+ also exposes the declared languages in system settings.

`app/src/main/assets/i18n/en.json` is the British English source catalogue. Other JSON files translate its phrases. Keep `{0}`, `{1}` and other numbered interpolations unchanged. Unknown phrases and missing translations fall back to British English. `coverage.json` records actual coverage; language choices include partial community catalogues, not a promise of universal translation.

Run `python3 tools/extract-ui-catalogue.py` after adding UI phrases, then update the affected translations and run `python3 tools/check-locales.py`. The check also runs in pull-request CI. Names and user content should use `localise = false`; annotated rich content is passed through unchanged.

Initial catalogues combine exact matching NewPipe community labels (GPL-3.0-or-later, attribution in i18n/NOTICE.txt) and locally generated Argos Translate/CTranslate2 machine translations. Translation uses no runtime SDK, server, account or network permission. Automatic text needs review by speakers of each language. Never advertise full language coverage without checking the coverage file and the UI.
