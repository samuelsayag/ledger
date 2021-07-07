Ledger
======

API that provide banking services.

Pre-requisite
-------------
* install docker/docker-compose
* endpoint example are give with [HTTPie](https://httpie.io/)

Usage
-----

1. Start the docker postgres container

```bash
$ docker-compose -f <project-dir>/docker/docker-compose.yml up
```

To shut down:

```bash
$ docker-compose -f <project-dir>/docker/docker-compose.yml down
```

2. start the application

```bash
$ sbt
> runMain ledger.Boot
```

The server listen to the port: `8080`.

To shut down (from withing the sbt console): `Ctrl + C`

End-points
----------

When the application start, it creates the schema in Postgres 
(Table: `users`, `accounts`, `transactions`, `postings`).

The `accounts` table contains the `CASH` account of the company (asset).

There are 4 end-points:

1. Create a deposit account 

```bash
$ http PUT localhost:8080/account/put name=Bob 
```

input:
```json
{
  "name": "Bob"
}
```