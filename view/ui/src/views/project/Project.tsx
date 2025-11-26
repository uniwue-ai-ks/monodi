import './Project.scss';

import bandde from '../../images/cm_band_de.jpg';
import bandfr from '../../images/cm_band_franz.jpg';

export function Project() {
  return <div className="project-main">
    <div className="left">
      <img src={bandde} alt="CM II 2" />
      <img src={bandfr} alt="CM II 1" />
    </div>
    <div className="content">


      <p> Das Corpus monodicum (CM) ist ein Langzeitforschungsprojekt der Akademie der Wissenschaften und der Literatur | Mainz,
        [<a href="http://www.adwmainz.de/projekte/corpus-monodicum-die-einstimmige-musik-des-lateinischen-mittelalters-gattungen-werkbestaende-kontexte/informationen.html">http://www.adwmainz.de/projekte/corpus-monodicum-die-einstimmige-musik-des-lateinischen-mittelalters-gattungen-werkbestaende-kontexte/informationen.html</a>]
        innerhalb des Forschungsprogramms der Union der deutschen Akademien der Wissenschaften.
        Seine Laufzeit beträgt 16 Jahre (2011 bis 2026).</p>
      <p>Das Projekt widmet sich der Erforschung und Edition musikhistorisch signifikanter, editorisch weithin unerschlossener Bestände
        der einstimmigen Musik des europäischen Mittelalters mit lateinischem Text mit dem Ziel, ein philologisch gesichertes Fundament
        für die weitere Erforschung der Formungsphase europäischer Musik zu legen und somit eines der größten Defizite der
        musikhistorischen Mittelalterforschung zu beseitigen.</p>
      <p>Die Edition wird am Zentrum für Philologie und Digitalität (ZPD) der Universität Würzburg unter Leitung des Musikhistorikers
        Andreas Haug [<a href="https://www.musikwissenschaft.uni-wuerzburg.de/team/haug-andreas-prof-dr/">https://www.musikwissenschaft.uni-wuerzburg.de/team/haug-andreas-prof-dr/</a>]
        und des Informatikers Frank Puppe [<a href="https://www.informatik.uniwuerzburg.de/is/mitarbeiter/puppe-frank/">https://www.informatik.uniwuerzburg.de/is/mitarbeiter/puppe-frank/</a>]
        herausgegeben. </p>
      <p>Die Arbeitsstelle des Projektes befindet sich am Institut für Musikforschung [<a href="https://www.musikwissenschaft.uni-wuerzburg.de/startseite/">https://www.musikwissenschaft.uni-wuerzburg.de/startseite/</a>] der Universität Würzburg.</p>
      <p>Das digitale Editionskorpus umfasst annotierte Transkriptionen von rund 5000 Gesängen oder Gesangskomplexen verschiedener
        Gattungen (Kyrie, Gloria, Sanctus, Agnus, Tropen, Sequenzen, Lieder und Spiele) mit Texten in lateinischer Sprache aus
        über 200 meist handschriftlichen Quellen des 12. bis 16. Jahrhunderts englischer, normannischer, französischer, deutscher,
        italienischer, aquitanischer und katalanischer Herkunft. </p>
      <p>Die Druckausgabe des Corpus monodicum erscheint beim Verlag Schwabe AG in Basel [<a href="https://schwabe.ch/produkttypen/editionen/corpus-monodicum/">https://schwabe.ch/produkttypen/editionen/corpus-monodicum/</a>].
        Das Notengrafikkonzept der Druckausgabe und die bei deren Herstellung eingesetzte Software Monodi für die digitale Transkription
        der Texte und der Melodien wurden 2012 bis 2015 in Zusammenarbeit mit der Firma Notengrafik Berlin GmbH
        [<a href="https://notengrafik.com/">https://notengrafik.com/</a>] entwickelt.</p>
      <p>Die im Projekt eingesetzte Software MonodiPlus für die digitale Transkription der Texte und der
        Melodien und die Präsentationssoftware (Viewer) für die Onlineausgabe wurden 2018 bis 2020 von der Würzburger Firma
        Olyro GmbH in Zusammenarbeit mit dem Lehrstuhl für Künstliche Intelligenz und Wissenssysteme der Universität Würzburg entwickelt.</p>
      <p>Die Kodierung der Editionsdaten beruht auf dem Format der Music Encoding Initiative (MEI)
        [<a href="https://music-encoding.org/projects/corpus-monodicum.html">https://music-encoding.org/projects/corpus-monodicum.html</a>],
        an der sich das Corpus monodicum beteiligt.</p>
    </div>
  </div>;
}
