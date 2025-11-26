package de.olyro.monodi.exprt.print
package bookbinding

import zio.*

final case class WorkState(
    work: WorkId,
    progress: Progress,
    fiber: Fiber[Throwable, Unit]
)
