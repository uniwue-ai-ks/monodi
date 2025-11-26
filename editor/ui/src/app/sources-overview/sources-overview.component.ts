import { Component, OnInit, OnDestroy } from '@angular/core';
import { Router } from '@angular/router';
import { UserService, User } from '../user.service';
import { APIService, UserInfo, Source } from '../api.service'
import { assertNever } from '../../utils';
import { Subscription } from 'rxjs';
import { Header } from '../smart-table/smart-table.component';
import { ToastrService } from 'ngx-toastr';

@Component({
  selector: 'app-sources-overview',
  templateUrl: './sources-overview.component.html',
  styleUrls: ['./sources-overview.component.css']
})
export class SourcesOverviewComponent implements OnInit, OnDestroy {
  subs: Subscription[] = [];
  sources: Source[] = [];
  user: User | null = null;

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
      makeCell: (x: Source) => ({ kind: 'text', text: x.datierung })
    },
    {
      name: "Quellentyp",
      makeCell: (x: Source) => ({ kind: 'text', text: x.quellentyp })
    },
  ];

  constructor(
    private api: APIService,
    private router: Router,
    private userService: UserService,
    private toastr: ToastrService
  ) {
  }

  ngOnInit() {
    this.subs.push(this.userService.user.subscribe(u => {
      this.user = u;
      this.updateList();
    }));
  }

  updateList(): void {
    if (this.user) {
      this.api.listSources(this.user.token).subscribe(res => {
        switch (res.kind) {
          case 'LoginRequired': this.userService.logout(); break;
          case 'SourcesRetrieved': this.sources = res.sources; break;
          default: assertNever(res);
        }
      });
    }
  }

  ngOnDestroy(): void {
    for (const s of this.subs) {
      s.unsubscribe();
    }
  }

  goToSource(s: Source) {
    this.router.navigate(['/source', s.id]);
  }

  delete(): void {
    this.toastr.warning("Quellen können nicht mehr direkt gelöscht werden.");
  }

}
