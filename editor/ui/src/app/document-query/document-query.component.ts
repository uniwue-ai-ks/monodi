import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { UserService, User } from '../user.service';
import { APIService, Document, DocumentQuery } from '../api.service'
import { assertNever } from '../../utils';
import { combineLatest, Subscription } from 'rxjs';
import { Location } from '@angular/common';
import * as D from '../dropdown';
import * as S from '../sselect/sselect.component';


@Component({
  selector: 'app-document',
  templateUrl: './document-query.component.html',
  styleUrls: ['./document-query.component.css']
})
export class DocumentQueryComponent implements OnInit {
  getTemplate() { return (this as any).tb; }

  subs: Subscription[] = [];
  user: User | null = null;
  query: DocumentQuery = {
    "dokumenten_id": undefined,
    "gattung1": undefined,
    "gattung2": undefined,
    "festtag": undefined,
    "feier": undefined,
    "textinitium": undefined,
    "bibliographischerverweis": undefined,
    "druckausgabe": undefined,
    "zeilenstart": undefined,
    "foliostart": undefined,
    "kommentar": undefined,
  };
  results: Document[] = [];

  gattung1: S.DropdownStack<D.Gattung> = S.makeDropdownHead(D.genres, r => r.gattung1, () => { if (this.query) { return this.query.gattung1 } });
  gattung2: S.DropdownStack<D.Untergattung> = S.makeDropdown(this.gattung1, p => p.untergattungen, d => d.gattung2, () => { if (this.query) { return this.query.gattung2 } }, s => { if (this.query) { this.query.gattung2 = s; } });
  fest: S.DropdownStack<D.Fest> = S.makeDropdownHead(D.feasts, r => r.name, () => { if (this.query) { return this.query.festtag } });

  items: Item[] = [];
  allItems: Item[] = [
    Item.D_ID,
    Item.D_GATTUNG1,
    Item.D_GATTUNG2,
    Item.D_FESTTAG,
    Item.D_FEIER,
    Item.D_INITIUM,
    Item.D_BIBLIOREF,
    Item.D_DRUCKAUSGABE,
    Item.D_ZEILENSTART,
    Item.D_FOLIOSTART,
    Item.D_KOMMENTAR,
  ];
  Item = Item
  newItem: string = "";

  addItem(item: Item) {
    this.items.push(item)
    setTimeout(() => this.newItem = "", 0);
  }

  constructor(private api: APIService,
    private router: Router,
    private userService: UserService,
    private route: ActivatedRoute,
    private location: Location) {
  }

  itemsLeft(): Item[] {
    const newArray = this.allItems.filter(i => this.items.indexOf(i) == -1);
    if (this.items.indexOf(Item.D_GATTUNG1) === -1) {
      return newArray.filter(i => i !== Item.D_GATTUNG2);
    } else {
      return newArray;
    }
  }

  remove(item: Item): void {
    const newItems = this.items.filter(i => i !== item);
    if (item === Item.D_GATTUNG1) {
      this.items = newItems.filter(i => i !== Item.D_GATTUNG2);
    } else {
      this.items = newItems;
    }
  }

  ngOnInit() {
    this.subs.push(combineLatest(this.userService.user, this.route.paramMap, (user, params) => ({ user, params })).subscribe(pair => {
      this.user = pair.user;
      let queryString = pair.params.get("query");
      if (queryString !== null) {
        console.log(queryString);
        this.query = JSON.parse(decodeURIComponent(queryString));
        this.buildItemsFromQuery();
        this.search();
      }
    }));
  }

  buildItemsFromQuery(): void {
    for (let key of Object.keys(this.query)) {
      switch (key) {
        case "dokumenten_id": this.addItem(Item.D_ID); break;
        case "gattung1": this.addItem(Item.D_GATTUNG1); break;
        case "gattung2": this.addItem(Item.D_GATTUNG2); break;
        case "festtag": this.addItem(Item.D_FESTTAG); break;
        case "feier": this.addItem(Item.D_FEIER); break;
        case "textinitium": this.addItem(Item.D_INITIUM); break;
        case "bibliographischerverweis": this.addItem(Item.D_BIBLIOREF); break;
        case "druckausgabe": this.addItem(Item.D_DRUCKAUSGABE); break;
        case "zeilenstart": this.addItem(Item.D_ZEILENSTART); break;
        case "foliostart": this.addItem(Item.D_FOLIOSTART); break;
        case "kommentar": this.addItem(Item.D_KOMMENTAR); break;
      }
    }
  }

  search(): void {
    if (this.user) {
      this.api.queryDocuments(this.user.token, this.query).subscribe(res => {
        this.location.replaceState("/search/" + encodeURIComponent(JSON.stringify(this.query)));
        switch (res.kind) {
          case 'LoginRequired': this.userService.logout(); break;
          case 'DocumentsRetrieved': {
            this.results = res.documents;
            break;
          }
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

enum Item {
  D_ID = "Dokumenten ID",
  D_GATTUNG1 = "Gattung 1",
  D_GATTUNG2 = "Gattung 2",
  D_FESTTAG = "Festtag",
  D_FEIER = "Feier",
  D_INITIUM = "Textinitium",
  D_BIBLIOREF = "Bibliographischerverweis",
  D_DRUCKAUSGABE = "Druckausgabe",
  D_ZEILENSTART = "Zeilenstart",
  D_FOLIOSTART = "Foliostart",
  D_KOMMENTAR = "Kommentar",
};
