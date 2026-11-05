# X observations

X has no public client source. Butterflyfish reads data returned to X's own website;
website changes may require parser and UI work.

watched.json is an array of public GitHub owner/repo strings. It is empty because GitHub
was unreachable while authoring; Claude should verify candidate repositories and their
relevance before adding them. An empty list is a supported no-op. The daily watcher records
commit IDs and updates one issue, never copies source or changes the app. Licences of those
projects grant no implied permission to copy their code.
