package de.olyro.monodi
package exprt

import de.olyro.monodi.Util.Fence
import de.olyro.monodi.data.Document
import de.olyro.monodi.data.notes.{
  FormteilContainer,
  MiscContainer,
  ParatextContainer,
  RootContainer,
  Syllable,
  ZeileContainer
}
import de.olyro.monodi.db.DBRunner
import de.olyro.monodi.db.DBRunner.DBDoobie
import de.olyro.monodi.exprt.Message.FromDoc
import zio.*
import zio.ZIO.attemptBlocking

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.xml.{Elem, MetaData, NamespaceBinding, Node, Null, PrettyPrinter, Text, TopScope, UnprefixedAttribute}

object MeiExport:
  private val meiNs: String      = "http://www.music-encoding.org/ns/mei"
  private val meiScope           = NamespaceBinding(null, meiNs, TopScope)
  private val printer            = new PrettyPrinter(120, 4)
  private val xmlPrelude: String =
    """<?xml version="1.0" encoding="UTF-8"?>
      |<?xml-model href="https://music-encoding.org/schema/dev/mei-Neumes.rng" type="application/xml" schematypens="http://relaxng.org/ns/structure/1.0"?>
      |<?xml-model href="https://music-encoding.org/schema/dev/mei-Neumes.rng" type="application/xml" schematypens="http://purl.oclc.org/dsdl/schematron"?>
      |""".stripMargin

  // A document's MEI export lives at `<meiPath>/<folderFor(doc)>/<filenameFor(doc)>`.
  // The folder is the document UUID (guaranteed unique). The filename is built
  // from the human-readable dokumenten_id so it shows up legibly both in
  // directory listings and in the link text on the document page. These two
  // helpers are the single source of truth shared by the file writer below
  // and by the link builder in DocCreator.
  def folderFor(doc: Document): String = doc.id

  def filenameFor(doc: Document): String = sanitizeForFilename(doc.dokumenten_id) + ".xml"

  // Less harsh than alnum-only: keeps spaces, dashes, dots and other readable
  // characters; replaces only what would actually break a filesystem path
  // (separators, control bytes, Windows-reserved characters).
  private def sanitizeForFilename(s: String): String =
    s.replaceAll("[\\\\/:*?\"<>|\\u0000-\\u001F\\u007F]", "_")

  def run(
      config: ExportConfig
  ): ZIO[DBDoobie & Fence, Nothing, List[Message]] =
    config.meiPath match
      case None       => ZIO.succeed(Nil)
      case Some(path) =>
        Fence.measure("mei-export"):
          for
            _    <- zio.Console.printLine("Exporting MEI files...").orDie
            _    <- attemptBlocking(Files.createDirectories(path)).orDie
            docs <- DBRunner.runZ(DBRunner.listDocuments).orDie
            _    <- zio.Console.printLine(s"Writing ${docs.length} MEI documents...").orDie
            msgs <- ZIO.foldLeft(docs)(List.empty[Message]): (acc, doc) =>
                      processDocument(doc, path).map(_ ::: acc)
          yield msgs.reverse

  private def processDocument(
      doc: Document,
      out: Path
  ): ZIO[DBDoobie, Nothing, List[Message]] =
    for
      _      <- zio.Console.printLine(s"Processing document ${doc.id}...").orDie
      rc     <- DBRunner.runZ(DBRunner.getDocumentNotesDecoded(doc.id)).map(_.map(_._1)).option.map(_.flatten)
      content = xmlPrelude + printer.format(buildMei(doc, rc))
      folder  = out.resolve(folderFor(doc))
      _      <- attemptBlocking(Files.createDirectories(folder)).orDie
      _      <- attemptBlocking(
                  Files.writeString(folder.resolve(filenameFor(doc)), content, StandardCharsets.UTF_8)
                ).orDie
    yield
      if rc.isEmpty then List(Message(Message.Warning, FromDoc(doc), "MEI: no notes; wrote empty skeleton"))
      else Nil

  private def elem(label: String, attrs: MetaData, children: Node*): Elem =
    Elem(null, label, attrs, meiScope, minimizeEmpty = true, children*)

  private def elem(label: String, children: Node*): Elem =
    elem(label, Null, children*)

  private def attrs(pairs: (String, String)*): MetaData =
    pairs.foldRight[MetaData](Null)((p, acc) => new UnprefixedAttribute(p._1, p._2, acc))

  private def buildMei(doc: Document, rc: Option[RootContainer]): Elem =
    val staves: Seq[Elem] = rc.toList.flatMap(collectZeilen).map(zeileToStaff)
    elem(
      "mei",
      elem(
        "meiHead",
        elem(
          "fileDesc",
          elem("titleStmt", elem("title", Text(doc.textinitium))),
          elem("pubStmt")
        )
      ),
      elem(
        "music",
        elem(
          "body",
          elem(
            "mdiv",
            elem(
              "score",
              elem("scoreDef"),
              elem("section", staves*)
            )
          )
        )
      )
    )

  private def collectZeilen(rc: RootContainer): List[ZeileContainer] =
    rc.children.flatMap:
      case fc: FormteilContainer => zeilenFromFormteil(fc)
      case mc: MiscContainer     => zeilenFromMisc(mc)

  private def zeilenFromFormteil(fc: FormteilContainer): List[ZeileContainer] =
    fc.children.flatMap:
      case zc: ZeileContainer     => List(zc)
      case fc2: FormteilContainer => zeilenFromFormteil(fc2)
      case _: ParatextContainer   => Nil

  private def zeilenFromMisc(mc: MiscContainer): List[ZeileContainer] =
    mc.children.flatMap:
      case zc: ZeileContainer   => List(zc)
      case _: ParatextContainer => Nil

  private def zeileToStaff(z: ZeileContainer): Elem =
    val syls = z.children.collect { case s: Syllable => syllableToXml(s) }
    elem("staff", elem("layer", syls*))

  private def syllableToXml(s: Syllable): Elem =
    val neumes = s.notes.spaced.map: spc =>
      val notes = spc.nonSpaced.flatMap(_.grouped)
      val ncs   = notes.zipWithIndex.map: (n, idx) =>
        val pname = n.base.toString.toLowerCase
        val oct   = n.octave.toString
        val a     =
          if idx == 0 then attrs("pname" -> pname, "oct" -> oct)
          else attrs("pname"             -> pname, "oct" -> oct, "con" -> "g")
        elem("nc", a)
      elem("neume", ncs*)
    elem("syllable", (elem("syl", Text(s.text)) +: neumes)*)
