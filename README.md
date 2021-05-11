# Coinbase Scala Websocket Client
Akka-Typed Actor-based client to ingest Coinbase websocket events and persist them to Postgres without blocking. Actors monitor themselves to ensure the database and websocket connections are alive. If they're not they're restarted internal to the actor. Actors restart if they fail.
