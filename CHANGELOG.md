## 2.0

- Use lazy val to initialize SecureRandom, so that initialization occurs also async
- Refactor authenticators and add BearerTokenAuthenticator, JWTAuthenticator and SessionAuthenticator
- Better error handling for authenticators
- Authenticators now using an extra backing store instead of only the cache
- Split up SocialProvider.authenticate method into authenticate and retrieveProfile methods
- Remove authInfo from SocialProfile
- Add OAuth2 state implementation
- Documentation is now included in the repository and hosted on Read The Docs
- Renamed packages "core" to "api", "contrib" to "impl", "utils" to "util"
- Reorganized the project structure (moved all providers into the "impl" package, moved some classes/traits)
- Add request handlers
- Add request providers in combination with HTTP basic auth provider

## 1.0 (2014-06-12)

- First release for Play 2.3

## 0.9 (2014-06-12)

- First release for Play 2.2
