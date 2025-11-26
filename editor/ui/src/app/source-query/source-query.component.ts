import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { UserService, User } from '../user.service';
import { APIService, Source, SourceQuery } from '../api.service'
import { assertNever } from '../../utils';
import { combineLatest, Subscription } from 'rxjs';
import { Header } from '../smart-table/smart-table.component';
import { Location } from '@angular/common';
import * as D from '../dropdown';
import * as S from '../sselect/sselect.component';


@Component({
  selector: 'app-source-query',
  templateUrl: './source-query.component.html',
  styleUrls: ['./source-query.component.css']
})
export class SourceQueryComponent implements OnInit {
  subs: Subscription[] = [];
  user: User | null = null;
  query: SourceQuery = {
    quellensigle: undefined,
    herkunftsregion: undefined,
    herkunftsort: undefined,
    herkunftsinstitution: undefined,
    ordenstradition: undefined,
    quellentyp: undefined,
    bibliotheksort: undefined,
    bibliothek: undefined,
    bibliothekssignatur: undefined,
    kommentar: undefined,
    datierung: undefined
  };
  results: Source[] = [];

  regionDropdown: S.DropdownStack<D.Herkunftsregion> = S.makeDropdownHead(D.regions, r => r.name, () => { if (this.query) { return this.query.herkunftsregion } });
  ortDropdown: S.DropdownStack<D.Herkunftsort> = S.makeDropdown(this.regionDropdown, p => p.orte, d => d.name, () => { if (this.query) { return this.query.herkunftsort } }, s => { if (this.query) { this.query.herkunftsort = s; } });
  institutDropdown: S.DropdownStack<string> = S.makeDropdown(this.ortDropdown, p => p.institute, d => d, () => { if (this.query) { return this.query.herkunftsinstitution } }, s => { if (this.query) { this.query.herkunftsinstitution = s; } });
  bibliotheksortDropdown: S.DropdownStack<D.Bibliotheksort> = S.makeDropdownHead(D.libraries, r => r.name, () => { if (this.query) { return this.query.bibliotheksort } });
  bibliothekDropdown: S.DropdownStack<D.Bibliothek> = S.makeDropdown(this.bibliotheksortDropdown, p => p.bibliotheken, d => d.name, () => { if (this.query) { return this.query.bibliothek } }, s => { if (this.query) { this.query.bibliothek = s; } });
  bibliothekssignaturDropdown: S.DropdownStack<D.Signatur> = S.makeDropdown(this.bibliothekDropdown, p => p.signaturen, d => d.name, () => { if (this.query) { return this.query.bibliothekssignatur } }, s => { if (this.query) { this.query.bibliothekssignatur = s; } });
  siglaDropdown: S.DropdownStack<string> = S.makeDropdownHead(D.sigla, r => r, () => { if (this.query) { return this.query.quellensigle } });
  sourceTypeDropdown: S.DropdownStack<string> = S.makeDropdownHead(D.sourceType, r => r, () => { if (this.query) { return this.query.quellentyp } });
  datesDropdown: S.DropdownStack<string> = S.makeDropdownHead(D.dates, r => r, () => { if (this.query) { return this.query.datierung } });

  headers: Header<Source>[] = [
    {
      name: "Quellensigle",
      makeCell: (x: Source) => ({ kind: 'text', text: x.quellensigle })
    },
    {
      name: "Herkunftsort",
      makeCell: (x: Source) => ({ kind: 'text', text: x.herkunftsort })
    },
    {
      name: "Instution",
      makeCell: (x: Source) => ({ kind: 'text', text: x.herkunftsinstitution })
    },
    {
      name: "Datierung",
      makeCell: (x: Source) => ({ kind: 'text', text: "" + Math.random() })
    },
    {
      name: "Quellentyp",
      makeCell: (x: Source) => ({ kind: 'text', text: x.quellentyp })
    },
  ];

  constructor(private api: APIService,
    private router: Router,
    private userService: UserService,
    private route: ActivatedRoute,
    private location: Location) {
  }

  ngOnInit() {
    this.subs.push(combineLatest(this.userService.user, this.route.paramMap, (user, params) => ({ user, params })).subscribe(pair => {
      this.user = pair.user;
      let queryString = pair.params.get("query");
      if (queryString !== null) {
        this.query = JSON.parse(decodeURIComponent(queryString));
        //this.buildItemsFromQuery();
        this.search();
      }
    }));
  }

  goToSource(s: Source) {
    this.router.navigate(['/source', s.id]);
  }

  search(): void {
    if (this.user) {
      this.api.querySources(this.user.token, this.query).subscribe(res => {
        this.location.replaceState("/search-source/" + encodeURIComponent(JSON.stringify(this.query)));
        switch (res.kind) {
          case 'LoginRequired': this.userService.logout(); break;
          case 'SourcesRetrieved': this.results = res.sources; break;
          default: assertNever(res);
        }
      });
    } else {
      window.alert("You have to be logged in to search.");
    }
  }


  ngOnDestroy(): void {
    for (const s of this.subs) {
      s.unsubscribe();
    }
  }
}
