package de.olyro.monodi.exprt.print
package bookbinding

import zio.*

final case class State(
    isShutDown: Boolean,
    queue: List[(WorkId, Request)],
    inProgress: Option[WorkState]
):
  def enqueue(workId: WorkId, request: Request): (Boolean, State) =
    if isShutDown || queue.length > 10 then (false, this)
    else (true, copy(queue = queue :+ (workId, request)))

  def reportProgress(workId: WorkId, progress: Progress): State =
    if isShutDown then this
    else
      inProgress match
        case None                                     => this
        case Some(WorkState(currentWorkId, _, fiber)) =>
          if currentWorkId == workId then copy(inProgress = Some(WorkState(currentWorkId, progress, fiber)))
          else this

  def getStatus(workId: WorkId): Option[Status] =
    val forCurrent = inProgress.filter(_.work == workId).map(ws => Status.Active(ws.progress))

    val forQueued = Some(queue.indexWhere(_._1 == workId)).filter(_ >= 0).map(Status.Queued.apply)

    forCurrent.orElse(forQueued)

  def popFromQueue: (Option[(WorkId, Request)], State) =
    if isShutDown then (None, this)
    else
      queue match
        case Nil          => (None, this)
        case head :: tail => (Some(head), copy(queue = tail))

  def abort(workId: WorkId): (Option[Fiber[Throwable, Unit]], State) =
    if isShutDown then (None, this)
    else
      inProgress match
        case None                                     => (None, this)
        case Some(WorkState(currentWorkId, _, fiber)) =>
          if currentWorkId == workId then (Some(fiber), copy(inProgress = None))
          else (None, this)

object State:
  val empty: State = State(false, Nil, None)
