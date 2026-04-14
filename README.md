# HoneypotAPI-ExtraStores

A plugin to provide extra data stores to Honeypot. [Check out Honeypot here!](https://github.com/TerrrorByte/Honeypot)

## Stores

- `datacontainer` - Provides the same support as the legacy PDC provider that Honeypot used to provide
    - PDC cannot, by nature, support purging all players or player history, nor can it support removing a specific
      number of player history records. PDC stores its data within the `Player` itself, so there is no source of truth
      for "all" players or records.
- `file` - An incomplete file-based data store that uses a YAML file to store data.
    - This is horrendously slow, all things considered, and should not be used in production unless other options just
      aren't available to you.
- `database` - A yet-to-be-implemented database-based data store that uses a MySQL database to store data.
    - Honeypot does not support asynchronous operations, so this store instead dynamically loads and unloads data from
      the database around a given player as needed. These operations are asynchronous, but since the data is loaded into
      memory, there is no risk of issues related to performance.
    - Note that there is a potential for Honeypot misses if a Player teleports, then triggers a Honeypot before the
      database can be queried for blocks nearby.
    - Configure the database via `config.yml`