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

### 1. Create a deposit account 

```bash
$ http POST localhost:8080/account/put name='Ted'

HTTP/1.1 200 OK
Content-Length: 1
Content-Type: application/json
Date: Wed, 07 Jul 2021 12:28:02 GMT
Server: akka-http/10.2.4

2

```

### 2. Perform transactions:

Deposit:

```bash

$ http POST localhost:8080/transaction/put Deposit:='{"name": "Ted","amount": 2000 }'
HTTP/1.1 200 OK
Content-Length: 2
Content-Type: application/json
Date: Wed, 07 Jul 2021 12:27:33 GMT
Server: akka-http/10.2.4

{}
```

Withdraw: 

```json
$ http POST localhost:8080/transaction/put Withdraw:='{"name": "Ted","amount": 150}'
HTTP/1.1 200 OK
Content-Length: 2
Content-Type: application/json
Date: Wed, 07 Jul 2021 12:30:10 GMT
Server: akka-http/10.2.4

{}
```

Book (assuming `Bob` account is `3`):

```json
$ http POST localhost:8080/transaction/put Book:='{"name": "Ted","amount": 500, "accountId": 3}'

HTTP/1.1 200 OK
Content-Length: 2
Content-Type: application/json
Date: Wed, 07 Jul 2021 12:33:26 GMT
Server: akka-http/10.2.4

{}

```

### 3. Get account information (balance):

```json
$ http POST localhost:8080/account/get name='Ted'

HTTP/1.1 200 OK
Content-Length: 88
Content-Type: application/json
Date: Wed, 07 Jul 2021 12:35:53 GMT
Server: akka-http/10.2.4


{
  "accountType": {
    "Deposit": {
      "owner": {
        "id": 1,
        "name": "Ted"
      }
    }
  },
  "balance": 1850,
  "number": 2
}

```

### 4. Transaction informations

```json
$ http POST localhost:8080/transaction/get name='Ted'

HTTP/1.1 200 OK
Content-Length: 389
Content-Type: application/json
Date: Wed, 07 Jul 2021 12:40:27 GMT
Server: akka-http/10.2.4

[
  {
    "accountId": 1,
    "credit": 2000.0,
    "debit": null,
    "transactionId": 1,
    "transactionType": {
    "Deposit": {}
  }
  },
  {
    "accountId": 1,
    "credit": null,
    "debit": 150.0,
    "transactionId": 2,
    "transactionType": {
    "Withdraw": {}
  }
  },
  {
    "accountId": 3,
    "credit": null,
    "debit": 500.0,
    "transactionId": 3,
    "transactionType": {
    "Withdraw": {}
  }
  },
  {
    "accountId": 1,
    "credit": null,
    "debit": 500.0,
    "transactionId": 3,
    "transactionType": {
    "Withdraw": {}
  }
  }
]

```

Bugs
----

While playing with it I see some bugs that still need fixing:

* Restart the database reinsert a CASH account (should be unique)
* Can create account with the same user multiple time
* ...