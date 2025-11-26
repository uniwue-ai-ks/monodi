import { ViewChild, ElementRef, Component, OnInit, HostListener } from '@angular/core';
import { Router, ActivatedRoute } from '@angular/router';
import { UserService, User } from '../user.service';
import { APIService, Document } from '../api.service';
import { assertNever } from '../../utils';
import { ToolsService } from '../tools.service';
import { ToastrService } from 'ngx-toastr';
import { Subscription, combineLatest } from 'rxjs';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { parsers } from '../types/parser';
import * as VM from '../types/model';
import * as D from '../dropdown';
import * as S from '../sselect/sselect.component';
import { UndoService } from '../undoService';

@Component({
  selector: 'app-document',
  templateUrl: './document.component.html',
  styleUrls: ['./document.component.css']
})
export class DocumentComponent implements OnInit {
  @ViewChild('textImport', { static: true }) textImportModal!: ElementRef;
  @ViewChild('globalComment', { static: true }) globalCommentModal!: ElementRef;
  subs: Subscription[] = [];
  document: Document | undefined = undefined;
  user: User | null = null;
  cont: VM.RootContainer | undefined;
  readOnly: boolean = false;
  collapseMetadata: boolean = true;
  sourceSigle: string | undefined;
  documentJsonClone: string | undefined = undefined;
  contJsonClone: string | undefined = undefined;
  textImportErrors: Array<string> = [];

  gattung1: S.DropdownStack<D.Gattung> = S.makeDropdownHead(D.genres, r => r.gattung1, () => { if (this.document) { return this.document.gattung1 } });
  gattung2: S.DropdownStack<D.Untergattung> = S.makeDropdown(this.gattung1, p => p.untergattungen, d => d.gattung2, () => { if (this.document) { return this.document.gattung2 } }, s => { if (this.document) { this.document.gattung2 = s; } });
  fest: S.DropdownStack<D.Fest> = S.makeDropdownHead(D.feasts, r => r.name, () => { if (this.document) { return this.document.festtag } });

  importText: string = '';
  importType: keyof typeof parsers = "Misc";
  importTypes = Object.keys(parsers);

  constructor(
    private api: APIService,
    private router: Router,
    private userService: UserService,
    private undoService: UndoService,
    private route: ActivatedRoute,
    private toastr: ToastrService,
    private modalService: NgbModal,
    private toolService: ToolsService) {
  }

  test() {
    this.undoService.undo();
  }

  toggleReadOnly() {
    this.readOnly = !this.readOnly;
  }

  recalcId(): void {
    if (this.sourceSigle && this.document) {
      this.document.dokumenten_id = [this.sourceSigle, this.document.foliostart, this.document.zeilenstart].join('-');
    }
  }

  getJsonString = (): string | undefined => {
    if (this.cont) {
      return JSON.stringify(this.cont);
    }
    return undefined;
  }

  undoChanges = (jsonString: string): void => {
    const newCont: VM.RootContainer = JSON.parse(jsonString);
    if (newCont) {
      this.cont = newCont;
    }
  }

  ngOnInit() {
    this.undoService.registerUnDo(this.getJsonString, this.undoChanges);
    this.subs.push(combineLatest(this.userService.user, this.route.paramMap, (user, params) => ({ user, params })).subscribe(pair => {
      this.user = pair.user;
      const source = (pair.params.get('source') as string);
      const id = pair.params.get('id');
      this.setSourceSigle(source);
      if (id !== null) {
        this.retrieveForId(id);
      } else {
        this.collapseMetadata = false;
        this.document = {
          id: '',
          quelle_id: source,
          dokumenten_id: '',
          gattung1: '',
          gattung2: '',
          festtag: '',
          feier: '',
          textinitium: '',
          bibliographischerverweis: '',
          druckausgabe: '',
          zeilenstart: '',
          foliostart: '',
          kommentar: '',
          editionsstatus: '',
        };
        this.cont = VM.emptyRootContainer();
      }

      setTimeout(() => {
        this.toolService.addStack({
          source: this,
          tools: [
            {
              callback: () => { this.goToSource(source); },
              icon: 'to-source',
              title: 'Zurück zur Quelle'
            },
            {
              callback: () => { if (this.document && this.document.id) { this.update(); } else { this.create(); } },
              icon: 'save',
              title: 'Speichern'
            },
            {
              callback: () => { this.upload(); },
              icon: 'upload',
              title: 'Dokument hochladen'
            },
            {
              callback: () => { this.download(); },
              icon: 'download',
              title: 'Dokument exportieren'
            },
            {
              callback: () => { this.toggleReadOnly(); },
              icon: 'eye',
              title: 'View-Only Modus umschalten'
            },
            {
              callback: () => { this.modalService.open(this.textImportModal); },
              icon: 'short-text',
              title: 'Text Import'
            },
            {
              callback: () => { this.modalService.open(this.globalCommentModal, { size: 'xl', fullscreen: true }); },
              icon: 'comment',
              title: 'Globalen Kommentar festlegen'
            }
          ]
        });
      }, 0);
    }));
  }

  goToSource(s_id: string) {
    this.router.navigate(['/source', s_id]);
  }

  retrieveForId(id: string): void {
    if (this.user) {
      this.api.getDocument(this.user.token, id).subscribe(res => {
        switch (res.kind) {
          case 'LoginRequired': this.userService.logout(); break;
          case 'InsufficientPermissions': this.userService.logout(); break;
          case 'DocumentNotFound': this.document = undefined; break;
          case 'DocumentRetrieved': this.document = res.document; this.documentJsonClone = JSON.stringify(this.document); break;
          default: assertNever(res);
        }
      });
      this.api.getDocumentNotes(this.user.token, id).subscribe(res => {
        switch (res.kind) {
          case 'LoginRequired': this.userService.logout(); break;
          case 'InsufficientPermissions': this.userService.logout(); break;
          case 'DocumentNotFound': this.document = undefined; break;
          case 'NotesRetrieved': this.cont = res.data; this.contJsonClone = JSON.stringify(this.cont); break;
          default: assertNever(res);
        }
      });
    }
  }

  setSourceSigle(sourceId: string): void {
    if (this.user) {
      this.api.getSigle(this.user.token, sourceId).subscribe(res => {
        switch (res.kind) {
          case 'LoginRequired': this.userService.logout(); break;
          case 'SourceNotFound': this.sourceSigle = ''; break;
          case 'SigleRetrieved': this.sourceSigle = res.sigle; break;
          default: assertNever(res);
        }
        this.recalcId();
      });
    }
  }

  create(): void {
    const doc = this.document;
    const cont = this.cont;
    if (this.user && doc && cont) {
      this.api.createDocument(this.user.token, { document: doc, notes: cont }).subscribe(res => {
        switch (res.kind) {
          case 'LoginRequired': this.userService.logout(); break;
          case 'DocumentCreated': this.toastr.success("Dokument wurde erstellt"); this.router.navigate(['/document', doc.quelle_id, res.id]); break;
          default: assertNever(res);
        }
      });
    }
  }

  update(): void {
    if (this.user && this.document && this.document.id && this.cont) {
      this.api.updateDocument(this.user.token, { document: this.document, notes: this.cont }).subscribe(res => {
        switch (res.kind) {
          case 'LoginRequired': this.userService.logout(); break;
          case 'Ok': this.toastr.success("Erfolgreich gespeichert."); this.resetClones(); break;
          case 'DocumentNotFound': this.toastr.error("Es sieht so aus, als wäre das Dokument zwischenzeitlich gelöscht worden"); break;
          case 'InsufficientPermissions': this.toastr.error("Sie haben nicht genügend Rechte, um dieses Dokument zu speichern. Sie können das Dokument jedoch als JSON downloaden, um die Rechte bitten und es dann wieder hochladen um Datenverlust zu vermeiden.", "Fehler beim Speichern."); break;
          default: assertNever(res);
        }
      });
    }
  }

  download(): void {
    const pom = document.createElement('a');
    pom.setAttribute('href', 'data:application/json;charset=utf-8,' + encodeURIComponent(JSON.stringify(this.cont)));
    if (this.document && this.document.dokumenten_id) {
      pom.setAttribute('download', this.document.dokumenten_id + ".json");
    } else {
      pom.setAttribute('download', "document.json");
    }

    if (document.createEvent) {
      var event = document.createEvent('MouseEvents');
      event.initEvent('click', true, true);
      pom.dispatchEvent(event);
    }
    else {
      pom.click();
    }
  }

  upload(): void {
    document.getElementById("document-upload")!.click();
  }

  handleFile(): void {
    const that = this;
    const file = (document.getElementById("document-upload") as HTMLInputElement)!.files![0];
    const reader = new FileReader();
    reader.onload = (p: ProgressEvent) => {
      const newCont = (p.target as any).result;
      if (this.user) {
        this.api.verifyNotes(this.user.token, newCont).subscribe(res => {
          switch (res.kind) {
            case 'LoginRequired': this.userService.logout(); break;
            case 'Failed': this.toastr.error("Invalides Format", "Upload gescheitert!"); break;
            case 'NotesRetrieved':
              this.cont = res.data;
              this.contJsonClone = JSON.stringify(this.cont);
              this.toastr.success("Upload erfolgreich!");
              break;

            default: assertNever(res);
          }
        });
        (document.getElementById("document-upload") as HTMLInputElement)!.value = "";
      }
    }
    reader.readAsText(file);
  }

  @HostListener('window:unload', ['$event'])
  unloadHandler($event: any) {
    this.hasChanges();
  }

  @HostListener('window:beforeunload', ['$event'])
  beforeUnloadHander($event: any) {
    return !this.hasChanges();
  }

  @HostListener('window:keydown', ['$event'])
  keyEvent(event: KeyboardEvent) {
    if (event.ctrlKey && event.key === 'z') {
      this.undoService.undo();
    }
  }

  hasChanges(): boolean {
    if (this.contJsonClone)
      return !(this.contJsonClone.replace(/"focus":true/, '"focus":false') === JSON.stringify(this.cont).replace(/"focus":true/, '"focus":false')
        && this.documentJsonClone === JSON.stringify(this.document));
    return false;
  }

  resetClones(): void {
    this.documentJsonClone = JSON.stringify(this.document);
    this.contJsonClone = JSON.stringify(this.cont);
  }

  ngOnDestroy(): void {
    for (const s of this.subs) {
      s.unsubscribe();
    }
    this.toolService.remove(this);
  }

  doImport(): void {
    this.textImportErrors = [];
    if (this.validateTextImputAgainstCommonErrors(this.importText.trim())) {
      const result = parsers[this.importType]!.parse(this.importText.trim());
      if (result.status) {
        this.cont = result.value;
        this.modalService.dismissAll();
      } else {
        console.log(result);
        this.toastr.error("Technische details können in der Konsole gesehen werden", "Text konnte nicht geparst werden");
        this.modalService.dismissAll();
      }
    }
  }

  validateTextImputAgainstCommonErrors(importedText: string): boolean {
    // rule more then 2 tabstops regex(/\t\t+/)
    const rule1 = /\t\t\t+/;
    // rule more then 2 spaces regex(/\ \ +/)
    const rule2 = /\ \ +/;
    // rule no whitespace in first column
    const rule3 = /^\ +\t/;
    // rule if || then two tabs else only one allowed
    const rule4 = /\t.*\t(?!\|{2})/;

    const rules: RegExp[] = [rule1, rule2, rule3, rule4];
    const inputLines = importedText.split('\n');
    let result = true;
    const errors: Array<Array<string>> = new Array(4).fill(1).map(() => new Array());
    for (let i = 0; i < inputLines.length; i++) {
      rules.map((rule, index) => {
        if (inputLines[i].match(rule) != null) {
          errors[index].push('' + (i + 1));
          result = false;
        }
      }
      );
    }
    if (!result) {
      if (errors.length > 0) {
        if (errors[0].length > 0) {
          this.textImportErrors.push('Es wurden mehr als Zwei Tabstops in folgenden Zeilen erkannt: ' + errors[0]);
        }
        if (errors[1].length > 0) {
          this.textImportErrors.push('Es wurden Zwei oder mehr Leerzeichen hintereinander in folgenden Zeilen erkannt: ' + errors[1]);
        }
        if (errors[2].length > 0) {
          this.textImportErrors.push('Es wurden Leerzeichen in der ersten Spalte in folgenden Zeilen erkannt: ' + errors[2]);
        }
        if (errors[3].length > 0) {
          this.textImportErrors.push('Es wurden Zwei Tabs in Zeilen ohne Seitenumbruch in folgenden Zeilen erkannt: ' + errors[3]);
        }
      }
      this.toastr.error('Es wurden Fehler in der Eingabe erkannt.');
    }
    return result;
  }

  copyIdToClipboard(): void {
    const e = document.getElementById("document-id-input") as HTMLInputElement | null;
    if (e) {
      e.select();
      e.setSelectionRange(0, 99999)
      document.execCommand("copy");
      window.alert("copied");
    }
  }

  createGlobalComment(): void {
    if (this.cont) {
      this.cont.globalComment = VM.emptyCommentTree();
    }
  }

  handleGlobalCommentTreeEvent(e: VM.CommentTreeEvent): void {
    if (this.cont?.globalComment) {
      this.cont.globalComment = VM.applyCommentTreeEvent(this.cont.globalComment, e);
    }
  }

  createNewZeileContainerForTreeComment(): VM.ZeileContainer {
    return VM.emptyZeileContainer();
  }

  deleteGlobalComment(): void {
    if (this.cont) {
      this.cont.globalComment = undefined;
    }
  }

}
