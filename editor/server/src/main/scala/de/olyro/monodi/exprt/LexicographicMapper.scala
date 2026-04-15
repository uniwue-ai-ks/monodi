package de.olyro.monodi.exprt

object LexicographicMapper:
  /**
    * Makes a string, for example a dokumenten_id, lexicographically sortable
    * by splitting it into segments and making each segment lexicographically
    * sortable.
    *
    * For example, `Civ 101-86-21` becomes `Civ-000/101-00/86-00/21`.
    *
    * Not that for old documents that works nicely with `r` for recto and `v` for verso
    * because recto is both the front page and `r` comes before `v` for verso, which is the 
    * back page.
    */
  def makeLexicographicallySortable(input: String): String               =
    val segments = splitIntoSegments(input)
    val mapped   = segments.map(turnIntoSortableString)
    mapped.mkString("-")

    /**
    * Splits a given text into segments of either letters or numbers
    * and drops any other characters.
    *
    * For example, `Civ 101-86-21` becomes `List(Left("Civ"), Right(101), Right(86), Right(21))`.
    *
    * Not that for old documents that works nicely with `r` for recto and `v` for verso
    * because recto is both the front page and `r` comes before `v` for verso, which is the 
    * back page.
    */
  private def splitIntoSegments(text: String): List[Either[String, Int]] =
    text.headOption match
      case None     => Nil
      case Some(hc) =>
        if hc.isDigit then
          val (prefix, suffix) = text.span(_.isDigit)
          Right(prefix.toInt) :: splitIntoSegments(suffix)
        else if hc.isLetter then
          val (prefix, suffix) = text.span(_.isLetter)
          Left(prefix) :: splitIntoSegments(suffix)
        else splitIntoSegments(text.tail)

  /**
    * Keeps strings but turns numbers into lexicographically sortable strings. Work
    * by prepending a prefix of zeros of the same length as the number itself, followed
    * by a slash.
    *
    * @param segment
    * @return
    */
  private def turnIntoSortableString(segment: Either[String, Int]): String =
    segment match
      case Left(str)  => str // strings are already lexicographically sortable
      case Right(num) =>
        val asString = num.toString
        val length   = asString.length
        val prefix   = "0" * length
        prefix + "/" + asString
