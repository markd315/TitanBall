Software is provided as a work-in-progress, as-is, with no guarantee of performance, reliability, security, or safety.

In the interest of transparency, it is disclosed that user emails and passwords are stored in a mariadb database on an AWS ec2 server, protected by SSH.

The passwords are hashed with Bcrypt and the server is built with Spring Boot (2.2.2) HTTPS endpoints and Kryonet.

Out-of-game communications (account and matchmaking-related communications) between the client and matchmaker are encrypted by self-signed certificate TLS.

In-game connectivity is not encrypted, but includes only emails and domain-specific authorization tokens (JWT), not plaintext passwords.

ALL of the above information is provided in good faith so that users can make a more informed decision as to the risks of using this software, but is not guaranteed to be accurate.