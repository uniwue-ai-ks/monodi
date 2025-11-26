package de.olyro.monodi
package db

import cats.MonadError
import cats.data.*
import cats.effect.*
import cats.implicits.*
import de.olyro.monodi.data.*
import de.olyro.monodi.data.notes.{Container, Evolutions, RootContainer}
import de.olyro.monodi.db.MetaInstances.given
import de.olyro.monodi.exprt.{CategoryDescription, CategoryValue, DocumentCategoryFilter, SourceCategoryFilter}
import cats.effect.unsafe.implicits.global
import doobie.*
import doobie.hikari.HikariTransactor
import doobie.implicits.*
import io.circe.*
import io.circe.parser.decode

import zio.interop.catz.*
import zio.{Task, ZIO, ZLayer}

import java.nio.file.{Files, Paths}

object DBRunner:
  given zioRuntime: zio.Runtime[Any] = zio.Runtime.default

  val currentVersion = 22

  val fileCredentials: Option[FileCredentials] =
    try
      val content = Files.readAllBytes(Paths.get("/var/lib/monodi/editor/db.json"))
      val parsed  = decode[Json](new String(content, "UTF-8"))
      parsed match
        case Left(t)  =>
          t.printStackTrace()
          None
        case Right(j) =>
          for
            obj      <- j.asObject
            name     <- obj("name").flatMap(_.asString)
            password <- obj("password").flatMap(_.asString)
            user     <- obj("user").flatMap(_.asString)
          yield FileCredentials(name, password, user)
    catch
      case e: Exception => {
        e.printStackTrace
        None
      }

  private val xa = fileCredentials match
    case None     =>
      Transactor.fromDriverManager[IO]("org.postgresql.Driver", "jdbc:postgresql:monodi", "postgres", "test", None)
    case Some(fc) =>
      Transactor
        .fromDriverManager[IO]("org.postgresql.Driver", "jdbc:postgresql:" + fc.name, fc.user, fc.password, None)

  type DBDoobie = Transactor[Task]

  val live: ZLayer[Any, Throwable, DBDoobie] =
    val (dbName, user, pw) = fileCredentials match
      case None     => ("monodi", "postgres", "test")
      case Some(fc) => (fc.name, fc.user, fc.password)

    ZLayer.scoped(for
      ec         <- ZIO.descriptor.map(_.executor.asExecutionContext)
      transactor <- HikariTransactor
                      .newHikariTransactor[Task](
                        driverClassName = "org.postgresql.Driver",
                        url = s"jdbc:postgresql:$dbName",
                        user = user,
                        pass = pw,
                        connectEC = ec
                      )
                      .toScopedZIO
      _          <- ZIO.succeed(println("build transactor"))
    yield transactor)

  def areValidCredentials(user: User, password: String): ConnectionIO[Boolean] =
    sql"""SELECT email FROM account WHERE email = $user AND password = crypt($password, password)"""
      .query[User]
      .to[List]
      .map({
        case Nil      => false
        case _ :: Nil => true
        case _        => throw new RuntimeException("can never happen")
      })

  def insertSource(s: Source): ConnectionIO[Unit] =
    sql"""INSERT INTO quelle VALUES (
      ${s.id},
      ${s.quellensigle},
      ${s.herkunftsregion},
      ${s.herkunftsort},
      ${s.herkunftsinstitution},
      ${s.ordenstradition},
      ${s.quellentyp},
      ${s.bibliotheksort},
      ${s.bibliothek},
      ${s.bibliothekssignatur},
      ${s.kommentar},
      ${s.datierung},
      ${s.status},
      ${s.jahrhundert},
      ${s.manifest},
      ${s.foliooffset},
      ${s.publish},
      ${s.beschreibung}
      )""".update.run.void

  def updateSource(s: Source): ConnectionIO[Boolean] =
    sql"""UPDATE quelle SET
      quellensigle = ${s.quellensigle},
      herkunftsregion = ${s.herkunftsregion},
      herkunftsort = ${s.herkunftsort},
      herkunftsinstitution = ${s.herkunftsinstitution},
      ordenstradition = ${s.ordenstradition},
      quellentyp = ${s.quellentyp},
      bibliotheksort = ${s.bibliotheksort},
      bibliothek = ${s.bibliothek},
      bibliothekssignatur = ${s.bibliothekssignatur},
      kommentar = ${s.kommentar},
      datierung = ${s.datierung},
      status = ${s.status},
      jahrhundert = ${s.jahrhundert},
      manifest = ${s.manifest},
      foliooffset = ${s.foliooffset},
      publish = ${s.publish},
      beschreibung = ${s.beschreibung}
      WHERE id = ${s.id}""".update.run
      .map(i => if i == 0 then false else true)

  def importSource(s: Source): ConnectionIO[Unit] =
    val exists = sql"""SELECT COUNT(*) FROM quelle WHERE id = ${s.id}""".query[Int].unique.map(_ > 0)

    exists.flatMap(e => if e then updateSource(s).void else insertSource(s).void)

  def removeSource(id: String): ConnectionIO[Unit] =
    sql"""DELETE FROM quelle WHERE id = $id""".update.run.void

  def listSources: ConnectionIO[List[Source]] =
    sql"""SELECT * FROM quelle""".query[Source].to[List]

  def getSource(id: String): ConnectionIO[Option[Source]] =
    sql"""SELECT * FROM quelle WHERE id = $id""".query[Source].option

  def insertDocument(d: CreateDocument): ConnectionIO[Unit] =
    sql"""INSERT INTO dokument VALUES (
      ${d.document.id},
      ${d.document.quelle_id},
      ${d.document.dokumenten_id},
      ${d.document.gattung1},
      ${d.document.gattung2},
      ${d.document.festtag},
      ${d.document.feier},
      ${d.document.textinitium},
      ${d.document.bibliographischerverweis},
      ${d.document.druckausgabe},
      ${d.document.zeilenstart},
      ${d.document.foliostart},
      ${d.document.kommentar},
      ${d.notes},
      ${d.document.editionsstatus},
      ${d.document.additionalData},
      ${d.document.publish}
      )""".update.run.void

  def getAllDocumentIdsWithTheirDokumentIdField: ConnectionIO[List[(String, String)]] =
    sql"""SELECT id, dokumenten_id FROM dokument""".query[(String, String)].to[List]

  def updateDocument(d: CreateDocument): ConnectionIO[Boolean] =
    sql"""UPDATE dokument SET
      quelle_id = ${d.document.quelle_id},
      dokumenten_id = ${d.document.dokumenten_id},
      gattung1 = ${d.document.gattung1},
      gattung2 = ${d.document.gattung2},
      festtag = ${d.document.festtag},
      feier = ${d.document.feier},
      textinitium = ${d.document.textinitium},
      bibliographischerverweis = ${d.document.bibliographischerverweis},
      druckausgabe = ${d.document.druckausgabe},
      zeilenstart = ${d.document.zeilenstart},
      foliostart = ${d.document.foliostart},
      kommentar = ${d.document.kommentar},
      notes = ${d.notes},
      editionsstatus = ${d.document.editionsstatus},
      weitereFelder = ${d.document.additionalData},
      publish = ${d.document.publish}
      WHERE id = ${d.document.id}""".update.run
      .map(i => if i == 0 then false else true)

  def importDocument(d: CreateDocument): ConnectionIO[Unit] =

    val exists = sql"""SELECT COUNT(*) FROM dokument WHERE id = ${d.document.id}""".query[Int].unique.map(_ > 0)

    exists.flatMap(e => if e then updateDocument(d).void else insertDocument(d).void)

  def removeDocument(id: String): ConnectionIO[Unit] =
    sql"""DELETE FROM dokument WHERE id = $id""".update.run.void

  def listDocuments: ConnectionIO[List[Document]] =
    sql"""SELECT
        id,
        quelle_id,
        dokumenten_id,
        gattung1,
        gattung2,
        festtag,
        feier,
        textinitium,
        bibliographischerverweis,
        druckausgabe,
        zeilenstart,
        foliostart,
        kommentar,
        editionsstatus,
        weitereFelder,
        publish
     FROM dokument""".query[Document].to[List]

  def listDocumentIds: ConnectionIO[List[String]] =
    sql"""SELECT id FROM dokument""".query[String].to[List]

  def getDocument(id: String): ConnectionIO[Option[Document]] =
    sql"""SELECT
        id,
        quelle_id,
        dokumenten_id,
        gattung1,
        gattung2,
        festtag,
        feier,
        textinitium,
        bibliographischerverweis,
        druckausgabe,
        zeilenstart,
        foliostart,
        kommentar,
        editionsstatus,
        weitereFelder,
        publish
      FROM dokument WHERE id = $id""".query[Document].option

  val getDistinctDocumentEdititionsstatus: ConnectionIO[List[String]] =
    sql"""SELECT DISTINCT editionsstatus FROM dokument""".query[String].to[List]

  def getDistinctGattung1: ConnectionIO[List[String]] =
    sql"""SELECT DISTINCT gattung1 FROM dokument""".query[String].to[List]

  def getDistinctGattung1ForQuelle(id: String): ConnectionIO[List[String]] =
    sql"""SELECT DISTINCT gattung1 FROM dokument WHERE quelle_id = $id """.query[String].to[List]

  def listAllDistinct_UNSAFE_CAN_LEAD_TO_SQL_INJECTION(
      tableName: String,
      columnName: String
  ): ConnectionIO[List[String]] =
    (fr"SELECT DISTINCT " ++ Fragment.const(columnName) ++ fr" FROM " ++ Fragment.const(tableName))
      .query[String]
      .to[List]

  def listAllDistinctWeitereFelder_UNSAFE_CAN_LEAD_TO_SQL_INJECTION(
      jsonName: String
  ): ConnectionIO[List[String]] =
    (fr"SELECT DISTINCT ((weitereFelder->$jsonName)::TEXT) FROM dokument")
      .query[Option[String]]
      .to[List]
      .map(_.flatten)
      .map(_.map(_.stripPrefix("\"").stripSuffix("\"")))

  def listDistinct_UNSAFE_CAN_LEAD_TO_SQL_INJECTION(c: CategoryDescription): ConnectionIO[List[CategoryValue]] =
    val toSelect =
      val value =
        if c.isNativeColumn then Fragment.const("m." + c.columnName) else fr"((m.weitereFelder->${c.columnName})::TEXT)"
      c.guard match
        case None        => value
        case Some(guard) => value ++ Fragment.const(s", ${guard.column}")

    val selectFrom = c.filter match
      case SourceCategoryFilter(_)   => Fragment.const("quelle as m")
      case DocumentCategoryFilter(_) => Fragment.const("dokument as m, quelle as q")

    val condition = c.filter match
      case SourceCategoryFilter(Nil)         => Fragment.const("")
      case DocumentCategoryFilter(Nil)       => Fragment.const("")
      case SourceCategoryFilter(whiteList)   =>
        Fragment.const(
          s"""WHERE TRIM(LOWER(m.publish)) IN (${whiteList.map(entry => s"TRIM(LOWER('$entry'))").mkString(", ")})"""
        )
      case DocumentCategoryFilter(whiteList) =>
        Fragment.const(
          s"""WHERE
            TRIM(LOWER(m.publish)) IN (${whiteList.map(entry => s"TRIM(LOWER('$entry'))").mkString(", ")}) AND 
            TRIM(LOWER(q.publish)) IN (${whiteList.map(entry => s"TRIM(LOWER('$entry'))").mkString(", ")}) AND
            m.quelle_id = q.id
          """
        )

    val queryFr = (fr"SELECT DISTINCT " ++ toSelect ++ fr"FROM" ++ selectFrom ++ condition)

    c.guard match
      case None         =>
        queryFr
          .query[Option[String]]
          .to[List]
          .map(_.flatten)
          .map(_.map(_.stripPrefix("\"").stripSuffix("\"")))
          .map(_.map(CategoryValue(_, None)))
      case Some(cGuard) =>
        queryFr
          .query[(Option[String], Option[String])]
          .to[List]
          .map(_.flatMap({
            case (None, _)            => None
            case (Some(value), guard) =>
              Some(
                CategoryValue(
                  value.stripPrefix("\"").stripSuffix("\""),
                  guard.map(v => CategoryValue.Guard(cGuard.ref, v.stripPrefix("\"").stripSuffix("\"")))
                )
              )
          }))

  def getDocumentNotes(id: String): ConnectionIO[Option[Json]] =
    sql"""SELECT notes FROM dokument WHERE id = $id""".query[Json].option

  def getDocumentNotesString(id: String): ConnectionIO[Option[String]] =
    sql"""SELECT notes FROM dokument WHERE id = $id""".query[String].option

  def getDocumentNotesDecoded(id: String): ConnectionIO[Option[(RootContainer, Evolutions.EvolutionResult)]] =
    sql"""SELECT notes FROM dokument WHERE id = $id"""
      .query[Json]
      .option
      .flatMap(
        _.traverse(j => {
          Container.parse(j.printWith(jsonPrinter)) match {
            case Right(r) =>
              r.pure[ConnectionIO]
            case Left(t)  =>
              MonadError[ConnectionIO, Throwable]
                .raiseError[(RootContainer, Evolutions.EvolutionResult)](new RuntimeException(t))
          }
        })
      )

  def saveDocumentNotes(sn: SaveNotes): ConnectionIO[Boolean] =
    sql"""UPDATE dokument SET notes = ${sn.notes} WHERE id = ${sn.id}""".update.run.map(_ > 0)

  def removeUser(u: User): ConnectionIO[Unit] =
    sql"""DELETE FROM account WHERE account.email = $u""".update.run.void

  def createUserOrUpdatePassword(u: User, password: String): ConnectionIO[Boolean] =
    sql"""
      INSERT INTO account
      VALUES ($u, crypt($password, gen_salt('bf')))
      ON CONFLICT (email) DO UPDATE
      SET password = crypt($password, gen_salt('bf'))
    """.update.run.map(i => if i == 0 then false else true)

  def listUsers: ConnectionIO[List[User]] =
    sql"""SELECT email FROM account""".query[User].to[List]

  def listRoles(u: User): ConnectionIO[List[Role]] =
    sql"""SELECT role FROM role WHERE role.account_email = $u""".query[Role].to[List]

  def test: ConnectionIO[List[Int]] =
    sql"select 1".query[Int].to[List]

  def setMafftResult(doc1: String, doc2: String, score: Double): ConnectionIO[Unit] =
    sql"""INSERT INTO similarity_score(document_1, document_2, similarity) VALUES ($doc1, $doc2, $score) ON CONFLICT (document_1, document_2) DO UPDATE SET similarity = $score""".update.run.void

  def run[A](c: ConnectionIO[A]): IO[A] = c.transact(xa)

  def runZ[A](c: ConnectionIO[A]): ZIO[DBDoobie, Throwable, A] =
    ZIO.serviceWithZIO[DBDoobie](transactor => c.transact(transactor))

  def connectionWorks: IO[Boolean] =
    test
      .transact(xa)
      .attempt
      .map({
        case Left(_)  => false
        case Right(_) => true
      })

  def getDatabaseVersion: IO[Option[Int]] =
    sql"""SELECT version FROM database_version"""
      .query[Int]
      .unique
      .transact(xa)
      .attempt
      .map(_.toOption)

  def evolveAllZ: Task[Unit] = evolveAll.to[Task]

  def evolveAll: IO[Unit] =
    for
      version <- getDatabaseVersion
      range    = (version.getOrElse(0) until currentVersion).toList
      result  <- runAll(range)
      _       <- result match
                   case None         => ().pure[IO]
                   case Some((i, e)) => IO { throw new RuntimeException(s"Couldn't apply evolution number ${i.toString}", e) }
    yield ()

  def runAll(l: List[Int]): IO[Option[(Int, Throwable)]] =
    l.map(i =>
      evolve(i).attempt.map(
        _.leftMap(e => (i, e))
      ),
    ).map(io => EitherT(io))
      .sequence
      .value
      .map({
        case Left(e)  => Some(e)
        case Right(_) => None
      })

  private def evolve(n: Int): IO[Unit] =
    Util.readResource(getClass, evolutionName(n)) >>=
      (_.split("--.*")
        .filter(s => !s.trim().isEmpty)
        .toList
        .traverse(statement => Fragment.const(statement).update.run)
        .flatMap(_ => setDatabaseVersion(n + 1))
        .transact(xa)
        .void)

  private def setDatabaseVersion(n: Int): ConnectionIO[Unit] =
    sql"""UPDATE database_version SET version = $n""".update.run.void

  private def evolutionName(n: Int): String =
    "ev" + n.toString + ".sql"

  val jsonPrinter = Printer.spaces2SortKeys.copy(dropNullValues = true)

final case class FileCredentials(name: String, password: String, user: String)
