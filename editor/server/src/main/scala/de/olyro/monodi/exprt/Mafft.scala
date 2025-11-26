package de.olyro.monodi
package exprt

import cats.implicits.*
import de.olyro.monodi.Util.Fence
import de.olyro.monodi.data.notes.*
import de.olyro.monodi.data.{Document, Source}
import de.olyro.monodi.db.DBRunner
import de.olyro.monodi.db.DBRunner.DBDoobie

import zio.stream.*

import java.nio.charset.StandardCharsets
import java.nio.file.*
import sys.process.*
import io.circe.Json
import zio.*
import zio.Console.printLine
import java.io.IOException

object Mafft extends ZIOAppDefault:
  lazy val availableProcessors = java.lang.Runtime.getRuntime.availableProcessors()

  sealed trait ExportFilter:
    def sourceShouldBeExported(src: String): Boolean
    def documentShouldBeExported(doc: Document): Boolean
    def notesShouldBeExportedFor(doc: Document): Boolean

  type DocStoreData = (Document, Option[RootContainer], Evolutions.EvolutionResult, Source)

  override def run: ZIO[ZIOAppArgs, IOException, Int] =
    def continueFromIds(arg1: Option[String], arg2: Option[String]) = for
      a1 <- arg1
      a2 <- arg2
    yield (a1, a2)

    val loadDocs =
      val docList           = Fence.measure("docs-load")(DBRunner.runZ(DBRunner.listDocuments))
      def notes(id: String) = Fence.measure("docs-load")(DBRunner.runZ(DBRunner.getDocumentNotes(id)))
      def src(id: String)   = Fence.measure("docs-load")(DBRunner.runZ(DBRunner.getSource(id)).someOrFailException)

      ZStream.fromIterableZIO(docList).mapZIO(doc => ZIO.succeed(doc) <*> notes(doc.id) <*> src(doc.quelle_id))

    def getDocs() = for
      docs <- loadDocs.map(d => (d._1, d._2)).runCollect.orDie
    yield docs.toList

    def parseNotes(
        doc: Document,
        rawNotes: Option[Json],
    ): ZIO[
      Any,
      String,
      (RootContainer, Evolutions.EvolutionResult),
    ] =
      ZIO
        .fromEither(rawNotes.toRight("could not load notes"))
        .map(j => Container.parse(j.printWith(DBRunner.jsonPrinter)))
        .absolve
        .mapError(e => doc.dokumenten_id + " : " + e)

    def getDirection(data: (Document, Option[Json])): ZIO[
      Any,
      String,
      String,
    ] = for
      (rc, evos) <- parseNotes(data._1, data._2)
      warnings    = evos.messages.map(Message(Message.Warning, Message.FromDoc(data._1), _))
      notes       = Container.getAllNotes(rc)
      directions  = NoteStringifier.directions(notes)
    yield (directions)

    def runMafft(uuid1: String, dir1: String, uuid2: String, dir2: String): ZIO[Any, Nothing, String] =
      for
        file <- ZIO.succeed("mktemp".!!).map(s => Paths.get(s.trim))
        _    <- ZIO
                  .attemptBlocking(
                    Files.write(
                      file,
                      ("> " + uuid1 + "\n" + dir1 + "\n" + "> " + uuid2 + "\n" + dir2).getBytes(StandardCharsets.UTF_8),
                    ),
                  )
                  .orDie
        res  <- ZIO.succeed(s"mafft --quiet --anysymbol $file".!!)
        _    <- ZIO.succeed(s"rm $file".!!)
      yield (res)

    def getDirectionalData(data: (Document, Option[Json])): ZIO[Any, String, String] = getDirection(data)

    def getDataToWrite(data1: (Document, Option[Json])): ZIO[Any, String, (String, String)] = for
      dir <- getDirectionalData(data1)
      id   = data1._1.id
    yield (id, dir)

    def writeMafftScore(
        data1: (Document, Option[Json]),
        data2: (Document, Option[Json]),
    ): ZIO[DBDoobie, String, Unit] =
      for
        d1      <- getDataToWrite(data1)
        d2      <- getDataToWrite(data2)
        maftRes <- runMafft(d1._1, d1._2, d2._1, d2._2)
        score    = calculateScore(maftRes)
        _       <- DBRunner.runZ(DBRunner.setMafftResult(data1._1.id, data2._1.id, score)).orDie

      yield ()

    def ListToZStream(documents: List[(Document, Option[Json])]): ZStream[Any, Nothing, (Document, Option[Json])] =
      ZStream.fromIterable(documents)

    def reducedCrossProduct(
        documents: List[(Document, Option[Json])],
    ): ZStream[Any, Nothing, ((Document, Option[Json]), (Document, Option[Json]))] =
      ListToZStream(documents).crossWith(ListToZStream(documents))(Tuple2.apply)

    def calculateScore(mafftResult: String): Double =
      val withoutHead = mafftResult.linesIterator.toList.drop(1)
      val nextIdIndex = withoutHead.indexWhere(_.startsWith(">"))
      val line1       = withoutHead.take(nextIdIndex).mkString("")
      val line2       = withoutHead.drop(nextIdIndex + 1).mkString("")

      val correct = line1.zip(line2).count(t => t._1 == t._2)
      if line1.length() == 0 then
        0
      else
        correct.toDouble / line1.length

    (for
      args   <- ZIOAppArgs.getArgs
      arg1    = args.lift(0)
      arg2    = args.lift(1)
      _      <- db.DBRunner.evolveAllZ.orDie
      docs   <- getDocs()
      product =
        reducedCrossProduct(docs.sortBy(d => d._1.id))
          .filter(s => s._1._1.id < s._2._1.id)
          .dropWhile(s => continueFromIds(arg1, arg2).fold(false)(cf => !(s._1._1.id == cf._1 && s._2._1.id == cf._2)))
          .zipWithIndex

      _ <- product.foreach(t =>
             for
               _ <- writeMafftScore(t._1._1, t._1._2).catchAll(e => printLine(e))
               _ <- printLine("current Index: " + t._2 + " id1: " + t._1._1._1.id + " id2: " + t._1._2._1.id)
                      .when(t._2 % 1000 == 0)
             yield (),
           )

    yield (0)).provideSome[ZIOAppArgs](Fence.live ++ DBRunner.live.orDie)

    /*
    > ID
    --AAB-AABBB---AAA
    > ID
    BBAAB---BBB-ABAAA

    one empty
    > ID
    --AAB-AABBB---AAA
    > ID
    -----------------

    Both empty
    > ID
    > ID
     */


