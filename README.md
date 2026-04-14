# HoneypotAPI-ExtraStores

A plugin to provide extra data stores to Honeypot. [Check out Honeypot here!](https://github.com/TerrrorByte/Honeypot)

## Stores

- `mysql` - A database-backed data store that uses a MySQL (Or MariaDB) database to store data.
    - Honeypot does not support asynchronous operations, so this store instead loads and unloads Honeypot blocks from
      storage based on when the _server_ (not a player) loads/unloads chunks. The stored data is kept in-memory for
      synchronous querying. Changes to the in-memory data are reflected in the database asynchronously.
    - Configure the database via `config.yml`
    - The database user must be able to SELECT, INSERT, UPDATE, DELETE, and CREATE tables and indexes.
    - You must start your server at least once to generate the configuration file needed to configure this store.
      Configure the store, set Honeypot to use the `mysql` data store, then reboot your server again.

## Unsupported Stores

Honeypot does not support data stores that rely on the presence of players/entities, or are asynchronous in nature.

In Honeypot 4, PDC was removed. Plans originally existed to support PDC via an official storage provider add-on,
but it was abandoned due to PDC, by nature, not being able to support all the features that Honeypot provides.

This is because PDC typically stores data inside an entity or block of some sort, yet we must be able to query all data
regardless of where it's stored. Fragmenting data storage across multiple entities/blocks that may or may not exist when
needed makes it impossible to operate.