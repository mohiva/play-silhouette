## 1.1

- Use lazy val to initialize SecureRandom, so that initialization occurs also async
- Refactor authenticators and add HeaderAuthenticator and SessionAuthenticator
- Split up SocialProvider.authenticate method into authenticate and retrieveProfile methods
- Remove authInfo from SocialProfile

## 1.0 (2014-06-12)

- First release for Play 2.3

## 0.9 (2014-06-12)

- First release for Play 2.2
