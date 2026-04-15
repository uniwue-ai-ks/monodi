package de.olyro.monodi
package service

import cats.effect.*
import java.security.*
import java.security.cert.*
import java.nio.file.{Files, Paths}
import java.math.BigInteger
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import sun.security.x509.*

object Cert:
  private val certPath = Paths.get("/root/certificate.p12")
  private val password = "1".toCharArray
  private val alias = "1"

  private lazy val (privateKeyValue, publicKeyValue) = loadOrGenerateCertificate()

  def privateKey: PrivateKey = privateKeyValue
  def publicKey: PublicKey = publicKeyValue

  private def loadOrGenerateCertificate(): (PrivateKey, PublicKey) =
    if Files.exists(certPath) then
      try
        loadFromP12()
      catch
        case e: Exception =>
          println(s"Failed to load certificate from ${certPath}: ${e.getMessage}")
          println("Generating new certificate in memory...")
          generateCertificate()
    else
      println(s"Certificate not found at ${certPath}, generating new one in memory...")
      generateCertificate()

  private def loadFromP12(): (PrivateKey, PublicKey) =
    val keyStore = KeyStore.getInstance("PKCS12")
    val inputStream = Files.newInputStream(certPath)
    try
      keyStore.load(inputStream, password)
      val entry = keyStore.getEntry(alias, new KeyStore.PasswordProtection(password))
        .asInstanceOf[KeyStore.PrivateKeyEntry]
      val privateKey = entry.getPrivateKey
      val publicKey = entry.getCertificate.getPublicKey
      println(s"Successfully loaded certificate from ${certPath}")
      (privateKey, publicKey)
    finally
      inputStream.close()

  private def generateCertificate(): (PrivateKey, PublicKey) =
    val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
    keyPairGenerator.initialize(2048)
    val keyPair = keyPairGenerator.generateKeyPair()
    
    println("Generated new RSA key pair (in-memory only)")
    (keyPair.getPrivate, keyPair.getPublic)
