export interface Bibliotheksort {
  name: string;
  bibliotheken: Bibliothek[];
}

export interface Bibliothek {
  name: string;
  signaturen: Signatur[];
}

export interface Signatur {
  name: string;
  quellensigle: string;
}

export interface Herkunftsregion {
  name: string;
  orte: Herkunftsort[];
}

export interface Herkunftsort {
  name: string;
  institute: string[];
}

export interface Gattung {
  gattung1: string;
  kürzel: string;
  untergattungen: Untergattung[];
}

export interface Untergattung {
  gattung2: string;
  kürzel: string;
}

export interface Fest {
  name: string;
  datum: string;
}

export function isOfBibliotheksort(x: any): boolean {
  return typeof(x['name']) === 'string' && x['bibliotheken'].filter((x: any) => { return isOfBibliothek(x); }).length === x['bibliotheken'].length;
}

export function isOfBibliothek(x: any): boolean {
  return typeof(x['name']) === 'string' && x['signaturen'].filter((x: any) => { return isOfSignatur(x); }).length === x['signaturen'].length;
}

export function isOfSignatur(x: any): boolean {
  return typeof(x['name']) === 'string' && typeof(x['quellensigle']) === 'string';
}

export function isOfHerkunftsregion(x: any): boolean {
  return typeof(x['name']) === 'string' && x['orte'].filter((x: any) => { return isOfHerkunftsort(x); }).length === x['orte'].length;
}

export function isOfHerkunftsort(x: any): boolean {
  return typeof(x['name']) === 'string' && x['institute'].filter((x: any) => { return typeof(x) === 'string'; }).length === x['institute'].length;
}

export function isOfGattung(x: any): boolean {
  return typeof(x['gattung1']) === 'string' && typeof(x['kürzel']) === 'string' && x['untergattungen'].filter((x: any) => { return isOfUntergattung(x); }).length === x['untergattungen'].length;
}

export function isOfUntergattung(x: any): boolean {
  return typeof(x['gattung2']) === 'string' && typeof(x['kürzel']) === 'string';
}

export function isOfFesttag(x: any): boolean {
  return typeof(x['name']) === 'string' && x['feiern'].filter((x: any) => { return typeof(x) === 'string'; }).length === x['feiern'].length;
}

export const regions: Herkunftsregion[] = [
  {
    "name": "Quellen französischer Herkunft",
    "orte": [
      {
        "name": "Noyon",
        "institute": ["Kathedrale Notre-Dame"]
      },
      {
        "name": "Corbie",
        "institute": ["Abtei Saint-Pierre"]
      },
      {
        "name": "Reims / Paris",
        "institute": []
      },
      {
        "name": "Autun",
        "institute": ["Abtei Saint-Martin"]
      },
      {
        "name": "Auxerre",
        "institute": []
      },
      {
        "name": "Cambrai",
        "institute": [
          "Kathedrale Notre-Dame",
          "Cambrai"
        ]
      },
      {
        "name": "Anchin",
        "institute": []
      },
      {
        "name": "Le Puy",
        "institute": ["Kathedrale Notre-Dame"]
      },
      {
        "name": "Sion (Sitten)",
        "institute": [
          "Kathedrale",
          "Basilique de Valère (Valeria)"
        ]
      },
      {
        "name": "Laon",
        "institute": ["Kathedrale Notre-Dame"]
      },
      {
        "name": "Paris, später Saint-Junien",
        "institute": []
      },
      {
        "name": "Beauvais",
        "institute": ["Kathedrale Saint-Pierre"]
      },
      {
        "name": "Fleury",
        "institute": []
      },
      {
        "name": "Saint-Lomer / Fleury",
        "institute": []
      },
      {
        "name": "Bayeux",
        "institute": []
      },
      {
        "name": "Nevers",
        "institute": []
      },
      {
        "name": "Saint-Denis",
        "institute": ["Abtei Saint-Denis"]
      },
      {
        "name": "Paris",
        "institute": [
          "Kathedrale Notre-Dame",
          "Saint-Victor"
        ]
      },
      {
        "name": "Clermont-Ferrand",
        "institute": []
      },
      {
        "name": "Saintes",
        "institute": []
      },
      {
        "name": "Compiègne",
        "institute": ["Abtei Saint-Corneille"]
      },
      {
        "name": "Beauvais ",
        "institute": ["Kathedrale Saint-Pierre"]
      },
      {
        "name": "Nevers",
        "institute": ["Kathedrale Saint-Cyr"]
      },
      {
        "name": "Beauvais",
        "institute": ["Saint-Michel"]
      },
      {
        "name": "Chartres",
        "institute": ["Kathedrale Notre-Dame"]
      },
      {
        "name": "Jumièges",
        "institute": ["Abtei Jumièges"]
      },
      {
        "name": "Origny-Sainte-Benoîte",
        "institute": []
      },
      {
        "name": "Sens",
        "institute": ["Kathedrale Saint-Étienne"]
      },
      {
        "name": "Tours",
        "institute": []
      },
      {
        "name": "Verdun",
        "institute": ["Abtei Saint-Airy"]
      },
      {
        "name": "Zypern",
        "institute": []
      }
    ]
  },
  {
    "name": "Quellen normannischer und normanno-sizilischer Herkunft",
    "orte": [
      {
        "name": "Rouen",
        "institute": ["Kathedrale Notre-Dame de l\u2019Assomption"]
      },
      {
        "name": "Süditalien oder Sizilien",
        "institute": ["Hofkapelle der normannischen Herrscher"]
      },
      {
        "name": "Palermo",
        "institute": [
          "Cappella Palatina",
          "Kathedrale"
        ]
      },
      {
        "name": "Catania",
        "institute": ["Benediktinerkloster Sant\u2019Agata"]
      }
    ]
  },
  {
    "name": "Quellen englischer Herkunft",
    "orte": [
      {
        "name": "Dublin",
        "institute": []
      },
      {
        "name": "England",
        "institute": []
      },
      {
        "name": "Sarum",
        "institute": []
      },
      {
        "name": "Worchester / Canterbury",
        "institute": []
      },
      {
        "name": "Saint Albans",
        "institute": []
      },
      {
        "name": "Chichester",
        "institute": ["Kathedrale"]
      },
      {
        "name": "London?",
        "institute": []
      },
      {
        "name": "Wilton",
        "institute": ["Benediktinerin}nabtei St Edith + Fragmente Alison Altstatt"]
      }
    ]
  },
  {
    "name": "Quellen deutscher Herkunft",
    "orte": [
      {
        "name": "Aachen",
        "institute": ["Marienstift"]
      },
      {
        "name": "Admont",
        "institute": []
      },
      {
        "name": "Bamberg",
        "institute": ["Dom"]
      },
      {
        "name": "Region Trier",
        "institute": []
      },
      {
        "name": "Erfurt",
        "institute": ["Kollegiatstift Sankt Marien"]
      },
      {
        "name": "Quedlinburg",
        "institute": ["möglicherweise Sankt Nicolai"]
      },
      {
        "name": "Einsiedeln",
        "institute": []
      },
      {
        "name": "Engelberg",
        "institute": []
      },
      {
        "name": "Fulda",
        "institute": ["Petersberg"]
      },
      {
        "name": "Klosterneuburg",
        "institute": []
      },
      {
        "name": "Halberstadt",
        "institute": ["Dom"]
      },
      {
        "name": "Innichen (San Candido)",
        "institute": []
      },
      {
        "name": "Erfurt",
        "institute": ["Augustiner-Chorfrauenstift Neuwerk"]
      },
      {
        "name": "Fulda",
        "institute": []
      },
      {
        "name": "Kölner Raum",
        "institute": []
      },
      {
        "name": "Lübeck",
        "institute": []
      },
      {
        "name": "Dießen",
        "institute": ["Augustiner-Chorherrenstift"]
      },
      {
        "name": "Oberaltaich",
        "institute": []
      },
      {
        "name": "Diözese Freising",
        "institute": []
      },
      {
        "name": "Moosburg",
        "institute": ["Kollegiatstift Sankt Castulus"]
      },
      {
        "name": "Meißen",
        "institute": ["Dom"]
      },
      {
        "name": "Andenne",
        "institute": []
      },
      {
        "name": "Nürnberg",
        "institute": ["Sankt Lorenz"]
      },
      {
        "name": "Admont / Moggio",
        "institute": []
      },
      {
        "name": "Preetz",
        "institute": []
      },
      {
        "name": "Möglicherweise Regensburg",
        "institute": []
      },
      {
        "name": "Region Trier",
        "institute": []
      },
      {
        "name": "Utrecht",
        "institute": ["Stift Sankt Marien"]
      },
      {
        "name": "Wien",
        "institute": ["Schottenstift"]
      },
      {
        "name": "Diözese Hildesheim",
        "institute": []
      },
      {
        "name": "Würzburg",
        "institute": ["Stift Haug"]
      }
    ]
  },
  {
    "name": "Quellen italienischer Herkunft",
    "orte": [
      {
        "name": "Aosta",
        "institute": []
      },
      {
        "name": "Benevento",
        "institute": []
      },
      {
        "name": "Venedig",
        "institute": ["San Marco"]
      },
      {
        "name": "Ravenna",
        "institute": []
      },
      {
        "name": "Cividale del Friuli",
        "institute": ["Kathedrale Santa Maria Assunta"]
      },
      {
        "name": "Rom",
        "institute": ["Santa Cecilia in Trastevere"]
      },
      {
        "name": "Aquileia",
        "institute": []
      },
      {
        "name": "Santa Maria di Pontetetto",
        "institute": []
      },
      {
        "name": "Oberitalien",
        "institute": []
      },
      {
        "name": "Modena",
        "institute": []
      },
      {
        "name": "Monza",
        "institute": []
      },
      {
        "name": "Oberitalien",
        "institute": []
      },
      {
        "name": "Nonantola",
        "institute": []
      },
      {
        "name": "Borgosesia",
        "institute": []
      },
      {
        "name": "Novara",
        "institute": []
      },
      {
        "name": "Region Ravenna",
        "institute": []
      },
      {
        "name": "Padua",
        "institute": [
          "Padua",
          "Kathedrale Santa Maria Assunta"
        ]
      },
      {
        "name": "Parma",
        "institute": []
      },
      {
        "name": "Piacenza",
        "institute": []
      },
      {
        "name": "Pisa",
        "institute": []
      },
      {
        "name": "Pistoia",
        "institute": []
      },
      {
        "name": "Faenza",
        "institute": []
      },
      {
        "name": "Nonantola",
        "institute": []
      },
      {
        "name": "Toscana",
        "institute": []
      },
      {
        "name": "Pistoia",
        "institute": []
      },
      {
        "name": "Bobbio",
        "institute": []
      },
      {
        "name": "Vermutlich Aquileia",
        "institute": []
      },
      {
        "name": "Verbania / Intra",
        "institute": []
      },
      {
        "name": "Vercelli",
        "institute": []
      },
      {
        "name": "Ivrea",
        "institute": []
      }
    ]
  },
  {
    "name": "Quellen aquitanischer Herkunft",
    "orte": [
      {
        "name": "Apt",
        "institute": ["Kathedrale Sainte-Anne"]
      },
      {
        "name": "Gaillac",
        "institute": ["Abtei Saint-Michel"]
      },
      {
        "name": "Narbonne",
        "institute": []
      },
      {
        "name": "Aurillac",
        "institute": []
      },
      {
        "name": "Saint-Yrieix",
        "institute": []
      },
      {
        "name": "Limoges",
        "institute": ["Abtei Saint-Martial"]
      },
      {
        "name": "Limousin",
        "institute": []
      },
      {
        "name": "Moissac",
        "institute": []
      }
    ]
  },
  {
    "name": "Quellen katalanischer Herkunft",
    "orte": [
      {
        "name": "Girona",
        "institute": []
      },
      {
        "name": "Katalonien",
        "institute": []
      },
      {
        "name": "Vic",
        "institute": ["Kathedrale Sant Pere"]
      }
    ]
  },
  {
    "name": "Quellen spanischer Herkunft",
    "orte": [
      {
        "name": "Santiago de Compostela",
        "institute": []
      },
      {
        "name": "Tortosa",
        "institute": []
      },
      {
        "name": "Portugal",
        "institute": []
      }
    ]
  },
  {
    "name": "Quellen von Ordenstraditionen",
    "orte": [
      {
        "name": "Franziskaner",
        "institute": []
      },
      {
        "name": "Kartäuser",
        "institute": []
      },
      {
        "name": "Prämonstratenser",
        "institute": []
      },
      {
        "name": "Zisterzienser",
        "institute": []
      },
      {
        "name": "Dominikaner",
        "institute": []
      }
    ]
  }
]

export const libraries: Bibliotheksort[] = [
  {
    "name": "Abbeville",
    "bibliotheken": [{
      "name": "Bibliothèque municipale",
      "signaturen": [{
        "name": "Ms. 7",
        "quellensigle": "Abb 7"
      }]
    }]
  },
  {
    "name": "Aachen",
    "bibliotheken": [{
      "name": "Domarchiv",
      "signaturen": [{"name": "Hs. G 13", "quellensigle": "Aa 13"}]
    }]
  },
  {
    "name": "Amiens",
    "bibliotheken": [{
      "name": "Bibliothèque municipale",
      "signaturen": [{"name": "Hs. G 13", "quellensigle": "Ami 115"}]
    }]
  },
  {
    "name": "Aosta",
    "bibliotheken": [{
      "name": "Biblioteca del Seminario Maggiore",
      "signaturen": [{"name": "Ms. D16", "quellensigle": "Ao 16"}]
    }]
  },
  {
    "name": "Apt",
    "bibliotheken": [{
      "name": "Cathédrale Sainte-Anne",
      "signaturen": [{"name": "Trésor 17", "quellensigle": "Apt 17"}]
    }]
  },
  {
    "name": "Assi",
    "bibliotheken": [{
      "name": "Biblioteca communale",
      "signaturen": [{"name": "Ms. 695 ", "quellensigle": "Ass 695"}]
    }]
  },
  {
    "name": "Augsburg",
    "bibliotheken": [{
      "name": "Universitätsbibliothek Sammlung Oettingen-Wallerstein",
      "signaturen": [{"name": "Cod. I. 2. 4° 13", "quellensigle": "AugW 13"}]
    }]
  },
  {
    "name": "Autun",
    "bibliotheken": [{
      "name": "Bibliothèque municipale",
      "signaturen": [{"name": "Ms. S 183 (Z) ", "quellensigle": "Aut 183"}]
    }]
  },
  {
    "name": "Auxerre",
    "bibliotheken": [{
      "name": "Bibliothèque municipale",
      "signaturen": [{"name": "54", "quellensigle": "Aux 54"}]
    }]
  },
  {
    "name": "Bamberg",
    "bibliotheken": [{
      "name": "Staatsbibliothek",
      "signaturen": [
        {"name": "Msc. lit. 12", "quellensigle": "Ba 12"},
        {"name": "Msc. lit. 45", "quellensigle": "Ba 45"}
      ]
    }]
  },
  {
    "name": "Benevento",
    "bibliotheken": [{
      "name": "Biblioteca Capitolare",
      "signaturen": [
        {"name": "VI.34", "quellensigle": "Ben 34"},
        {"name": "VI.35", "quellensigle": "Ben 35"}
      ]
    }]
  },
  {
    "name": "Berlin",
    "bibliotheken": [{
      "name": "Staatsbibliothek PKB",
      "signaturen": [
        {"name": "Lat. 4° 665", "quellensigle": "Be 664"},
        {"name": "Ms. lat. fol. 792", "quellensigle": "Be 792"},
        {"name": "Mus. ms. 40078", "quellensigle": "40078"},
        {"name": "Mus. ms. 40608", "quellensigle": "40608"}
      ]
    }]
  },
  {
    "name": "Bologna",
    "bibliotheken": [{
      "name": "Civico Museo (Liceo)",
      "signaturen": [{"name": "Q7", "quellensigle": "Bo 7"}]
    }]
  },
  {
    "name": "Cambrai",
    "bibliotheken": [{
      "name": "Bibliothèque municipale",
      "signaturen": [
        {"name": "Ms. 72 (73)", "quellensigle": "Cai 72"},
        {"name": "Ms. 172 (167) ", "quellensigle": "Cai 172"}
      ]
    }]
  },
  {
    "name": "Cambridge",
    "bibliotheken": [{
      "name": "University Library",
      "signaturen": [
        {"name": "Add. 710", "quellensigle": "Cdg 710"},
        {"name": "Ms. Ff. I. 17", "quellensigle": "Cdg 17"}
      ]
    }]
  },
  {
    "name": "Cividale",
    "bibliotheken": [{
      "name": "Museo Archeologico Nazionale",
      "signaturen": [
        {"name": "Ms. LVI", "quellensigle": "Civ 56"},
        {"name": "Ms. CI", "quellensigle": "Civ 101"},
        {"name": "Ms. CII", "quellensigle": "Civ 102"}
      ]
    }]
  },
  {
    "name": "Cologny (Genf)",
    "bibliotheken": [{
      "name": "Bibliotheca Bodmeriana",
      "signaturen": [{"name": "Ms. C 74", "quellensigle": "GeB 74"}]
    }]
  },
  {
    "name": "Douai",
    "bibliotheken": [{
      "name": "Bibliothèque municipale",
      "signaturen": [{"name": "Ms. 246", "quellensigle": "Dou 246"}]
    }]
  },
  {
    "name": "Einsiedeln",
    "bibliotheken": [{
      "name": "Stiftsbibliothek",
      "signaturen": [
        {"name": "Cod. 366", "quellensigle": "Ei 366"},
        {"name": "Cod. 610", "quellensigle": "Ei 610"}
      ]
    }]
  },
  {
    "name": "Engelberg",
    "bibliotheken": [{
      "name": "Stiftsbibliothek",
      "signaturen": [{"name": "Cod. 314", "quellensigle": "Eng 314"}]
    }]
  },
  {
    "name": "Fulda",
    "bibliotheken": [{
      "name": "Hessische Landesbibliothek",
      "signaturen": [{"name": "Aa 62", "quellensigle": "Ful 62"}]
    }]
  },
  {
    "name": "Girona",
    "bibliotheken": [{
      "name": "Archivum del seminario Episcopal",
      "signaturen": [{"name": "Cod. 4", "quellensigle": "Gir 4"}]
    }]
  },
  {
    "name": "Gorizia",
    "bibliotheken": [{
      "name": "Biblioteca del Seminario Teologico Centrale",
      "signaturen": [{"name": "Ms. I", "quellensigle": "Go I"}]
    }]
  },
  {
    "name": "Graz",
    "bibliotheken": [{
      "name": "Universitätsbibliothek",
      "signaturen": [{"name": "Ms. 807", "quellensigle": "Gr 807"}]
    }]
  },
  {
    "name": "Grenoble",
    "bibliotheken": [{
      "name": "Bibliothèque municipale",
      "signaturen": [{"name": "Ms. 4413", "quellensigle": "Gren 4413"}]
    }]
  },
  {
    "name": "Halberstadt",
    "bibliotheken": [{
      "name": "Domschatz",
      "signaturen": [{"name": "Inv.-Nr. 45", "quellensigle": "Halb 45"}]
    }]
  },
  {
    "name": "Innichen (San Candido)",
    "bibliotheken": [{
      "name": "Bibliothek des Kollegiatstiftes",
      "signaturen": [{"name": "VII a 7", "quellensigle": "SCan 7"}]
    }]
  },
  {
    "name": "Königswinter",
    "bibliotheken": [{
      "name": "Katholische Pfarrei Sankt Remigius",
      "signaturen": [{"name": "ohne Signatur (\u201eDrachenfels-Missale\u201c)", "quellensigle": "Kön D"}]
    }]
  },
  {
    "name": "Karlsruhe",
    "bibliotheken": [{
      "name": "Badische Landesbibliothek",
      "signaturen": [{"name": "St. Peter perg. 16 ", "quellensigle": "Kar 16"}]
    }]
  },
  {
    "name": "Kassel",
    "bibliotheken": [{
      "name": "Universitätsbibliothek, Landesbibliothek und Murhardsche Bibl.",
      "signaturen": [{"name": "4° Ms. theol. 5 ", "quellensigle": "Ka 5"}]
    }]
  },
  {
    "name": "Kopenhagen",
    "bibliotheken": [{
      "name": "Carl Claudius\u2019 musikhistoriske Samling",
      "signaturen": [{"name": "13", "quellensigle": "Kop 13"}]
    }]
  },
  {
    "name": "Lübeck",
    "bibliotheken": [{
      "name": "Bibliothek der Hansestadt",
      "signaturen": [{"name": "Ms. theol. lat. 2° 11", "quellensigle": "Lü 11"}]
    }]
  },
  {
    "name": "Laon",
    "bibliotheken": [{
      "name": "Bibliothèque municipale",
      "signaturen": [{"name": "Ms. 263  ", "quellensigle": "La 263"}]
    }]
  },
  {
    "name": "Le Puy",
    "bibliotheken": [{
      "name": "Bibliothèque de Grand Séminaire",
      "signaturen": [{"name": "A V 7 009  ", "quellensigle": "Le Puy 009"}]
    }]
  },
  {
    "name": "Limoges",
    "bibliotheken": [{
      "name": "Bibliothèque municipale",
      "signaturen": [{"name": "Ms. 2 (17)", "quellensigle": "Lim 2"}]
    }]
  },
  {
    "name": "London",
    "bibliotheken": [{
      "name": "British Library",
      "signaturen": [
        {"name": "Egerton 2615", "quellensigle": "Lo 2615"},
        {"name": "Add. 12194", "quellensigle": "Lo 12194"},
        {"name": "Cotton Caligula A XIV", "quellensigle": "Lo 14"},
        {"name": "Royal 2 B. IV", "quellensigle": "Lo 4"},
        {"name": "Add. 31384 (OCart)", "quellensigle": "Lo 31384"}
      ]
    }]
  },
  {
    "name": "Lucca",
    "bibliotheken": [{
      "name": "Biblioteca Capitolare",
      "signaturen": [{"name": "Ms. 603", "quellensigle": "Luc 603"}]
    }]
  },
  {
    "name": "München",
    "bibliotheken": [
      {
        "name": "Bayerische Staatsbibliothek",
        "signaturen": [
          {"name": "Clm 5539", "quellensigle": "Mü 5539"},
          {"name": "Clm 9508", "quellensigle": "Mü 9508"},
          {"name": "Clm 29307(2", "quellensigle": "Mü 29307"}
        ]
      },
      {
        "name": "Universitätsbibliothek",
        "signaturen": [{"name": "2° Cod. ms. 156 (= Cim. 100)", "quellensigle": "Mü 156"}]
      }
    ]
  },
  {
    "name": "Madrid",
    "bibliotheken": [{
      "name": "Biblioteca Nacional",
      "signaturen": [
        {"name": "Ms. 288", "quellensigle": "Ma 288"},
        {"name": "Ms. 289", "quellensigle": "Ma 289"},
        {"name": "Ms. 19421", "quellensigle": "Ma 19421"},
        {"name": "Vit. 20-4", "quellensigle": "Ma 20-4"}
      ]
    }]
  },
  {
    "name": "Melk",
    "bibliotheken": [{
      "name": "Stiftsbibliothek",
      "signaturen": [{"name": "Cod. 109", "quellensigle": "Mel 109"}]
    }]
  },
  {
    "name": "Mailand",
    "bibliotheken": [{
      "name": "Biblioteca Ambrosiana",
      "signaturen": [{"name": "Ms. D 25 s (Fragm.)", "quellensigle": "Mil 25"}]
    }]
  },
  {
    "name": "Modena",
    "bibliotheken": [{
      "name": "Biblioteca Capitolare",
      "signaturen": [{"name": "Ms. O.I.16", "quellensigle": "Mod 16"}]
    }]
  },
  {
    "name": "Montpellier",
    "bibliotheken": [{
      "name": "Bibliothèque de la Faculté de Médicine",
      "signaturen": [{"name": "H 304", "quellensigle": "Mon 304"}]
    }]
  },
  {
    "name": "Monza",
    "bibliotheken": [{
      "name": "Biblioteca Capitolare",
      "signaturen": [
        {"name": "Ms. c-14/77", "quellensigle": "Mza 77"},
        {"name": "Ms. K 11", "quellensigle": "Mza 11"}
      ]
    }]
  },
  {
    "name": "Namur",
    "bibliotheken": [{
      "name": "Diözesanmuseum",
      "signaturen": [{"name": "Ms. 1", "quellensigle": "Nam 1"}]
    }]
  },
  {
    "name": "Naumburg",
    "bibliotheken": [{
      "name": "Domstiftsbibliothek",
      "signaturen": [{"name": "Chorbuch I", "quellensigle": "Nau I"}]
    }]
  },
  {
    "name": "New York",
    "bibliotheken": [
      {
        "name": "The Morgan Library & Museum",
        "signaturen": [{"name": "M. 905 (\u201eGänsebuch\u201c)", "quellensigle": "NY 905"}]
      },
      {
        "name": "Columbia University, Barnard",
        "signaturen": [{"name": "MS 3", "quellensigle": "NY 3"}]
      }
    ]
  },
  {
    "name": "Nonatola",
    "bibliotheken": [{
      "name": "Biblioteca dell\u201aÄôAbazia",
      "signaturen": [{"name": "Cod. 1", "quellensigle": "Non 1"}]
    }]
  },
  {
    "name": "Novara",
    "bibliotheken": [
      {
        "name": "Archivio Storico Diocesano",
        "signaturen": [{"name": "Ms. G 3", "quellensigle": "Nvr 3"}]
      },
      {
        "name": "Biblioteca Capitolare di S. Maria",
        "signaturen": [{"name": "Ms. CXVI", "quellensigle": "Nvr 116"}]
      }
    ]
  },
  {
    "name": "Orléans",
    "bibliotheken": [{
      "name": "Bibliothèque municipale",
      "signaturen": [
        {"name": "Ms. 129", "quellensigle": "Or 129"},
        {"name": "Ms. 201", "quellensigle": "Or 201"}
      ]
    }]
  },
  {
    "name": "Oxford",
    "bibliotheken": [{
      "name": "Bodleian Library",
      "signaturen": [
        {"name": "University College 148 ", "quellensigle": "Ox 148"},
        {"name": "Laud Misc. 358", "quellensigle": "Ox 358"},
        {"name": "MS. Canon. Liturg. 340", "quellensigle": "Ox 340"}
      ]
    }]
  },
  {
    "name": "Padua",
    "bibliotheken": [
      {
        "name": "Biblioteca Capitolare",
        "signaturen": [
          {"name": "A 47", "quellensigle": "Pad 47"},
          {"name": "B 16*", "quellensigle": "Pad 16"},
          {"name": "C 55", "quellensigle": "Pad 55"}
        ]
      },
      {
        "name": "Biblioteca Universitaria",
        "signaturen": [{"name": "1340", "quellensigle": "Pad 1340"}]
      }
    ]
  },
  {
    "name": "Paris",
    "bibliotheken": [
      {
        "name": "Bibliothèque de l\u2019Arsenal",
        "signaturen": [
          {"name": "Ms. 279", "quellensigle": "PaA 279"},
          {"name": "Ms. 135", "quellensigle": "PaA 135"}
        ]
      },
      {
        "name": "Bibliothèque Mazarine",
        "signaturen": [{"name": "1708", "quellensigle": "PaM 1708"}]
      },
      {
        "name": "Bibliothèque Sainte-Geneviève",
        "signaturen": [{"name": "117", "quellensigle": "PaSG 117"}]
      },
      {
        "name": "Bibliothèque nationale de France",
        "signaturen": [
          {"name": "Lat. 1107", "quellensigle": "Pa 1107"},
          {"name": "Lat. 1112 ", "quellensigle": "Pa 1112"},
          {"name": "Lat. 1274 ", "quellensigle": "Pa 1274"},
          {"name": "Lat. 14452", "quellensigle": "Pa 14452"},
          {"name": "Lat. 14819 ", "quellensigle": "Pa 14819"},
          {"name": "Lat. 16309 ", "quellensigle": "Pa 16049"},
          {"name": "Lat. 16819", "quellensigle": "Pa 16819"},
          {"name": "Nouv. acq. lat. 1064", "quellensigle": "Pa 1064"},
          {"name": "Nouv. acq. lat. 1235", "quellensigle": "Pa 1235"},
          {"name": "Lat. 904", "quellensigle": "Pa 904"},
          {"name": "Lat. 905", "quellensigle": "Pa 905"},
          {"name": "Lat. 1213", "quellensigle": "Pa 1213"},
          {"name": "Lat. 776", "quellensigle": "Pa 776"},
          {"name": "Lat. 778", "quellensigle": "Pa 778"},
          {"name": "Lat. 887", "quellensigle": "Pa 887"},
          {"name": "Lat. 903", "quellensigle": "Pa 903"},
          {"name": "Lat. 909", "quellensigle": "Pa 909"},
          {"name": "Lat. 1139", "quellensigle": "Pa 1139"},
          {"name": "Lat. 3719b", "quellensigle": "Pa 3719b"},
          {"name": "Lat. 3719c", "quellensigle": "Pa 3719c"},
          {"name": "Nouv. acq. lat. 1871", "quellensigle": "Pa 1871"},
          {"name": "Lat. 5302", "quellensigle": "Pa 5302"},
          {"name": "Lat. 833 (OPraem)", "quellensigle": "Pa 883"},
          {"name": "Lat. 17328 (OCist)", "quellensigle": "Pa 17328"}
        ]
      }
    ]
  },
  {
    "name": "Parma",
    "bibliotheken": [{
      "name": "Archivio Capitolare",
      "signaturen": [{"name": "Ms. AC 12", "quellensigle": "Parm 12"}]
    }]
  },
  {
    "name": "Piacenza",
    "bibliotheken": [{
      "name": "Biblioteca Capitolare",
      "signaturen": [{"name": "Ms. 65", "quellensigle": "Pia 65"}]
    }]
  },
  {
    "name": "Pisa",
    "bibliotheken": [{
      "name": "Biblioteca Cateriniana",
      "signaturen": [{"name": "219 (Stäblein: Seminario, s.n.) ", "quellensigle": "Pis 219"}]
    }]
  },
  {
    "name": "Pistoia",
    "bibliotheken": [{
      "name": "Biblioteca Capitolare",
      "signaturen": [
        {"name": "Ms. C 120a", "quellensigle": "Pst 120a"},
        {"name": "Ms. C 121a", "quellensigle": "Pst 121a"}
      ]
    }]
  },
  {
    "name": "Preetz",
    "bibliotheken": [{
      "name": "Klosterarchiv",
      "signaturen": [{"name": "Reihe V G2", "quellensigle": "Pre 2"}]
    }]
  },
  {
    "name": "Provins",
    "bibliotheken": [{
      "name": "Bibliothèque municipale",
      "signaturen": [{"name": "Ms. 12 (24)", "quellensigle": "Pro 12"}]
    }]
  },
  {
    "name": "Rom",
    "bibliotheken": [
      {
        "name": "Biblioteca Apostolica Vaticana",
        "signaturen": [{"name": "Vat. lat. 3797", "quellensigle": "Vat 3797"}]
      },
      {
        "name": "Biblioteca Casanatense",
        "signaturen": [{"name": "Ms. 1741", "quellensigle": "RoC 1741"}]
      },
      {
        "name": "Biblioteca Vallicelliana",
        "signaturen": [{"name": "C 52", "quellensigle": "RoV 52"}]
      },
      {
        "name": "Archivio Generale dell\u2019Ordine dei Predicatori",
        "signaturen": [{"name": "XIV.L 1", "quellensigle": "RoAG 14"}]
      }
    ]
  },
  {
    "name": "Rouen",
    "bibliotheken": [{
      "name": "Bibliothèque municipale",
      "signaturen": [
        {"name": "Ms. 250 (A. 233)", "quellensigle": "Rou 250"},
        {"name": "Ms. 222", "quellensigle": "Rou 222"},
        {"name": "Ms. 382", "quellensigle": "Rou 382"},
        {"name": "Ms. 384", "quellensigle": "Rou 384"}
      ]
    }]
  },
  {
    "name": "Saint-Quentin",
    "bibliotheken": [{
      "name": "Bibliothèque municipale",
      "signaturen": [{"name": "86", "quellensigle": "SQuen 86"}]
    }]
  },
  {
    "name": "Salzburg",
    "bibliotheken": [{
      "name": "Bibliothek des Erzabtei Sankt Peter",
      "signaturen": [{"name": "a VII 20", "quellensigle": "Sal 20"}]
    }]
  },
  {
    "name": "Santiago de Compostela",
    "bibliotheken": [{
      "name": "Kathedralarchiv",
      "signaturen": [{"name": "Codex Calixtinus", "quellensigle": "Calixt"}]
    }]
  },
  {
    "name": "Sens",
    "bibliotheken": [{
      "name": "Bibliothèque municipale",
      "signaturen": [{"name": "Ms. 46A", "quellensigle": "Sens 46A"}]
    }]
  },
  {
    "name": "Sevilla",
    "bibliotheken": [{
      "name": "Biblioteca Rodrigo De Zayas",
      "signaturen": [{"name": "Ms. 2 (Fragm.)", "quellensigle": "SeZ 2"}]
    }]
  },
  {
    "name": "Sion (Sitten)",
    "bibliotheken": [{
      "name": "Archives du Chapitre",
      "signaturen": [{"name": "Ms. 29", "quellensigle": "Si 29"}]
    }]
  },
  {
    "name": "Solesmes",
    "bibliotheken": [{
      "name": "Abbaye Saint-Pierre, Atelier de Pal. mus.",
      "signaturen": [{"name": "No. 596 ", "quellensigle": "So 596"}]
    }]
  },
  {
    "name": "Torino",
    "bibliotheken": [{
      "name": "Biblioteca Nazionale",
      "signaturen": [
        {"name": "Ms. J II 9", "quellensigle": "To 9"},
        {"name": "Ms. F IV 18", "quellensigle": "To 18"}
      ]
    }]
  },
  {
    "name": "Tortosa",
    "bibliotheken": [{
      "name": "Archivo de la Catedral",
      "signaturen": [{"name": "135", "quellensigle": "Tsa 135"}]
    }]
  },
  {
    "name": "Tours",
    "bibliotheken": [{
      "name": "Bibliothèque municipale",
      "signaturen": [{"name": "927", "quellensigle": "Tou 927"}]
    }]
  },
  {
    "name": "Trier",
    "bibliotheken": [{
      "name": "Stadtbibliothek",
      "signaturen": [{"name": "2254 (2197)", "quellensigle": "Tri 2254"}]
    }]
  },
  {
    "name": "Utrecht",
    "bibliotheken": [{
      "name": "Universiteitsbibliotheek",
      "signaturen": [{"name": "417", "quellensigle": "Ut 417"}]
    }]
  },
  {
    "name": "Venedig",
    "bibliotheken": [{
      "name": "Biblioteca di Santa Maria della Consolazione (detta della Fava)",
      "signaturen": [{"name": "Lit. 4", "quellensigle": "Ven 4"}]
    }]
  },
  {
    "name": "Verbania/Intra",
    "bibliotheken": [{
      "name": "Biblioteca Capitolare di San Vittore",
      "signaturen": [{"name": "Ms. 5 (14)", "quellensigle": "Intra 5"}]
    }]
  },
  {
    "name": "Vercelli",
    "bibliotheken": [{
      "name": "Biblioteca Capitolare",
      "signaturen": [
        {"name": "Ms. LVIa (Fragm.)", "quellensigle": "Vce 56a"},
        {"name": "Ms. LVIb", "quellensigle": "Vce 56b"}
      ]
    }]
  },
  {
    "name": "Verdun",
    "bibliotheken": [{
      "name": "Bibliothèque municipale",
      "signaturen": [{"name": "Ms. 10", "quellensigle": "Ver 10"}]
    }]
  },
  {
    "name": "Vic",
    "bibliotheken": [{
      "name": "Biblioteca Episcopal",
      "signaturen": [
        {"name": "Ms. 105", "quellensigle": "Vic 105"},
        {"name": "Ms. 106", "quellensigle": "Vic 106"}
      ]
    }]
  },
  {
    "name": "Würzburg",
    "bibliotheken": [{
      "name": "Universitätsbibliothek",
      "signaturen": [{"name": "M. p. th. f. 165", "quellensigle": "Wü 165"}]
    }]
  },
  {
    "name": "Wien",
    "bibliotheken": [{
      "name": "Archiv des Schottenstiftes",
      "signaturen": [{"name": "Fragm. liturg. 4 und Fragm. liturg. 5", "quellensigle": "WiSch 4/5"}]
    }]
  },
  {
    "name": "Wolfenbüttel",
    "bibliotheken": [{
      "name": "Herzog August Bibliothek",
      "signaturen": [{"name": "Cod. Guelf. 491 Helmst.", "quellensigle": "Wo 491"}]
    }]
  }
]

export const sigla: string[] = [
  "Abb 7",
  "Aa 13",
  "Ami 115",
  "Ao 16",
  "Apt 17",
  "Ass 695",
  "AugW 13",
  "Aut 183",
  "Aux 54",
  "Ba 12",
  "Ba 45",
  "Ben 34",
  "Ben 35",
  "Be 664",
  "Be 792",
  "Be 40078",
  "Be 40608",
  "Bo 7",
  "Cai 72",
  "Cai 172",
  "Cdg 710",
  "Cdg 17",
  "Civ 56",
  "Civ 101",
  "Civ 102",
  "GeB 74",
  "Dou 246",
  "Ei 366",
  "Ei 610",
  "Eng 314",
  "Ful 62",
  "Gir 4",
  "Go I ",
  "Gr 807",
  "Gren 4413",
  "Halb 45",
  "SCan 7",
  "Ka 5",
  "Kar 16",
  "Kop 13",
  "Lü 11",
  "La 263",
  "Le Puy 009",
  "Lim 2",
  "Lo 2615",
  "Lo 12194",
  "Lo 4",
  "Lo 14",
  "Lo 31384",
  "Luc 603",
  "Mü 5539",
  "Mü 9508",
  "Mü 293072",
  "Mü 156",
  "Ma 288",
  "Ma 289",
  "Ma 19421",
  "Ma 20-4",
  "Mel 109",
  "Mil 25",
  "Mod 16",
  "Mon 304",
  "Mza 77",
  "Mza 11",
  "Nam 1",
  "Nau I",
  "NY 905",
  "NY 3",
  "Non 1",
  "Nvr G3",
  "Nvr 116",
  "Or 129",
  "Or 201",
  "Ox 148",
  "Ox 358",
  "Ox 340",
  "Pad 47",
  "Pad 16",
  "Pad 55",
  "Pad 1340",
  "PaA 279",
  "PaM 1708",
  "Pa 1107",
  "Pa 1112",
  "Pa 1274",
  "Pa 14452",
  "Pa 14819",
  "Pa 16309",
  "Pa 1064",
  "Pa 1235",
  "PaSG 117",
  "Pa 904",
  "Pa 905",
  "Pa 1213",
  "PaA 135",
  "Pa 776",
  "Pa 778",
  "Pa 887",
  "Pa 903",
  "Pa 909",
  "Pa 1139",
  "Pa 3719b",
  "Pa 3719c",
  "Pa 1871",
  "Pa 5302",
  "Pa 833",
  "Pa 16819",
  "Pa 17328",
  "Parm 12",
  "Pia 65",
  "Pis 219",
  "Pst 120a",
  "Pst 121a",
  "Pre 2",
  "Pro 12",
  "Vat 3797",
  "RoC 1741",
  "RoV 52",
  "RoAG 14",
  "Rou 250",
  "Rou 222",
  "Rou 382",
  "Rou 384",
  "SQuen 86",
  "Sal 20",
  "Sol 596",
  "Calixt",
  "Sens 46A",
  "SeZ 2",
  "Si 29",
  "To 9",
  "To 18",
  "Tsa 135",
  "Tou 927",
  "Tri 2254",
  "Ut 417",
  "Ven 4",
  "Intra 5",
  "Vce 56a",
  "Vce 56b",
  "Ver 10",
  "Vic 105",
  "Vic 106",
  "Wü 165",
  "WiSch 4/5",
  "Wo 491"
];

export const sourceType = [
  "Antiphonar",
  "Cantatorium",
  "Graduale",
  "Hymnar",
  "Kyriale",
  "Missale",
  "Prozessionar",
  "Sequentiar",
  "Tonar",
  "Tropar",
  "Tropar-Sequentiar"
];

export const dates = [
  "1000",
  "1050",
  "1100",
  "1150",
  "1200",
  "1250",
  "1300",
  "1350",
  "1400",
  "1450",
  "1500"
]

export const genres: Gattung[] = [
  {
    "gattung1": "Lied",
    "kürzel": "L",
    "untergattungen": [
      {
        "gattung2": "Benedicamus",
        "kürzel": "Bd"
      },
      {
        "gattung2": "Conductus",
        "kürzel": "Cd"
      }
    ]
  },
  {
    "gattung1": "Ordinariumsgesang",
    "kürzel": "Or",
    "untergattungen": [
      {
        "gattung2": "Kyrie",
        "kürzel": "Ky"
      },
      {
        "gattung2": "Gloria",
        "kürzel": "Gl"
      },
      {
        "gattung2": "Credo",
        "kürzel": "Cr"
      },
      {
        "gattung2": "Sanctus",
        "kürzel": "Sa"
      },
      {
        "gattung2": "Agnus dei",
        "kürzel": "Ag"
      }
    ]
  },
  {
    "gattung1": "Sequenz",
    "kürzel": "Sq",
    "untergattungen": []
  },
  {
    "gattung1": "Spiel",
    "kürzel": "Sp",
    "untergattungen": [
      {
        "gattung2": "Antiphon",
        "kürzel": "A"
      },
      {
        "gattung2": "Responsorium",
        "kürzel": "R"
      },
      {
        "gattung2": "Sequenz",
        "kürzel": "Sq"
      },
      {
        "gattung2": "Hymnus",
        "kürzel": "Hy"
      },
      {
        "gattung2": "Evangelium",
        "kürzel": "Ev"
      },
      {
        "gattung2": "Vers",
        "kürzel": "V"
      }
    ]
  },
  {
    "gattung1": "Tropus",
    "kürzel": "Tr",
    "untergattungen": [
      {
        "gattung2": "Introitus",
        "kürzel": "In"
      },
      {
        "gattung2": "Offertorium",
        "kürzel": "Of"
      },
      {
        "gattung2": "Communio",
        "kürzel": "Cm"
      }
    ]
  },
  {
    "gattung1": "Prosula",
    "kürzel": "Pr",
    "untergattungen": [
      {
        "gattung2": "Offertorium",
        "kürzel": "Of"
      },
      {
        "gattung2": "Alleluia",
        "kürzel": "Al"
      },
      {
        "gattung2": "Tractus",
        "kürzel": "Tc"
      },
      {
        "gattung2": "Graduale",
        "kürzel": "Gr"
      }
    ]
  },
  {
    "gattung1": "Epistel",
    "kürzel": "Ep",
    "untergattungen": []
  },
  {
    "gattung1": "Evangelium",
    "kürzel": "Ev",
    "untergattungen": []
  },
  {
    "gattung1": "Introitus",
    "kürzel": "In",
    "untergattungen": []
  },
  {
    "gattung1": "Offertorium",
    "kürzel": "Of",
    "untergattungen": []
  },
  {
    "gattung1": "Communio",
    "kürzel": "Cm",
    "untergattungen": []
  }
];

export const feasts: Fest[] = [
  {
    "name": "Dominica I adventus",
    "datum": ""
  },
  {
    "name": "Eligius",
    "datum": "01.12."
  },
  {
    "name": "Nativitas domini II",
    "datum": "25.12."
  },
  {
    "name": "Nativitas domini III",
    "datum": "25.12."
  },
  {
    "name": "Stephanus",
    "datum": "26.12."
  },
  {
    "name": "Iohannes Evangelista",
    "datum": "27.12."
  },
  {
    "name": "Innocentes",
    "datum": "28.12."
  },
  {
    "name": "Silvester",
    "datum": "31.12."
  },
  {
    "name": "Circumcisio domini",
    "datum": "01.01."
  },
  {
    "name": "Octava domini",
    "datum": "01.01."
  },
  {
    "name": "Epiphania",
    "datum": "06.01."
  },
  {
    "name": "Vincentius",
    "datum": "22.01."
  },
  {
    "name": "Purificatio Mariae",
    "datum": "02.02."
  },
  {
    "name": "Pascha",
    "datum": ""
  },
  {
    "name": "Feria II post Pascha",
    "datum": ""
  },
  {
    "name": "Feria III post Pascha",
    "datum": ""
  },
  {
    "name": "Feria IV post Pascha",
    "datum": ""
  },
  {
    "name": "Octava Paschae",
    "datum": ""
  },
  {
    "name": "Ascensio domini",
    "datum": ""
  },
  {
    "name": "Pentecoste",
    "datum": ""
  },
  {
    "name": "Trinitatis",
    "datum": ""
  },
  {
    "name": "Castulus",
    "datum": "26.03."
  },
  {
    "name": "Inventio crucis",
    "datum": "03.05."
  },
  {
    "name": "Medardus",
    "datum": "08.06."
  },
  {
    "name": "Translatio Castuli",
    "datum": "14.06."
  },
  {
    "name": "Cyricus",
    "datum": "16.06."
  },
  {
    "name": "Iohannes Baptista",
    "datum": "24.06."
  },
  {
    "name": "Eligius",
    "datum": "25.06."
  },
  {
    "name": "Petrus et Paulus",
    "datum": "29.06."
  },
  {
    "name": "Kilian",
    "datum": "08.07."
  },
  {
    "name": "Vincula Petri",
    "datum": "01.08."
  },
  {
    "name": "Sixtus",
    "datum": "06.08."
  },
  {
    "name": "Laurentius",
    "datum": "10.08."
  },
  {
    "name": "Hippolytus",
    "datum": "13.08."
  },
  {
    "name": "Assumptio Mariae",
    "datum": "15.08."
  },
  {
    "name": "Adventus reliquiarum",
    "datum": "16.08."
  },
  {
    "name": "Decollatio Iohannis Baptistae",
    "datum": "29.08."
  },
  {
    "name": "Nativitas Mariae",
    "datum": "08.09."
  },
  {
    "name": "Mauritius",
    "datum": "22.09."
  },
  {
    "name": "Michael",
    "datum": "29.09."
  },
  {
    "name": "Piatus",
    "datum": "01.10."
  },
  {
    "name": "Dionysius,Rusticus et Eleutherius",
    "datum": "09.10."
  },
  {
    "name": "Gereon et socii",
    "datum": "10.10."
  },
  {
    "name": "Omnes sancti",
    "datum": "01.11."
  },
  {
    "name": "Martinus",
    "datum": "11.11."
  },
  {
    "name": "Andreas",
    "datum": "30.11."
  },
  {
    "name": "Dedicatio ecclesiae",
    "datum": ""
  }
]
