import { Component, EventEmitter, HostBinding, Input, Output } from '@angular/core';
import { ToastrService } from 'ngx-toastr';
import * as M from '../../../types/model';

@Component({
  selector: 'app-comment-tree-grid',
  templateUrl: './comment-tree-grid.component.html',
  styleUrls: ['./comment-tree-grid.component.scss']
})
export class CommentTreeGridComponent {
  @Input() originalCreator?: () => M.ZeileContainer;
  @Input({ required: true }) data!: M.CommentTreeGrid;
  @Input({ required: true }) path!: M.CommentTreePath;
  @Output() treeEvent = new EventEmitter<M.CommentTreeEvent>();

  constructor(
    private toaster: ToastrService
  ) { }


  @HostBinding('style.grid-template-columns') get gridTemplateColumns(): string {
    const dataCols = Array.from(Array(this.data.items[0].length).keys()).map(i => this.getColumnWidth(i));
    return [...dataCols, "auto"].join(" ");
  }

  @HostBinding('style.grid-template-rows') get gridTemplateRows(): string {
    return `repeat(${this.data.items.length}, auto) auto`;
  }

  private getColumnWidth(index: number): string {
    const allCells = this.data.items.map(row => row[index]);
    const allAreAuto = allCells.every(cell => cell.kind === "CommentTreeLeaf" && cell.content.kind === "Bracket");
    return allAreAuto ? "auto" : "1fr";
  }

  entries(): { path: M.CommentTreePath, sub: M.CommentTree }[] {
    return Array.from(Array(this.data.items.length).keys()).flatMap(y => {
      return Array.from(Array(this.data.items[y].length).keys()).map(x => {
        return {
          row: y,
          col: x,
          path: this.path.concat([[y, x]]),
          sub: this.data.items[y][x]
        };
      });
    });
  }

  identifyEntry(_: number, entry: { path: M.CommentTreePath, sub: M.CommentTree }): any {
    return entry.sub.id;
  }

  addRow(): void { this.treeEvent.emit({ source: this.path, intent: { kind: "AddRow" } }); }
  addCol(): void { this.treeEvent.emit({ source: this.path, intent: { kind: "AddColumn" } }); }
  delRow(i: number): void {
    if (this.data.items.length === 1) {
      this.toaster.error("Cannot delete the last row");
    } else {
      this.treeEvent.emit({ source: this.path, intent: { kind: "DeleteRow", index: i } });
    }
  }
  delCol(i: number): void {
    if (this.data.items[0].length === 1) {
      this.toaster.error("Cannot delete the last column");
    } else {
      this.treeEvent.emit({
        source: this.path, intent: { kind: "DeleteColumn", index: i }
      });
    }
  }
}
