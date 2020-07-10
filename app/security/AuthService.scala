package security

import java.util.Date

import io.jsonwebtoken.{Claims, JwtException, Jwts, SignatureAlgorithm}
import javax.crypto.{KeyGenerator, SecretKey}
import models.AuthUser

import scala.util.{Failure, Success, Try}

object AuthService {

  // start play logger (logback with slf4j)
  val logger = play.api.Logger(getClass).logger

  private val EXPIRATION_TIME = 3600
  // Cannot access Java static method from Scala
  // private val key: SecretKey = Keys.secretKeyFor(SignatureAlgorithm.HS256)
  // create secretKey with HMAC SHA-256 with min length of 256
  val keyGenerator: KeyGenerator = KeyGenerator.getInstance("HMACSHA256")
  keyGenerator.init(256)
  val key: SecretKey = keyGenerator.generateKey

  private val tokenIssuer = "toptechBarnwaldo";

  def getAllClaimsFromToken(token: String): Claims = Jwts.parser.setSigningKey(key).parseClaimsJws(token).getBody

  def getUsernameFromToken(token: String): String = getAllClaimsFromToken(token).getSubject

  def getIssuerFromToken(token: String): String = getAllClaimsFromToken(token).getIssuer

  def getExpirationDateFromToken(token: String): Date = getAllClaimsFromToken(token).getExpiration


  def generateToken(authUser: AuthUser): String = {
    val token: String = doGenerateToken(authUser.getAuthorities(), authUser.username)
    // logger.info("Claims: " + claims.toString());
    // logger.info("Token: " + token);
    // logger.info("Username from Token: " + getUsernameFromToken(token));
    token
  }

  private def doGenerateToken(grantedAuthorities: String, username: String): String = {
    val millis = System.currentTimeMillis
    val issueDate = new Date(millis)
    val expirationDate = new Date(millis + EXPIRATION_TIME * 1000)
    Jwts.builder
      .claim("roles", grantedAuthorities)
      .setIssuer(tokenIssuer)
      .setSubject(username)
      .setIssuedAt(issueDate)
      .setExpiration(expirationDate)
      .signWith(SignatureAlgorithm.HS256, key)
      .compact
  }

  // check JWT is well formed and matches standard claims
  def validateToken(token: String): Try[Claims] = {
    try {
      val claims: Claims = Jwts.parser.setSigningKey(key).parseClaimsJws(token).getBody
      val millis: Long = System.currentTimeMillis
      val expiration: Date = claims.getExpiration
      val issuer: String = claims.getIssuer
      if (expiration.after(new Date(millis)) && issuer == tokenIssuer) {
        Success(claims)
      } else {
        Failure(new Exception("JWT Token does not match standard claims"))
      }
    }
    catch {
      case jwtException: JwtException => Failure(jwtException)
      case _:Throwable => Failure(new Exception("JWT Token is not well formed"))
    }
  }
}
