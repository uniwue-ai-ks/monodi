package de.olyro.monodi.exprt
package iiif

class Scope private (
    val vals: Map[String, Int]
)

object Scope:
  def get(name: String): Scope ?=> Int =
    summon[Scope].vals.getOrElse(name, sys.error(s"unknown variable: $name"))

  def run(f: Scope ?=> Int, vals: (String, Int)*): Either[String, Int] =
    val scope   = new Scope(vals.toMap)
    given Scope = scope
    try Right(f)
    catch case e: Throwable => Left(e.getMessage())

  def runExpr(s: String, vals: (String, Int)*): Either[String, Int] =
    Expr.parseString(s).flatMap(run(_, vals*))
