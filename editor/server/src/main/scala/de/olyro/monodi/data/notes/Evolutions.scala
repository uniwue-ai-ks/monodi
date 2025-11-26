package de.olyro.monodi
package data
package notes

import cats.*
import cats.data.*
import cats.implicits.*
import de.olyro.monodi.data.notes.DocumentType.{Level1, Level2, Level3}
import io.circe.Json.*
import io.circe.*
import io.circe.parser.*
import io.circe.syntax.*

import java.util as ju
import scala.collection.immutable.Nil

object Evolutions:
  def recurse(in: Json, f: Folder[Json]): Json =
    in.fold(
      f.onNull,
      f.onBoolean,
      f.onNumber,
      f.onString,
      arr => f.onArray(arr.map(recurse(_, f))),
      obj => f.onObject(JsonObject.fromIterable(obj.toList.map({ case (k, v) => (k, recurse(v, f)) })))
    )

  val certainToEmendationFolder = new Folder[Json]:
    def onNull: Json                       = Json.Null
    def onBoolean(value: Boolean): Json    = Json.fromBoolean(value)
    def onNumber(value: JsonNumber): Json  = Json.fromJsonNumber(value)
    def onString(value: String): Json      = Json.fromString(value)
    def onArray(value: Vector[Json]): Json = Json.fromValues(value)
    def onObject(value: JsonObject): Json  =
      val asJson = Json.fromJsonObject(value)
      if value("kind") == Some(Json.fromString("ParatextContainer")) && value("comment").nonEmpty then
        asJson.hcursor
          .downField("comment")
          .withFocus(j => {
            Json.fromJsonObject(JsonObject.fromIterable(j.asObject.get.toList.map({ case (k, v) =>
              if k == "certain" then ("emendation", v)
              else (k, v)
            })))
          })
          .top
          .get
      else asJson

  val removeEndsWord = new Folder[Json]:
    def onNull: Json                       = Json.Null
    def onBoolean(value: Boolean): Json    = Json.fromBoolean(value)
    def onNumber(value: JsonNumber): Json  = Json.fromJsonNumber(value)
    def onString(value: String): Json      = Json.fromString(value)
    def onArray(value: Vector[Json]): Json = Json.fromValues(value)
    def onObject(value: JsonObject): Json  =
      val asJson = Json.fromJsonObject(value)
      if value("kind") == Some(Json.fromString("Syllable")) && value("endsWord").nonEmpty then
        asJson.hcursor
          .downField("endsWord")
          .delete
          .top
          .get
      else asJson

  val removeEndId = new Folder[Json]:
    def onNull: Json                       = Json.Null
    def onBoolean(value: Boolean): Json    = Json.fromBoolean(value)
    def onNumber(value: JsonNumber): Json  = Json.fromJsonNumber(value)
    def onString(value: String): Json      = Json.fromString(value)
    def onArray(value: Vector[Json]): Json = Json.fromValues(value)
    def onObject(value: JsonObject): Json  =
      val asJson = Json.fromJsonObject(value)
      if value("endid").nonEmpty then
        asJson.hcursor
          .downField("endid")
          .delete
          .downField("startid")
          .delete
          .top
          .get
      else asJson

  val removeDataArrayInZeile = new Folder[Json]:
    def onNull: Json                       = Json.Null
    def onBoolean(value: Boolean): Json    = Json.fromBoolean(value)
    def onNumber(value: JsonNumber): Json  = Json.fromJsonNumber(value)
    def onString(value: String): Json      = Json.fromString(value)
    def onArray(value: Vector[Json]): Json = Json.fromValues(value)
    def onObject(value: JsonObject): Json  =
      val asJson = Json.fromJsonObject(value)
      if value("kind") == Some(Json.fromString("ZeileContainer")) && value("data").nonEmpty then
        asJson.hcursor
          .downField("data")
          .delete
          .top
          .get
      else asJson

  val removeDataObjectFromMiscContainer = new Folder[Json]:
    def onNull: Json                       = Json.Null
    def onBoolean(value: Boolean): Json    = Json.fromBoolean(value)
    def onNumber(value: JsonNumber): Json  = Json.fromJsonNumber(value)
    def onString(value: String): Json      = Json.fromString(value)
    def onArray(value: Vector[Json]): Json = Json.fromValues(value)
    def onObject(value: JsonObject): Json  =
      val asJson = Json.fromJsonObject(value)
      if value("kind") == Some(Json.fromString("MiscContainer")) && value("data").nonEmpty then
        asJson.hcursor
          .downField("data")
          .delete
          .top
          .get
      else asJson

  val addWithoutNotesAttributeToLineParts = new Folder[Json]:
    override def onNull: Json                       = Json.Null
    override def onBoolean(value: Boolean): Json    = Json.fromBoolean(value)
    override def onNumber(value: JsonNumber): Json  = Json.fromJsonNumber(value)
    override def onString(value: String): Json      = Json.fromString(value)
    override def onArray(value: Vector[Json]): Json = Json.fromValues(value)
    override def onObject(value: JsonObject): Json  = value("kind")
      .filter(List("LineChange", "FolioChange").map(Json.fromString).contains)
      .as(value.add("hasNotes", Json.True))
      .getOrElse(value)
      .asJson

  type EvolutionStep = Either[String, (RootContainer, EvolutionResult)]

  def fixDocumentLevel(in: Json): EvolutionStep =
    final case class FloatState[C >: FormteilContainer](closed: Vector[C], open: Vector[FormteilChildren]):
      def close: FloatState[C] =
        if open.nonEmpty then
          FloatState(
            closed :+ FormteilContainer(ju.UUID.randomUUID().toString, open.toList, Nil),
            Vector.empty
          )
        else this

      def addToClosed(fc: C): FloatState[C]              = copy(closed = closed :+ fc)
      def addToOpen(fc: FormteilChildren): FloatState[C] = copy(open = open :+ fc)

    def probe(in: FormteilChildren): Probe =
      in match
        case f: FormteilContainer => f.children.foldLeft(Empty: Probe)((p, c) => p.add(probe(c))).inc
        case _: ZeileContainer    => Consistent(0)
        case _: ParatextContainer => Empty

    def float[C >: FormteilContainer <: FormteilChildren](
        targetLevel: Int,
        currentLevel: Int,
        children: List[C]
    ): List[C] =
      if targetLevel <= currentLevel then children
      else
        float(
          targetLevel,
          currentLevel + 1,
          children
            .foldLeft(FloatState[C](Vector.empty, Vector.empty))((fs, child) => {
              child match {
                case _: ParatextContainer =>
                  if fs.open.nonEmpty then fs.addToOpen(child)
                  else fs.addToClosed(child)
                case _: ZeileContainer    =>
                  if currentLevel == 0 then fs.addToOpen(child)
                  else fs.close.addToClosed(child)
                case f: FormteilContainer =>
                  probe(f) match {
                    case Empty =>
                      if fs.open.nonEmpty then fs.addToOpen(f)
                      else fs.addToClosed(f)

                    case Consistent(maxDepth) =>
                      if maxDepth == currentLevel then fs.addToOpen(f)
                      else fs.close.addToClosed(f)

                    case Inconsistent(maxDepth) =>
                      val consistent = f.copy(children = float(targetLevel - 1, 0, f.children))
                      if maxDepth == currentLevel then fs.addToOpen(consistent)
                      else fs.close.addToClosed(consistent)
                  }
              }
            })
            .close
            .closed
            .toList
        )

    Decoder[Container].decodeJson(in) match
      case Left(t)                 => Left(t.getMessage)
      case Right(r: RootContainer) =>
        r.children.traverse({
          case fc: FormteilContainer => Some(fc)
          case _: MiscContainer      => None
        }) match
          case None                        =>
            Right(
              (r, EvolutionResult(false, List("This still contains a misc container")))
            ) // When we have a misc container, we cant fix the level, since it is currently being rearranged
          case Some(rootFormteilContainer) =>
            if rootFormteilContainer.isEmpty then Right((r, EvolutionResult(true, Nil)))
            else
              probe(FormteilContainer(null, rootFormteilContainer, Nil)) match
                case Empty                    => Right((r, EvolutionResult(true, Nil)))
                case Consistent(realMaxDepth) =>
                  ((realMaxDepth - 1) match {
                    case 1 => Right(r.copy(documentType = Level1))
                    case 2 => Right(r.copy(documentType = Level2))
                    case 3 => Right(r.copy(documentType = Level3))
                    case l => Left(s"(a)invalid max depth $l")
                  }).map(newR =>
                    if newR.documentType == r.documentType then ((r, EvolutionResult(true, Nil)))
                    else
                      ((newR, EvolutionResult(true, List("Changed document level, but was consistent")))),
                  )

                case Inconsistent(realMaxDepth) =>
                  (Math.max(realMaxDepth - 1, r.documentType.maxDepth) match {
                    case 1 => Right(Level1)
                    case 2 => Right(Level2)
                    case 3 => Right(Level3)
                    case l => Left(s"(b)invalid max depth $l")
                  }).map(newLevel => {
                    (
                      r.copy(
                        documentType = newLevel,
                        children = float(newLevel.maxDepth, 1, rootFormteilContainer)
                      ),
                      EvolutionResult(true, List("tried to automatically fix inconsistent document levels"))
                    )
                  })
      case Right(c)                => Left(s"Wrong root container for ${c.toString}")

  def makeIdsUnique(rc: RootContainer): EvolutionStep =
    val uuidsUsedInComments = rc.comments.toSet[Comment].flatMap(c => Set(c.startUUID, c.endUUID))
    final case class State(seen: Set[String], messages: List[String])
    type F[A] = StateT[Eval, State, A]

    def markAsUsed(uuid: String): F[Unit]     = StateT.modify[Eval, State](s => s.copy(seen = s.seen + uuid))
    def alreadyUsed(uuid: String): F[Boolean] = StateT.inspect[Eval, State, Boolean](s => s.seen.contains(uuid))
    def addMessage(uuid: String): F[Unit]     =
      StateT.modify[Eval, State](s => s.copy(messages = s.messages :+ s"uuid duplication for uuid $uuid"))
    def keep[A](a: A): F[Option[A]]           = (Some(a): Option[A]).pure[F]

    def update[A](a: A, get: A => String, set: (A, String) => A): F[Option[A]] =
      val uuid = get(a)
      alreadyUsed(uuid).flatMap({
        case false => keep(a)
        case true  => {
          val newUuid = ju.UUID.randomUUID().toString

          if uuidsUsedInComments.contains(uuid) then
            addMessage(uuid) *> keep(set(a, newUuid))
          else
            keep(set(a, newUuid))
        }
      }) <* markAsUsed(uuid)

    val modifier = new de.olyro.monodi.data.notes.Modifier.Modifier[F]:
      def prePara(c: ParatextContainer): F[Option[ParatextContainer]] = update(c, _.uuid, (c, id) => c.copy(uuid = id))
      def preZeile(c: ZeileContainer): F[Option[ZeileContainer]]      = update(c, _.uuid, (c, id) => c.copy(uuid = id))
      def preForm(c: FormteilContainer): F[Option[FormteilContainer]] = update(c, _.uuid, (c, id) => c.copy(uuid = id))
      def preMisc(c: MiscContainer): F[Option[MiscContainer]]         = update(c, _.uuid, (c, id) => c.copy(uuid = id))
      def preRoot(c: RootContainer): F[Option[RootContainer]]         = update(c, _.uuid, (c, id) => c.copy(uuid = id))
      def preLinePart(c: LinePart): F[Option[LinePart]]               = update(c, _.uuid, (c, id) => c.setUuid(id))
      def preNote(c: Note): F[Option[Note]]                           = update(c, _.uuid, (c, id) => c.copy(uuid = id))

      def postNote(c: Note): F[Option[Note]]                           = keep(c)
      def postLinePart(c: LinePart): F[Option[LinePart]]               = keep(c)
      def postMisc(c: MiscContainer): F[Option[MiscContainer]]         = keep(c)
      def postRoot(c: RootContainer): F[Option[RootContainer]]         = keep(c)
      def postZeile(c: ZeileContainer): F[Option[ZeileContainer]]      = keep(c)
      def postForm(c: FormteilContainer): F[Option[FormteilContainer]] = keep(c)
      def postPara(c: ParatextContainer): F[Option[ParatextContainer]] = keep(c)


    Modifier.modify(modifier, rc).run(State(Set.empty, Nil)).value match
      case (_, None)         => Left("Could not deduplicate uuids")
      case (state, Some(rc)) => Right((rc, EvolutionResult(keepInExport = true, state.messages)))

  def makeLinePartsWithoutNotes(rc: RootContainer): EvolutionStep =
    val zeroPart: LinePart = Syllable("", "", Spaced(Nil), SyllableType.WithoutNotes)
    val modifier           = new Modifier.WithDefaults[Id]:
      override protected val mw: Wrapper = Wrapper()

      override def postZeile(c: ZeileContainer): Id[Option[ZeileContainer]] = if c.children.isEmpty then Some(c)
      else
        Some(c.copy(children = ((zeroPart +: c.children :+ zeroPart).sliding(3) map {
          case List(l: Syllable, m, r: Syllable) =>
            if l.syllableType == SyllableType.WithoutNotes && r.syllableType == SyllableType.WithoutNotes then
              m match {
                case x: LineChange  => x.copy(hasNotes = false)
                case x: FolioChange => x.copy(hasNotes = false)
                case _              => m
              }
            else m
          case List(_, m, _)                     => m
          case _                                 => throw new Exception("this should be unreachable!")
        }).toList))

    Modifier
      .modify(modifier, rc)
      .map(x => (x, EvolutionResult(true, Nil)))
      .toRight("Could not infer line parts without notes")

  def removePhantomNotes(rc: RootContainer): EvolutionStep =
    def isPhantom(note: Note) =
      val max     = BaseNote.C.halfs(6)
      val min     = BaseNote.G.halfs(3)
      val linqMax = BaseNote.G.halfs(5)
      val current = note.halfs
      current > max || current < min || (note.liquescent && current > linqMax)

    def replace(comments: List[Comment], phantom: String, syllable: String) = comments.map:
      case c if c.startUUID == phantom && c.endUUID == phantom => c.copy(startUUID = syllable, endUUID = syllable)
      case c if c.startUUID == phantom                         => c.copy(startUUID = syllable)
      case c if c.endUUID == phantom                           => c.copy(endUUID = syllable)
      case c                                                   => c

    type F[A] = State[List[Comment], A]
    val modifier = new Modifier.WithDefaults[F]:
      override protected val mw: Wrapper = Wrapper()

      override def postLinePart(c: LinePart): F[Option[LinePart]] = c match
        case syllable: Syllable =>
          syllable.getAllNotes match
            case singleton :: Nil if isPhantom(singleton) =>
              State(cmts => (replace(cmts, singleton.uuid, syllable.uuid), Some(syllable.copy(notes = Spaced(Nil)))))
            case _                                        => super.postLinePart(c)
        case _                  => super.postLinePart(c)

      override def postRoot(c: RootContainer): F[Option[RootContainer]] = State.get.map(s => Some(c.copy(comments = s)))

    Modifier
      .modify(modifier, rc)
      .runA(rc.comments)
      .value
      .map((_, EvolutionResult(true, Nil)))
      .toRight("Could not remove phantom notes")

  def applyAllJsonEvolutions(in: Json): EvolutionStep =
    def modifyDecoded(f: RootContainer => EvolutionStep) =
      State.modify[EvolutionStep]:
        case Left(err)        => Left(err)
        case Right((rc, er1)) => f(rc).map { case (res, er2) => (res, er1.combine(er2)) }

    val noNulls = decode[Json](in.printWith(db.DBRunner.jsonPrinter)) match
      case Left(_)      => throw new RuntimeException("Should never happen: a json should always be reencodeable")
      case Right(value) => value

    val nextJson = (for
      _ <- State.modify(Evolutions.recurse(_, Evolutions.removeEndsWord))
      _ <- State.modify(Evolutions.recurse(_, Evolutions.certainToEmendationFolder))
      _ <- State.modify(Evolutions.recurse(_, Evolutions.removeEndId))
      _ <- State.modify(Evolutions.recurse(_, Evolutions.removeDataArrayInZeile))
      _ <- State.modify(Evolutions.recurse(_, Evolutions.removeDataObjectFromMiscContainer))
      _ <- State.modify(Evolutions.recurse(_, Evolutions.addWithoutNotesAttributeToLineParts))
    yield ()).runS(noNulls).value

    val nextContainer = fixDocumentLevel(nextJson)

    (for
      _ <- modifyDecoded(makeIdsUnique)
      _ <- modifyDecoded(makeLinePartsWithoutNotes)
      _ <- modifyDecoded(removePhantomNotes)
    yield ()).runS(nextContainer).value

  final case class EvolutionResult(keepInExport: Boolean, messages: List[String]):
    def combine(er: EvolutionResult): EvolutionResult =
      EvolutionResult(keepInExport && er.keepInExport, messages ++ er.messages)

  sealed trait Probe derives CanEqual:
    def add(p: Probe): Probe = (this, p) match
      case (Empty, other)                       => other
      case (other, Empty)                       => other
      case (Inconsistent(d1), Inconsistent(d2)) => Inconsistent(Math.max(d1, d2))
      case (Inconsistent(d1), Consistent(d2))   => Inconsistent(Math.max(d1, d2))
      case (Consistent(d1), Inconsistent(d2))   => Inconsistent(Math.max(d1, d2))
      case (Consistent(d1), Consistent(d2))     =>
        if d1 == d2 then Consistent(d1)
        else Inconsistent(Math.max(d1, d2))

    def inc: Probe = this match
      case Empty                  => Empty
      case Consistent(maxDepth)   => Consistent(maxDepth + 1)
      case Inconsistent(maxDepth) => Inconsistent(maxDepth + 1)

  case object Empty                            extends Probe
  final case class Consistent(maxDepth: Int)   extends Probe
  final case class Inconsistent(maxDepth: Int) extends Probe
