package de.olyro.monodi.exprt

import de.olyro.monodi.exprt.Svg.LinePartSettings
import de.olyro.monodi.data.notes.*
import de.olyro.monodi.db.DBRunner
import zio.ZIO
import zio.stream.ZStream

import java.nio.file.{Files, Paths}
import scala.annotation.tailrec
import scala.annotation.nowarn

object ExperimentalSynopsis extends zio.ZIOAppDefault:
  // val lps   = LinePartSettings(FontKind.Normal, BaseNote.C.halfs(4) - 2, 0, 0, (false, false))
  // val empty = Syllable("", " ", Spaced(Nil), SyllableType.WithoutNotes)
  val svg = Svg(new CanvasConfig(width = Double.PositiveInfinity, height = Double.PositiveInfinity * Math.sqrt(2)))

//  def drawCompare(a: List[LinePart], b: List[LinePart]) = {
//    val parts = a.zipAll(b, empty, empty) map { case (lpa, lpb) =>
//      val aWidth = svg.drawLinePart(lps)(lpa).get.width
//      val bWidth = svg.drawLinePart(lps)(lpb).get.width
//      if (aWidth > bWidth) {
//        BoundingBox.concatY(
//          List(svg.drawLinePart(lps)(lpa), svg.drawLinePart(lps.copy(rightPadding = aWidth - bWidth))(lpb)).flatten,
//          svg.config.linePadding,
//          None,
//        )
//
//      } else {
//        BoundingBox.concatY(
//          List(svg.drawLinePart(lps.copy(rightPadding = bWidth - aWidth))(lpa), svg.drawLinePart(lps)(lpb)).flatten,
//          svg.config.linePadding,
//          None,
//        )
//      }
//    }
//    BoundingBox.concatX(parts, 0, Some("line-block"))
//  }

  def drawCompare(parts: List[List[LinePart]]) =
    val minNoteHalfs =
      ((svg.lineHalfs.head - 2) :: Container.getAllNotes(ZeileContainer("", parts.flatten)).map(_.halfs - 2)).min

    def split(l: List[LinePart]): (List[LinePart], List[LinePart]) =
      def expand(l: List[LinePart]): (List[LinePart], List[LinePart]) = l match
        case Nil                => (Nil, Nil)
        case (_: Syllable) :: _ => (Nil, l)
        case h :: t             =>
          val (hh, tt) = expand(t)
          (h :: hh, tt)
      l match
        case (s: Syllable) :: t =>
          val (hh, tt) = expand(t)
          (s :: hh, tt)
        case _                  => (Nil, Nil)

    @tailrec
    def go(acc: List[List[BoundingBox]], rem: List[List[LinePart]]): List[List[BoundingBox]] =
      if !rem.exists(_.nonEmpty) then acc.map(_.reverse)
      else
        val (column, tail) = rem.map(split).unzip
        val width          = column.map(l => drawLineParts(l, FontKind.Normal, minNoteHalfs)(Nil).width).maxOption.getOrElse(0.0)

        go(
          acc.zip(column).map { case (t, h) =>
            val box = drawLineParts(h, FontKind.Normal, minNoteHalfs)(Nil)
            box.padRight(width - box.width) :: t
          },
          tail
        )

    BoundingBox.concatY(
      go(List.fill(parts.size)(Nil), parts).map(l =>
        svg.addStaffLines(BoundingBox.concatX(l, svg.config.linePartPadding, Some("line-block"))),
      ),
      svg.config.linePadding,
      None
    )

  def drawLineParts(lineParts: List[LinePart], font: FontKind, minNote: Int)(comments: List[Comment]): BoundingBox =
    import svg.config.{lineContinuationIndent, linePadding, linePartPadding}

    def mkLPS(pl: Double, pr: Double, lp: LinePart) =
      LinePartSettings(font, minNote, pl, pr, ViewModel.getCommentInfo(lp, comments))

    @nowarn("msg=match may not be exhaustive.")
    val settings = (lineParts match {
      case Nil                  => Nil
      case lp :: Nil            => List(mkLPS(0.0, 0.0, lp))
      case first +: mid :+ last =>
        mkLPS(0.0, linePartPadding / 2.0, first) +: mid.map(
          mkLPS(linePartPadding / 2.0, linePartPadding / 2.0, _)
        ) :+ mkLPS(linePartPadding / 2.0, 0.0, last)
    })

    val parts = (lineParts zip settings) flatMap { case (lp, settings) => svg.drawLinePart(settings)(lp) }

    val lines = BoundingBox.wrapX(parts, xPadding = 0, xMax = svg.lineWidth, xIndent = lineContinuationIndent)
    BoundingBox.concatY(lines, linePadding, Some("line-block"))

  override def run: ZIO[Any, Nothing, Int] = (for
    notes <- ZStream(
               "97417cfa-5d85-40bc-8eb3-ed7db4043872",
               "4b762a9d-5068-480c-ba8c-35e575dcd238"
             ).mapZIO(id => DBRunner.runZ(DBRunner.getDocumentNotesDecoded(id)).someOrFailException.orDie)
               .map(_._1)
               .runCollect
               .map(_.toList)
    lps    = notes.map(Container.toLineParts)
    _     <- ZIO.attemptBlocking(Files.writeString(Paths.get("/tmp/synopsis.svg"), svg.makeSVG(drawCompare(lps), ""))).orDie
    grids  = renderer.Renderer.makeGrids(notes)
    _     <- ZStream
               .fromIterable(grids)
               .zipWithIndex
               .flatMap { case (grid, i) =>
                 ZStream
                   .fromIterable(renderer.Svg.draw(grid))
                   .zipWithIndex
                   .mapZIO {
                     case (Some(str), l) =>
                       ZIO.attemptBlocking(Files.writeString(Paths.get(s"/tmp/synopsis-new-$i-${l + 1}.svg"), str))
                     case _              => ZIO.unit
                   }
               }
               .runDrain
               .orDie
  yield 0).provideSomeLayer(DBRunner.live.orDie)
