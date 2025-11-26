import { DoCheck, Component, OnInit, OnDestroy } from '@angular/core';
import { Router, ActivatedRoute } from '@angular/router';
import { UserService, User } from '../user.service';
import { APIService, UserInfo, Source, Document } from '../api.service'
import { ToastrService } from 'ngx-toastr';
import { ToolsService, Tool} from '../tools.service';
import { assertNever } from '../../utils';
import { Subscription, combineLatest } from 'rxjs';
import * as D from '../dropdown';
import * as S from '../sselect/sselect.component';


@Component({
  selector: 'app-source',
  templateUrl: './source.component.html',
  styleUrls: ['./source.component.css']
})
export class SourceComponent implements OnInit {
  subs: Subscription[] = [];
  source: Source | undefined = undefined;
  documents: Document[] = [];
  user: User | null = null;
  regionDropdown:              S.DropdownStack<D.Herkunftsregion> = S.makeDropdownHead(D.regions, r => r.name, () => { if (this.source) { return this.source.herkunftsregion} });
  ortDropdown:                 S.DropdownStack<D.Herkunftsort>    = S.makeDropdown(this.regionDropdown, p => p.orte, d => d.name, () => { if (this.source) { return this.source.herkunftsort} }, s => { if (this.source) { this.source.herkunftsort = s; } });
  institutDropdown:            S.DropdownStack<string>            = S.makeDropdown(this.ortDropdown, p => p.institute, d => d, () => { if (this.source) { return this.source.herkunftsinstitution} }, s => { if (this.source) { this.source.herkunftsinstitution = s; } });
  bibliotheksortDropdown:      S.DropdownStack<D.Bibliotheksort>  = S.makeDropdownHead(D.libraries, r => r.name, () => { if (this.source) { return this.source.bibliotheksort} });
  bibliothekDropdown:          S.DropdownStack<D.Bibliothek>      = S.makeDropdown(this.bibliotheksortDropdown, p => p.bibliotheken, d => d.name, () => { if (this.source) { return this.source.bibliothek} }, s => { if (this.source) { this.source.bibliothek = s; } });
  bibliothekssignaturDropdown: S.DropdownStack<D.Signatur>        = S.makeDropdown(this.bibliothekDropdown, p => p.signaturen, d => d.name, () => { if (this.source) { return this.source.bibliothekssignatur} }, s => { if (this.source) { this.source.bibliothekssignatur = s; } });
  siglaDropdown:               S.DropdownStack<string>            = S.makeDropdownHead(D.sigla, r => r, () => { if (this.source) { return this.source.quellensigle} });
  sourceTypeDropdown:          S.DropdownStack<string>            = S.makeDropdownHead(D.sourceType, r => r, () => { if (this.source) { return this.source.quellentyp} });
  datesDropdown:               S.DropdownStack<string>            = S.makeDropdownHead(D.dates, r => r, () => { if (this.source) { return this.source.datierung} });

  constructor(
    private api: APIService,
    private router: Router,
    private userService: UserService,
    private route: ActivatedRoute,
    private toastr: ToastrService,
    private toolService: ToolsService) {
    this.siglaDropdown.addCallback(() => {
      const value = this.siglaDropdown.getValue();
      if (this.source && value) {
        [this.source.bibliotheksort, this.source.bibliothek, this.source.bibliothekssignatur] = findLibraries(value);
      }
    });
  }

  ngOnInit() {
    this.subs.push(combineLatest(this.userService.user, this.route.paramMap, (user, params) => ({user, params})).subscribe(pair => {
      this.user = pair.user;
      const id = pair.params.get("id");
      if (id !== null) {
        this.retrieveForId(id);
      } else {
        this.source = {
          id: undefined,
          quellensigle: "",
          herkunftsregion: "",
          herkunftsort: "",
          herkunftsinstitution: "",
          ordenstradition: "",
          quellentyp: "",
          bibliotheksort: "",
          bibliothek: "",
          bibliothekssignatur: "",
          kommentar: "",
          datierung: ""
        };
      }

    }));
  }

  updateQuellensigle(quellensigle:string){
    this.toolService.remove(this);
    this.toolService.addStack({
      source: this,
      tools: [
        {
          title: 'Quellensigle: ' + quellensigle
        },
      ]
    });
  }

  retrieveForId(id: string): void {
    if (this.user) {
      this.api.getSource(this.user.token, id).subscribe(res => {
        switch (res.kind) {
          case 'LoginRequired': this.userService.logout(); break;
          case 'SourceNotFound': this.source = undefined; break;
          case 'SourceRetrieved': 
            this.source = res.source;
            this.updateQuellensigle(this.source.quellensigle);
            break;
          case 'InsufficientPermissions': this.userService.logout(); break;
          default: assertNever(res);
        }
      });

      this.api.listDocuments(this.user.token).subscribe(res => {
        switch (res.kind) {
          case 'LoginRequired': this.userService.logout(); break;
          case 'DocumentsRetrieved': this.documents = res.documents.filter(d => d.quelle_id === id); break;
          default: assertNever(res);
        }
      });
    }
  }

  create(): void {
    if (this.user && this.source) {
      this.api.createSource(this.user.token, this.source).subscribe(res => {
        switch (res.kind) {
          case 'LoginRequired': this.userService.logout(); break;
          case 'SourceCreated': this.router.navigate(['/sources']); break;
          default: assertNever(res);
        }
      });
    }
  }

  update(): void {
    if (this.user && this.source && this.source.id) {
      this.api.updateSource(this.user.token, this.source).subscribe(res => {
        switch (res.kind) {
          case 'LoginRequired': this.userService.logout(); break;
          case 'Ok':
            this.toastr.success("Erfolgreich gespeichert.");
            if (this.source && this.source.quellensigle) {
              this.updateQuellensigle(this.source.quellensigle);
            }
            break;
          case 'SourceNotFound': this.toastr.error("Es sieht so aus, als wäre die Quelle zwischenzeitlich gelöscht worden"); break;
          default: assertNever(res);
        }
      });
    }
  }

  ngOnDestroy(): void {
    for (const s of this.subs) {
      s.unsubscribe();
    }
    this.toolService.remove(this);
  }

}

function findLibraries(sigle: string): [string, string, string] {
  for (let place of D.libraries) {
    for (let library of place.bibliotheken) {
      for (let signature of library.signaturen) {
        if (signature.quellensigle === sigle) {
          return [place.name, library.name, signature.name];
        }
      }
    }
  }

  return ["", "", ""];
}
