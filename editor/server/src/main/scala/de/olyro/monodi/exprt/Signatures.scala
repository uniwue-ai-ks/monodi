package de.olyro.monodi.exprt

import de.olyro.monodi.data.notes.*

object Signatures:
  def getSignatures(root: RootContainer): List[String] =
    ViewModel.getLineMetadata(root)
      .map(_.meta.signatures.mkString(""))
      .filter(_.nonEmpty)
      .distinct
