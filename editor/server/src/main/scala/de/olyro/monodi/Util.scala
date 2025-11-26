package de.olyro.monodi

import cats.data.Validated.*
import cats.data.*
import cats.effect.{Ref as _, *}
import io.circe.{Decoder, Encoder}
import zio.{IO as _, *}
import scala.jdk.CollectionConverters.*

import scala.math.Ordering.Implicits.*
import java.io.{Console as _, IO as _, *}
import java.net.URI
import java.net.http.{HttpClient as JHttpClient, *}
import java.nio.charset.StandardCharsets
import java.util.regex.*
import java.util.zip.{ZipEntry, ZipOutputStream}
import java.util.{List as _, *}
import java.nio.file.*
import scala.annotation.tailrec
import scala.util.{Try, Using}
import de.olyro.monodi.upload.zip.ZipImport

object Util:
  object scalatagsImplicits extends scalatags.Text.Cap with scalatags.Text.Aggregate

  def stringify(t: Throwable): String =
    val sw = new StringWriter
    t.printStackTrace(new PrintWriter(sw))
    sw.toString

  def clamp[T: Ordering](value: T, lower: T, upper: T): T = value.max(lower).min(upper)

  def mmToPixel(mm: Double): Double = math.floor(mm * 3.7795275591)

  def pixelToMm(pixel: Double): Double = math.ceil(pixel * 0.2645833333)

  /*
   * This is just a magic number that works well.
   * Since our font-size is 16 svg units by default,
   * this means that a font character will be roughly 4mm high.
   */
  val mmToSvgUnitsConstant = 4

  def readResource(c: Class[?], name: String): IO[String] =
    IO:
      val s = c.getResourceAsStream(name)
      try
        new Scanner(s, "utf-8").useDelimiter("\\Z").next()
      finally
        s.close()

  def unsafeReadResource(c: Class[?], name: String): String =
    Option(c.getResourceAsStream(name)).map(is => new String(is.readAllBytes(), StandardCharsets.UTF_8)).get

  def unsafeWithResource[A](c: Class[?], name: String, f: InputStream => A): A =
    val s = c.getResourceAsStream(name)
    try
      f(s)
    finally
      s.close()

  def unsafeCopy(c: Class[?], filename: String, outPath: java.nio.file.Path): Unit =
    import java.nio.file.*
    Util.unsafeWithResource(
      c,
      filename,
      is =>
        Files.write(outPath `resolve` filename, is.readAllBytes())
        ()
    )

  def isMailAddress(s: String): Boolean = mailPattern.matcher(s).matches

  def flattenVNel[E, A](a: ValidatedNel[E, ValidatedNel[E, A]]): ValidatedNel[E, A] =
    a match
      case Invalid(e) => Invalid(e)
      case Valid(aa)  => aa

  def sha256(s: String): String =
    sha256(s.getBytes(java.nio.charset.StandardCharsets.UTF_8))

  def sha256(b: Array[Byte]): String =
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    val hash   = digest.digest(b)
    val sb     = new StringBuilder()
    for b <- hash do sb.append("%02X".format(b))
    sb.toString

  def updateHeadOrCreate[A](l: List[A])(f: A => A, a: A): List[A] =
    l match
      case Nil    => a :: Nil
      case h :: t => f(h) :: t

  def intersperse[A](l: List[A], a: A): List[A] =
    @tailrec
    def go(accu: List[A], rest: List[A]): List[A] =
      rest match
        case Nil              => accu
        case h :: Nil         => h :: accu
        case h1 :: h2 :: tail => go(a :: h1 :: accu, h2 :: tail)

    go(Nil, l).reverse

  def collectAllMessages(t: Throwable): List[String] =
    @tailrec
    def go(accu: List[String], t: Throwable): List[String] =
      if t == null then accu
      else go(s"${t.getMessage} \n${t.getStackTrace.map(_.toString).mkString("\n")}" :: accu, t.getCause)

    go(Nil, t).reverse

  def debugWriteToFile(obj: Any, path: Path): Try[Unit] =
    Using(new BufferedWriter(new FileWriter(path.toFile))) { writer =>
      pprint.tokenize(obj, height = Int.MaxValue).map(_.plainText).foreach(writer.write)
    }

  object CirceNioInstances:
    import java.nio.file.Path

    given encPath: Encoder[Path] = Encoder[String].contramap((_: Path).toString)
    given decPath: Decoder[Path] = Decoder[String].map(s => Path `of` s)

  object http:

    trait HttpClient:
      def get(url: URI): Task[String]
      // ...

    def get(url: URI): ZIO[HttpClient, Throwable, String] = ZIO.serviceWithZIO(_.get(url))

    val live: ZLayer[Any, Throwable, HttpClient] = ZLayer
      .fromZIO(for
        sslParams <- ZIO.attempt {
                       val params = javax.net.ssl.SSLContext.getDefault.getDefaultSSLParameters
                       params.setProtocols(Array("TLSv1.2"))
                       params
                     }
        client    <-
          ZIO.attempt(
            JHttpClient.newBuilder().sslParameters(sslParams).followRedirects(JHttpClient.Redirect.ALWAYS).build()
          )
      yield new HttpClient {
        override def get(url: URI): Task[String] =
          ZIO.fromCompletionStage(
            client
              .sendAsync(HttpRequest.newBuilder(url).build(), HttpResponse.BodyHandlers.ofString())
              .thenApply(_.body())
          )
      })

  sealed trait Atomic[A]:
    def update[R, E, B](f: A => ZIO[R, E, (A, B)]): ZIO[R, E, B]
    def get: UIO[A]

  object Atomic:
    def make[A](a: A): UIO[Atomic[A]] =
      for
        semaphore <- Semaphore.make(1)
        ref       <- Ref.make[A](a)
      yield new Atomic[A]:
        override def get: UIO[A] = ref.get

        override def update[R, E, B](f: A => ZIO[R, E, (A, B)]): ZIO[R, E, B] =
          semaphore.withPermit(for
            current        <- ref.get
            (next, result) <- f(current)
            _              <- ref.set(next)
          yield result)

  sealed trait Fence:
    def measure[R, E, A](step: String)(program: ZIO[R, E, A]): ZIO[R, E, A]
    def results: UIO[List[(String, Long)]]

  object Fence:
    def measure[R, E, A](step: String)(program: ZIO[R, E, A]): ZIO[R & Fence, E, A] =
      ZIO.serviceWithZIO[Fence](_.measure[R, E, A](step)(program))

    def measurePure[A](step: String)(a: => A): URIO[Fence, A] = measure(step)(ZIO.succeed(a))

    def results = ZIO.serviceWithZIO[Fence](_.results)

    def live: ULayer[Fence] = ZLayer.fromZIO(
      for steps <- Ref.make(scala.collection.immutable.Map.empty[String, Long])
      yield new Fence {
        override def measure[R, E, A](step: String)(program: ZIO[R, E, A]): ZIO[R, E, A] = for
          start <- ZIO.succeed(java.lang.System.nanoTime())
          res   <- program
          end   <- ZIO.succeed(java.lang.System.nanoTime())
          diff   = end - start
          _     <- steps.update(old => old.updatedWith(step)(_.map(_ + diff).orElse(Some(diff))))
        yield res

        override def results: UIO[List[(String, Long)]] = steps.get.map(_.toList)
      }
    )

    def print(results: List[(String, Long)]): UIO[Unit] = ZIO
      .foreach(results.sortBy(_._1)) { case (name, time) => Console.printLine(s"$name: ${prettyPrintTime(time)}") }
      .ignore

    private def prettyPrintTime(ns: Long) =
      val raw = ns.toDouble / 1e9
      val s   = s"${"%.3f".format(raw % 60)}s"
      val m   = if raw > 60 then s"${Math.floor(raw / 60).toInt % 60}m" else ""
      val h   = if raw > 3600 then s"${Math.floor(raw / 3600).toInt}h" else ""
      List(h, m, s).filterNot(_.isEmpty).mkString(" ")

  sealed trait ZipFile:
    def append(blob: Array[Byte], path: String*): UIO[Unit]
    def =<<(entry: (Array[Byte], List[String])): UIO[Unit] = append(entry._1, entry._2*)

  object ZipFile:
    import java.nio.file.*

    def make(path: Path) = ZIO
      .acquireRelease(ZIO.attemptBlocking {
        if !Files.isRegularFile(path) then {
          // fixme this fails for paths like /to/existing.file/bad...
          Files.createDirectories(path.getParent)
          ()
        }
        new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(path.toFile)), StandardCharsets.UTF_8)
      })(zos =>
        ZIO.attemptBlocking {
          zos.flush()
          zos.close()
        }.ignore,
      )
      .map(zos =>
        new ZipFile {
          override def append(blob: Array[Byte], path: String*): UIO[Unit] = ZIO.attemptBlocking {
            zos.putNextEntry(new ZipEntry(path.map(ZipImport.encodeId).mkString("/")))
            zos.write(blob)
            zos.closeEntry()
          }.orDie
        },
      )

  def deleteAll(path: Path): Task[Unit] =
    ZIO.scoped:
      for
        stream <- ZIO.attemptBlocking(Files.walk(path))
        _      <- ZIO.addFinalizer(ZIO.attemptBlocking(stream.close()).ignore)
        asList  = stream.iterator().asScala.toList
        _      <- ZIO.foreach(asList.reverse)(path => ZIO.attemptBlocking(Files.deleteIfExists(path)).ignore)
      yield ()

  def createCleanTempDir(prefix: String): ZIO[Scope, Throwable, Path] =
    for
      path <- ZIO.attemptBlocking(Files.createTempDirectory(Paths.get("/tmp"), prefix))
      _    <- ZIO.addFinalizer(deleteAll(path).ignore)
    yield path

  // This is only at the end of the file because vim can't handle such long lines
  // and automatic indentation takes forever after that line
  private val mailPattern = Pattern.compile(
    "(?:(?:\\r\\n)?[ \\t])*(?:(?:(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\"(?:[^\\\"\\r\\\\]|\\\\.|(?:(?:\\r\\n)?[ \\t]))*\"(?:(?:\\r\\n)?[ \\t])*)(?:\\.(?:(?:\\r\\n)?[ \\t])*(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\"(?:[^\\\"\\r\\\\]|\\\\.|(?:(?:\\r\\n)?[ \\t]))*\"(?:(?:\\r\\n)?[ \\t])*))*@(?:(?:\\r\\n)?[ \\t])*(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\\[([^\\[\\]\\r\\\\]|\\\\.)*\\](?:(?:\\r\\n)?[ \\t])*)(?:\\.(?:(?:\\r\\n)?[ \\t])*(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\\[([^\\[\\]\\r\\\\]|\\\\.)*\\](?:(?:\\r\\n)?[ \\t])*))*|(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\"(?:[^\\\"\\r\\\\]|\\\\.|(?:(?:\\r\\n)?[ \\t]))*\"(?:(?:\\r\\n)?[ \\t])*)*\\<(?:(?:\\r\\n)?[ \\t])*(?:@(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\\[([^\\[\\]\\r\\\\]|\\\\.)*\\](?:(?:\\r\\n)?[ \\t])*)(?:\\.(?:(?:\\r\\n)?[ \\t])*(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\\[([^\\[\\]\\r\\\\]|\\\\.)*\\](?:(?:\\r\\n)?[ \\t])*))*(?:,@(?:(?:\\r\\n)?[ \\t])*(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\\[([^\\[\\]\\r\\\\]|\\\\.)*\\](?:(?:\\r\\n)?[ \\t])*)(?:\\.(?:(?:\\r\\n)?[ \\t])*(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\\[([^\\[\\]\\r\\\\]|\\\\.)*\\](?:(?:\\r\\n)?[ \\t])*))*)*:(?:(?:\\r\\n)?[ \\t])*)?(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\"(?:[^\\\"\\r\\\\]|\\\\.|(?:(?:\\r\\n)?[ \\t]))*\"(?:(?:\\r\\n)?[ \\t])*)(?:\\.(?:(?:\\r\\n)?[ \\t])*(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\"(?:[^\\\"\\r\\\\]|\\\\.|(?:(?:\\r\\n)?[ \\t]))*\"(?:(?:\\r\\n)?[ \\t])*))*@(?:(?:\\r\\n)?[ \\t])*(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\\[([^\\[\\]\\r\\\\]|\\\\.)*\\](?:(?:\\r\\n)?[ \\t])*)(?:\\.(?:(?:\\r\\n)?[ \\t])*(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\\[([^\\[\\]\\r\\\\]|\\\\.)*\\](?:(?:\\r\\n)?[ \\t])*))*\\>(?:(?:\\r\\n)?[ \\t])*)|(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\"(?:[^\\\"\\r\\\\]|\\\\.|(?:(?:\\r\\n)?[ \\t]))*\"(?:(?:\\r\\n)?[ \\t])*)*:(?:(?:\\r\\n)?[ \\t])*(?:(?:(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\"(?:[^\\\"\\r\\\\]|\\\\.|(?:(?:\\r\\n)?[ \\t]))*\"(?:(?:\\r\\n)?[ \\t])*)(?:\\.(?:(?:\\r\\n)?[ \\t])*(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\"(?:[^\\\"\\r\\\\]|\\\\.|(?:(?:\\r\\n)?[ \\t]))*\"(?:(?:\\r\\n)?[ \\t])*))*@(?:(?:\\r\\n)?[ \\t])*(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\\[([^\\[\\]\\r\\\\]|\\\\.)*\\](?:(?:\\r\\n)?[ \\t])*)(?:\\.(?:(?:\\r\\n)?[ \\t])*(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\\[([^\\[\\]\\r\\\\]|\\\\.)*\\](?:(?:\\r\\n)?[ \\t])*))*|(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\"(?:[^\\\"\\r\\\\]|\\\\.|(?:(?:\\r\\n)?[ \\t]))*\"(?:(?:\\r\\n)?[ \\t])*)*\\<(?:(?:\\r\\n)?[ \\t])*(?:@(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\\[([^\\[\\]\\r\\\\]|\\\\.)*\\](?:(?:\\r\\n)?[ \\t])*)(?:\\.(?:(?:\\r\\n)?[ \\t])*(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\\[([^\\[\\]\\r\\\\]|\\\\.)*\\](?:(?:\\r\\n)?[ \\t])*))*(?:,@(?:(?:\\r\\n)?[ \\t])*(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\\[([^\\[\\]\\r\\\\]|\\\\.)*\\](?:(?:\\r\\n)?[ \\t])*)(?:\\.(?:(?:\\r\\n)?[ \\t])*(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\\[([^\\[\\]\\r\\\\]|\\\\.)*\\](?:(?:\\r\\n)?[ \\t])*))*)*:(?:(?:\\r\\n)?[ \\t])*)?(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\"(?:[^\\\"\\r\\\\]|\\\\.|(?:(?:\\r\\n)?[ \\t]))*\"(?:(?:\\r\\n)?[ \\t])*)(?:\\.(?:(?:\\r\\n)?[ \\t])*(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\"(?:[^\\\"\\r\\\\]|\\\\.|(?:(?:\\r\\n)?[ \\t]))*\"(?:(?:\\r\\n)?[ \\t])*))*@(?:(?:\\r\\n)?[ \\t])*(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\\[([^\\[\\]\\r\\\\]|\\\\.)*\\](?:(?:\\r\\n)?[ \\t])*)(?:\\.(?:(?:\\r\\n)?[ \\t])*(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\\[([^\\[\\]\\r\\\\]|\\\\.)*\\](?:(?:\\r\\n)?[ \\t])*))*\\>(?:(?:\\r\\n)?[ \\t])*)(?:,\\s*(?:(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\"(?:[^\\\"\\r\\\\]|\\\\.|(?:(?:\\r\\n)?[ \\t]))*\"(?:(?:\\r\\n)?[ \\t])*)(?:\\.(?:(?:\\r\\n)?[ \\t])*(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\"(?:[^\\\"\\r\\\\]|\\\\.|(?:(?:\\r\\n)?[ \\t]))*\"(?:(?:\\r\\n)?[ \\t])*))*@(?:(?:\\r\\n)?[ \\t])*(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\\[([^\\[\\]\\r\\\\]|\\\\.)*\\](?:(?:\\r\\n)?[ \\t])*)(?:\\.(?:(?:\\r\\n)?[ \\t])*(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\\[([^\\[\\]\\r\\\\]|\\\\.)*\\](?:(?:\\r\\n)?[ \\t])*))*|(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\"(?:[^\\\"\\r\\\\]|\\\\.|(?:(?:\\r\\n)?[ \\t]))*\"(?:(?:\\r\\n)?[ \\t])*)*\\<(?:(?:\\r\\n)?[ \\t])*(?:@(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\\[([^\\[\\]\\r\\\\]|\\\\.)*\\](?:(?:\\r\\n)?[ \\t])*)(?:\\.(?:(?:\\r\\n)?[ \\t])*(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\\[([^\\[\\]\\r\\\\]|\\\\.)*\\](?:(?:\\r\\n)?[ \\t])*))*(?:,@(?:(?:\\r\\n)?[ \\t])*(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\\[([^\\[\\]\\r\\\\]|\\\\.)*\\](?:(?:\\r\\n)?[ \\t])*)(?:\\.(?:(?:\\r\\n)?[ \\t])*(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\\[([^\\[\\]\\r\\\\]|\\\\.)*\\](?:(?:\\r\\n)?[ \\t])*))*)*:(?:(?:\\r\\n)?[ \\t])*)?(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\"(?:[^\\\"\\r\\\\]|\\\\.|(?:(?:\\r\\n)?[ \\t]))*\"(?:(?:\\r\\n)?[ \\t])*)(?:\\.(?:(?:\\r\\n)?[ \\t])*(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\"(?:[^\\\"\\r\\\\]|\\\\.|(?:(?:\\r\\n)?[ \\t]))*\"(?:(?:\\r\\n)?[ \\t])*))*@(?:(?:\\r\\n)?[ \\t])*(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\\[([^\\[\\]\\r\\\\]|\\\\.)*\\](?:(?:\\r\\n)?[ \\t])*)(?:\\.(?:(?:\\r\\n)?[ \\t])*(?:[^()<>@,;:\\\\\".\\[\\] \\000-\\031]+(?:(?:(?:\\r\\n)?[ \\t])+|\\Z|(?=[\\[\"()<>@,;:\\\\\".\\[\\]]))|\\[([^\\[\\]\\r\\\\]|\\\\.)*\\](?:(?:\\r\\n)?[ \\t])*))*\\>(?:(?:\\r\\n)?[ \\t])*))*)?;\\s*)"
  )
