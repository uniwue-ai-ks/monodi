package de.olyro.monodi.exprt.print.markdown

import java.net.URI
import java.util.Base64
import scala.util.{Try, Using}

object ImageFetcher:
  def fetchImage(url: String): Option[String] =
    Try {
      val uri = URI.create(url)
      val connection = uri.toURL.openConnection()
      connection.setConnectTimeout(5000)
      connection.setReadTimeout(5000)
      
      Using(connection.getInputStream) { inputStream =>
        val imageBytes = inputStream.readAllBytes()
        val base64 = Base64.getEncoder.encodeToString(imageBytes)
        Some(s"data:image/png;base64,$base64")
      }.get
    }.recoverWith { case e =>
      System.err.println(s"Failed to fetch image from $url: ${e.getMessage}")
      Try(None)
    }.toOption.flatten
